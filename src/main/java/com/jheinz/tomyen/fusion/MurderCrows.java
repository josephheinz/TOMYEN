package com.jheinz.tomyen.fusion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;

import com.jheinz.tomyen.crow.Crow;
import com.jheinz.tomyen.crow.Crows;

/**
 * Murder Wings (Crow Feather + Elytra, in the reactor) and the Stormcrow Rig: whatever the wearer hurts (a mob, not a
 * player or a villager), a couple of crows come cawing out of the air to peck at it. Few and short-lived: at most
 * {@value #PER_HIT} a hit, {@value #MAX_ALIVE} at once per wearer, none more often than every
 * {@value #COOLDOWN_TICKS} ticks, and each vanishes after {@value #LIFETIME} ticks (or when its target dies). They're
 * ordinary {@link Crow}s in a summoned mode ({@link Crow#summonAgainst}): no loot, no murder of their own.
 */
public final class MurderCrows {
	private static final int PER_HIT = 2;
	private static final int MAX_ALIVE = 4;
	private static final int COOLDOWN_TICKS = 30;
	private static final int LIFETIME = 160;

	private static final Map<UUID, Long> NEXT = new HashMap<>();
	private static final Map<UUID, List<Crow>> CALLED = new HashMap<>();

	private MurderCrows() {
	}

	public static boolean isWearing(LivingEntity entity) {
		var chest = entity.getItemBySlot(EquipmentSlot.CHEST);
		return chest.is(Fusions.MURDER_WINGS) || chest.is(Fusions.STORMCROW_RIG);
	}

	static void initialize() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> {
			if (blocked || damage <= 0.0f || !(source.getEntity() instanceof ServerPlayer player) || entity == player
					|| entity instanceof Player || entity instanceof AbstractVillager || entity instanceof Crow || !isWearing(player)) {
				return;
			}
			call(player, entity);
		});
	}

	private static void call(ServerPlayer player, LivingEntity target) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (NEXT.getOrDefault(player.getUUID(), 0L) > now) {
			return;
		}
		List<Crow> called = CALLED.computeIfAbsent(player.getUUID(), id -> new ArrayList<>());
		called.removeIf(crow -> !crow.isAlive());
		int room = Math.min(PER_HIT, MAX_ALIVE - called.size());
		if (room <= 0) {
			return;
		}
		NEXT.put(player.getUUID(), now + COOLDOWN_TICKS);
		for (int i = 0; i < room; i++) {
			Crow crow = Crows.CROW.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (crow == null) {
				continue;
			}
			float angle = level.getRandom().nextFloat() * Mth.TWO_PI;
			double x = player.getX() + Mth.cos(angle) * 2.0;
			double z = player.getZ() + Mth.sin(angle) * 2.0;
			double y = player.getY() + 2.5 + level.getRandom().nextDouble();
			crow.snapTo(x, y, z, angle * Mth.RAD_TO_DEG, 0.0f);
			level.addFreshEntity(crow);
			crow.summonAgainst(target, LIFETIME);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 4, 0.2, 0.2, 0.2, 0.01);
			called.add(crow);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), Crows.ALARM, player.getSoundSource(), 1.0f, 0.9f);
	}
}
