package com.jheinz.tomyen.isle.shade;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.jheinz.tomyen.isle.IsleDimension;

/**
 * Every mob that dies anywhere but the Isle ends up there, one way or another (called after each death; a despawn is
 * no death, and players have their Revenants instead):
 *
 * <ul>
 * <li>a boss ({@code #tomyen:shade_bosses}) goes into the boss ring;
 * <li>a named mob, an elite ({@code #tomyen:notable_dead}) or a named pet gets a {@link DeathRecord} in the ring of the
 * region it fell in;
 * <li>a mob a player killed gets one too, but only one per type per region every {@value #SAMPLE_TICKS} ticks, and
 * then only by a {@value #SAMPLE_CHANCE_PERCENT}% chance, so a mob farm can't fill the buffer;
 * <li>everything else (and every sampled-out kill) adds one to its type's Soul Tide.
 * </ul>
 *
 * Machines and the Isle's own spirits ({@code #tomyen:soulless}) leave nothing. Each death costs a few map lookups and
 * at most one small allocation; {@link #averageNanos} keeps the measured cost for {@code /isle souls}.
 */
public final class SoulRecorder {
	static final int SAMPLE_TICKS = 2400;
	static final int SAMPLE_CHANCE_PERCENT = 35;
	/** Past this many sampling entries, the table starts over (it's only a rate limit). */
	private static final int SAMPLE_TABLE_LIMIT = 4096;
	/** At most this many records a second however much dies, the rest going to the tide. */
	private static final int RECORDS_PER_SECOND = 20;
	private static final EquipmentSlot[] GEAR_SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST,
			EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private record SampleKey(IsleSouls.RegionKey region, Identifier type) {
	}

	private static final Map<SampleKey, Long> LAST_SAMPLED = new HashMap<>();
	private static long secondStart;
	private static int recordsThisSecond;

	private static long totalNanos;
	private static long deaths;

	private SoulRecorder() {
	}

	static void initialize() {
		ServerLivingEntityEvents.AFTER_DEATH.register(SoulRecorder::onDeath);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LAST_SAMPLED.clear();
			totalNanos = 0;
			deaths = 0;
		});
	}

	private static void onDeath(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof Mob mob) || !(entity.level() instanceof ServerLevel level) || IsleDimension.is(level)
				|| entity.is(IsleShades.SOULLESS)) {
			return;
		}
		long start = System.nanoTime();
		record(level, mob, source);
		totalNanos += System.nanoTime() - start;
		deaths++;
	}

	private static void record(ServerLevel level, Mob mob, DamageSource source) {
		IsleSouls souls = IsleSouls.get(level.getServer());
		Identifier type = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
		long now = level.getGameTime();
		IsleSouls.RegionKey region = IsleSouls.region(level.dimension().identifier(), mob.getBlockX(), mob.getBlockZ());
		Player killer = mob.getLastHurtByPlayer();
		if (killer == null && source.getEntity() instanceof Player player) {
			killer = player;
		}

		boolean boss = mob.is(IsleShades.SHADE_BOSSES);
		boolean named = mob.hasCustomName();
		UUID owner = named && mob instanceof OwnableEntity pet ? ownerOf(pet) : null;
		boolean notable = boss || named || mob.is(IsleShades.NOTABLE_DEAD);
		if (!notable && killer != null) {
			notable = sampled(region, type, now, mob);
		}
		if (!notable || !underRate(now)) {
			souls.addTide(type, 1);
			return;
		}
		DeathRecord record = new DeathRecord(souls.nextId(), type,
				named ? Optional.of(mob.getCustomName().getString()) : Optional.empty(), gear(mob),
				Optional.ofNullable(killer).map(Player::getUUID), Optional.ofNullable(owner), now, origin(level, region));
		if (boss) {
			souls.recordBoss(record);
		} else {
			souls.record(region, record);
		}
	}

	/** One record per type per region per {@value #SAMPLE_TICKS} ticks, by chance. */
	private static boolean sampled(IsleSouls.RegionKey region, Identifier type, long now, Mob mob) {
		SampleKey key = new SampleKey(region, type);
		Long last = LAST_SAMPLED.get(key);
		if (last != null && now - last < SAMPLE_TICKS) {
			return false;
		}
		if (mob.getRandom().nextInt(100) >= SAMPLE_CHANCE_PERCENT) {
			return false;
		}
		if (LAST_SAMPLED.size() >= SAMPLE_TABLE_LIMIT) {
			LAST_SAMPLED.clear();
		}
		LAST_SAMPLED.put(key, now);
		return true;
	}

	private static boolean underRate(long now) {
		if (now - secondStart >= 20 || now < secondStart) {
			secondStart = now;
			recordsThisSecond = 0;
		}
		return recordsThisSecond++ < RECORDS_PER_SECOND;
	}

	private static UUID ownerOf(OwnableEntity pet) {
		EntityReference<LivingEntity> owner = pet.getOwnerReference();
		return owner == null ? null : owner.getUUID();
	}

	/** The six worn and held item ids, if it had anything at all. */
	private static List<Identifier> gear(Mob mob) {
		List<Identifier> gear = null;
		for (int i = 0; i < GEAR_SLOTS.length; i++) {
			ItemStack stack = mob.getItemBySlot(GEAR_SLOTS[i]);
			if (!stack.isEmpty()) {
				if (gear == null) {
					gear = new ArrayList<>(GEAR_SLOTS.length);
					for (int j = 0; j < GEAR_SLOTS.length; j++) {
						gear.add(BuiltInRegistries.ITEM.getKey(ItemStack.EMPTY.getItem()));
					}
				}
				gear.set(i, BuiltInRegistries.ITEM.getKey(stack.getItem()));
			}
		}
		return gear == null ? List.of() : List.copyOf(gear);
	}

	static EquipmentSlot[] gearSlots() {
		return GEAR_SLOTS;
	}

	/** "dimension x z": the centre of the region it fell in. */
	private static String origin(ServerLevel level, IsleSouls.RegionKey region) {
		int half = 1 << (IsleSouls.REGION_SHIFT - 1);
		return level.dimension().identifier() + " " + ((region.x() << IsleSouls.REGION_SHIFT) + half) + " "
				+ ((region.z() << IsleSouls.REGION_SHIFT) + half);
	}

	public static long deaths() {
		return deaths;
	}

	public static long averageNanos() {
		return deaths == 0 ? 0 : totalNanos / deaths;
	}
}
