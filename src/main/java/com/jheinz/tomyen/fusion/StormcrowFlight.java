package com.jheinz.tomyen.fusion;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The Stormcrow Rig (Rocket Boots + Murder Wings, tier 2, in the reactor): storm-dark crow wings over a pair of soda
 * rockets turned into real thrusters. It glides like an elytra; hold jump while gliding and the thrusters drive you
 * along your gaze, up to {@value #TOP_SPEED} blocks a tick, climbing if you look up: sustained, steerable flight.
 * Thrust costs the rig one durability every {@value #WEAR_TICKS} ticks (on top of an elytra's gliding wear), so its
 * {@code 2400} last some ten minutes of thrust between repairs with Star Steel. Its crows come with it (see
 * {@link MurderCrows}).
 *
 * <p>The wearer's client pushes them ({@link #thrust}, from RocketBootsClient); the server, from the same inputs,
 * wears the rig and throws the sparks and the roar for everyone.
 */
public final class StormcrowFlight {
	public static final double TOP_SPEED = 1.7;
	/** How hard the thrusters pull the velocity toward top speed along the gaze each tick. */
	public static final double PULL = 0.07;
	public static final double PUSH = 0.05;
	private static final int WEAR_TICKS = 5;

	private StormcrowFlight() {
	}

	public static boolean isWearing(LivingEntity entity) {
		return entity.getItemBySlot(EquipmentSlot.CHEST).is(Fusions.STORMCROW_RIG);
	}

	/** Whether the rig can push: worn, gliding, not about to break. */
	public static boolean canThrust(Player player) {
		ItemStack rig = player.getItemBySlot(EquipmentSlot.CHEST);
		return rig.is(Fusions.STORMCROW_RIG) && player.isFallFlying() && !rig.nextDamageWillBreak();
	}

	/** The velocity after one tick of thrust, from the current velocity and the gaze. */
	public static Vec3 thrust(Vec3 motion, Vec3 look) {
		Vec3 target = look.scale(TOP_SPEED);
		return motion.add(look.scale(PUSH)).add(target.subtract(motion).scale(PULL));
	}

	static void tick(ServerLevel level) {
		for (ServerPlayer player : level.players()) {
			RocketBoots.tick(player);
			if (!canThrust(player) || !player.getLastClientInput().jump()) {
				continue;
			}
			if (player.tickCount % WEAR_TICKS == 0 && !player.hasInfiniteMaterials()) {
				player.getItemBySlot(EquipmentSlot.CHEST).hurtAndBreak(1, player, EquipmentSlot.CHEST);
			}
			Vec3 look = player.getLookAngle();
			Vec3 back = player.position().add(0.0, 0.4, 0.0).subtract(look.scale(0.6));
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, back.x, back.y, back.z, 3, 0.15, 0.15, 0.15, 0.05);
			level.sendParticles(ParticleTypes.CLOUD, back.x, back.y, back.z, 1, 0.1, 0.1, 0.1, 0.01);
			if (player.tickCount % 3 == 0) {
				level.sendParticles(ParticleTypes.BUBBLE_POP, back.x, back.y, back.z, 2, 0.1, 0.1, 0.1, 0.02);
			}
			if (player.tickCount % 8 == 0) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.5f,
						0.7f + level.getRandom().nextFloat() * 0.2f);
			}
		}
	}
}
