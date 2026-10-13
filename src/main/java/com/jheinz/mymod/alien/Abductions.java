package com.jheinz.mymod.alien;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.MyMod;

/**
 * Aliens carrying people off in their sleep. If someone in survival or adventure goes to bed (or is asleep) within
 * {@value #RANGE} blocks of a landed saucer's crew, the crew set off for their bed, and the night isn't skipped while
 * they're on their way: for up to {@value #WINDOW} ticks, that sleeper doesn't count as "sleeping long enough" to
 * vanilla (Fabric's {@code ALLOW_RESETTING_TIME}, which is exactly vanilla's check for skipping the night), so the
 * usual five-second skip waits on the aliens. The aliens don't stop anyone getting into bed: they aren't monsters.
 * <p>
 * An alien that gets within {@value #REACH} blocks of the bed while its owner sleeps takes them: they're woken and
 * lifted up a {@link TractorBeam} into the dark, eerie hum and all, and set down at a safe spot on the surface
 * {@value #MIN_DISTANCE}-{@value #MAX_DISTANCE} blocks away, Probed (glowing, queasy, and none the worse), maybe a
 * souvenir short (one item from somewhere in their inventory other than their hands and armor). The saucer then
 * calls its crew in and leaves. Waking up in time, or hurting one of them (the whole crew bolts for the saucer), or a
 * bed the aliens can't find a way to, foils it; so does creative or spectator mode, or the {@code mymod:alien_abductions}
 * game rule set to false.
 */
public final class Abductions {
	public static final GameRule<Boolean> ALIEN_ABDUCTIONS = GameRuleBuilder.forBoolean(true)
			.category(GameRuleCategory.MOBS)
			.buildAndRegister(MyMod.id("alien_abductions"));

	static final int RANGE = 48;
	/** How long the aliens have, at most, before the night skips without them. */
	static final int WINDOW = 400;
	static final double REACH = 1.8;
	static final int MIN_DISTANCE = 500;
	static final int MAX_DISTANCE = 1500;
	private static final int PROBED_TICKS = 2400;
	private static final int NAUSEA_TICKS = 300;

	private record Attempt(ServerLevel level, BlockPos bed, long until) {
	}

	private static final Map<UUID, Attempt> ATTEMPTS = new HashMap<>();

	private Abductions() {
	}

	public static void initialize() {
		EntitySleepEvents.START_SLEEPING.register((entity, bed) -> {
			if (entity instanceof ServerPlayer player) {
				tryStart(player, bed);
			}
		});
		EntitySleepEvents.ALLOW_RESETTING_TIME.register(player -> !ATTEMPTS.containsKey(player.getUUID()));
		ServerTickEvents.END_LEVEL_TICK.register(Abductions::tick);
	}

	/** The crew of every landed saucer within range of this bed. */
	private static List<Alien> crewNear(ServerLevel level, BlockPos bed) {
		return level.getEntitiesOfClass(Alien.class, new AABB(bed).inflate(RANGE), alien -> {
			Ufo ship = alien.ship();
			return ship != null && ship.isLanded() && !ship.isRecalling() && !alien.isDazed();
		});
	}

	private static void tryStart(ServerPlayer player, BlockPos bed) {
		ServerLevel level = player.level();
		if (!level.getGameRules().get(ALIEN_ABDUCTIONS) || player.isCreative() || player.isSpectator() || ATTEMPTS.containsKey(player.getUUID())) {
			return;
		}
		List<Alien> crew = crewNear(level, bed);
		if (crew.isEmpty()) {
			return;
		}
		ATTEMPTS.put(player.getUUID(), new Attempt(level, bed, level.getGameTime() + WINDOW));
		for (Alien alien : crew) {
			alien.goToBed(bed);
		}
		// Something chitters outside.
		level.playSound(null, bed, Aliens.ALIEN_AMBIENT, SoundSource.HOSTILE, 0.6f, 0.8f);
	}

