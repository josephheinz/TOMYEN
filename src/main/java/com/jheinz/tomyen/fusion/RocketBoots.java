package com.jheinz.tomyen.fusion;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Rocket Boots (Big Red Boots + the Soda-Bottle Jetpack, at a pond): the rubber boots with two soda bottles strapped to
 * the heels as rockets. Diamond boots' armor (3, toughness 1), the boots' halved fall damage, and no waddle.
 * <ul>
 * <li>Double jump: jump again in mid-air for a fizzing burst upward ({@value #JUMP_SPEED} blocks a tick).
 * <li>Air dash: tap sprint in mid-air for a burst along your facing ({@value #DASH_SPEED} blocks a tick).
 * </ul>
 * One of each per time in the air; landing (or water, a ladder) resets them. As with all player movement the wearer's
 * client moves them (RocketBootsClient); it tells the server ({@link Fusions.RocketBurst}), which resets the fall so
 * far, wears the boots by one, and sprays the soda fizz and plays the whoosh for everyone.
 */
public final class RocketBoots {
	public static final int DOUBLE_JUMP = 0;
	public static final int DASH = 1;
	public static final double JUMP_SPEED = 0.62;
	public static final double DASH_SPEED = 1.05;
	public static final double FALL_REDUCTION = 0.5;
	private static final int COLA = 0x6E3A1C;
	private static final int LIME = 0x8CD27A;

	/** Bursts each wearer has fired since they last touched down (a client can't fire more than two a hop). */
	private static final Map<UUID, Integer> BURSTS = new HashMap<>();

	private RocketBoots() {
	}

	static void tick(ServerPlayer player) {
		if (player.onGround() || player.isInWater() || player.onClimbable()) {
			BURSTS.remove(player.getUUID());
		}
	}

	public static boolean isWearing(LivingEntity entity) {
		return entity.getItemBySlot(EquipmentSlot.FEET).is(Fusions.ROCKET_BOOTS);
	}

	/** Whether the boots can fire now: in the air, not swimming, climbing, gliding or flying. */
	public static boolean canFire(Player player) {
		return !player.onGround() && !player.isInWater() && !player.isInLava() && !player.onClimbable() && !player.isFallFlying()
				&& !player.getAbilities().flying && !player.isPassenger() && !player.isSpectator() && isWearing(player);
	}

	static void burst(ServerPlayer player, int kind) {
		if (!isWearing(player) || player.isSpectator()) {
			return;
		}
		int fired = BURSTS.merge(player.getUUID(), 1, Integer::sum);
		if (fired > 2) {
			return;
		}
		ServerLevel level = player.level();
		player.resetFallDistance();
		if (!player.hasInfiniteMaterials()) {
			player.getItemBySlot(EquipmentSlot.FEET).hurtAndBreak(1, player, EquipmentSlot.FEET);
		}
		double x = player.getX(), y = player.getY(), z = player.getZ();
		level.sendParticles(ParticleTypes.POOF, x, y, z, 8, 0.2, 0.05, 0.2, 0.03);
		level.sendParticles(ParticleTypes.BUBBLE_POP, x, y + 0.1, z, 10, 0.25, 0.1, 0.25, 0.05);
		level.sendParticles(new DustParticleOptions(COLA, 1.2f), x, y, z, 8, 0.2, 0.1, 0.2, 0.0);
		level.sendParticles(new DustParticleOptions(LIME, 1.0f), x, y, z, 6, 0.2, 0.1, 0.2, 0.0);
		level.playSound(null, x, y, z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.8f, kind == DASH ? 1.4f : 1.1f);
		level.playSound(null, x, y, z, SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 0.9f, 1.6f);
	}
}