	private static void tick(ServerLevel level) {
		long now = level.getGameTime();
		if (now % 10 == 0) {
			// Anyone asleep when a crew turned up.
			for (ServerPlayer player : level.players()) {
				if (player.isSleeping() && !ATTEMPTS.containsKey(player.getUUID())) {
					player.getSleepingPos().ifPresent(bed -> tryStart(player, bed));
				}
			}
		}
		for (Map.Entry<UUID, Attempt> entry : new ArrayList<>(ATTEMPTS.entrySet())) {
			Attempt attempt = entry.getValue();
			if (attempt.level() != level) {
				continue;
			}
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
			if (player == null || player.level() != level || !player.isSleeping() || now > attempt.until()) {
				end(entry.getKey(), attempt);
				continue;
			}
			Vec3 bed = Vec3.atBottomCenterOf(attempt.bed());
			for (Alien alien : crewNear(level, attempt.bed())) {
				if (alien.position().subtract(bed).horizontalDistance() < REACH && Math.abs(alien.getY() - bed.y) < 2.0) {
					ATTEMPTS.remove(entry.getKey());
					abduct(level, player, alien);
					break;
				}
			}
		}
	}

	/** Called off: the aliens go back to wandering. */
	private static void end(UUID player, Attempt attempt) {
		ATTEMPTS.remove(player);
		for (Alien alien : attempt.level().getEntitiesOfClass(Alien.class, new AABB(attempt.bed()).inflate(RANGE * 2),
				alien -> attempt.bed().equals(alien.bed()))) {
			alien.forgetBed();
		}
	}

	private static void abduct(ServerLevel level, ServerPlayer player, Alien taker) {
		for (Alien alien : level.getEntitiesOfClass(Alien.class, taker.getBoundingBox().inflate(RANGE * 2), alien -> alien.bed() != null)) {
			alien.forgetBed();
		}
		player.stopSleepInBed(true, true);
		if (TractorBeam.liftPlayer(level, player) == null) {
			return;
		}
		player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, TractorBeam.PLAYER_TICKS + 40, 0, false, false));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), Aliens.ABDUCTION, SoundSource.HOSTILE, 1.5f, 1.0f);
		Ufo ship = taker.ship();
		if (ship != null) {
			// Got what it came for.
			ship.recall();
		}
	}

	/** At the top of the beam: set down far away, probed, and maybe missing something. */
	static void deliver(ServerPlayer player) {
		ServerLevel level = player.level();
		RandomSource random = player.getRandom();
		BlockPos drop = null;
		for (int i = 0; i < 12 && drop == null; i++) {
			double angle = random.nextDouble() * Mth.TWO_PI;
			double distance = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
			int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
			int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
			if (!level.getWorldBorder().isWithinBounds(x, z)) {
				continue;
			}
			// Loads (or generates) the spot, like a spreadplayers does.
			level.getChunk(x >> 4, z >> 4);
			BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
			if (top.getY() > level.getMinY() + 1 && level.getFluidState(top.below()).isEmpty() && level.getFluidState(top).isEmpty()
					&& level.getBlockState(top.below()).isSolidRender()) {
				drop = top;
			}
		}
		if (drop == null) {
			// Nowhere better: put back down where they were taken from.
			drop = BlockPos.containing(player.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.getBlockX(), player.getBlockZ()),
					player.getZ());
		}
		player.teleportTo(level, drop.getX() + 0.5, drop.getY(), drop.getZ() + 0.5, Set.of(), random.nextFloat() * 360.0f, 0.0f, true);
		player.resetFallDistance();
		player.addEffect(new MobEffectInstance(Aliens.PROBED, PROBED_TICKS));
		player.addEffect(new MobEffectInstance(MobEffects.GLOWING, PROBED_TICKS, 0, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, NAUSEA_TICKS));
		player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false));
		player.sendSystemMessage(Component.translatable("entity.mymod.ufo.returned").withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC));
		if (random.nextBoolean()) {
			takeSouvenir(player, random);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), Aliens.UFO_TAKE, SoundSource.HOSTILE, 1.0f, 0.6f);
	}

	/** One item off a random stack, from anywhere but their hands and armor. */
	private static void takeSouvenir(ServerPlayer player, RandomSource random) {
		List<Integer> slots = new ArrayList<>();
		int held = player.getInventory().getSelectedSlot();
		for (int i = 0; i < 36; i++) {
			if (i != held && !player.getInventory().getItem(i).isEmpty()) {
				slots.add(i);
			}
		}
		if (slots.isEmpty()) {
			return;
		}
		ItemStack stack = player.getInventory().getItem(slots.get(random.nextInt(slots.size())));
		ItemStack taken = stack.split(1);
		player.sendSystemMessage(Component.translatable("entity.mymod.ufo.souvenir", taken.getHoverName()).withStyle(ChatFormatting.GRAY,
				ChatFormatting.ITALIC));
	}
}
