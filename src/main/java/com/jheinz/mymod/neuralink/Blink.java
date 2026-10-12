package com.jheinz.mymod.neuralink;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Teleports the player up to {@link #RANGE} blocks along their line of sight, stopping short of the first solid
 * block. It looks back along the ray for the furthest spot the player fits (stepping up onto a ledge if needed),
 * never into lava, and cancels any fall; a landing well off the ground gets a moment of slow falling.
 */
final class Blink implements NeuralCore.Ability {
	public static final double RANGE = 9.0;
	private static final double MIN_DISTANCE = 1.5;
	private static final double STEP = 0.4;
	/** How far below the destination counts as "near the ground". */
	private static final int SAFE_DROP = 3;
	private static final int SLOW_FALL_TICKS = 40;

	@Override
	public boolean start(ServerPlayer player, NeuralCore.State state) {
		ServerLevel level = (ServerLevel) player.level();
		Vec3 destination = findDestination(level, player);
		if (destination == null) {
			return false;
		}
		Vec3 origin = player.position();
		player.stopRiding();
		player.teleportTo(destination.x, destination.y, destination.z);
		player.resetFallDistance();
		if (!nearGround(level, destination)) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALL_TICKS, 0, false, false, true));
		}

		for (Vec3 at : new Vec3[] {origin, destination}) {
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1.0, at.z, 30, 0.3, 0.6, 0.3, 0.05);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 1.0, at.z, 10, 0.3, 0.6, 0.3, 0.3);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.7f);
		}
		ServerPlayNetworking.send(player, new NeuralNetworking.Flash(NeuralNetworking.Flash.BLINK));
		return true;
	}

	/** The furthest safe spot for the player's feet along their line of sight, or null if there's none worth jumping to. */
	private static Vec3 findDestination(ServerLevel level, ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0f);
		BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		double reach = hit.getType() == HitResult.Type.MISS ? RANGE : eye.distanceTo(hit.getLocation()) - 0.3;
		double eyeHeight = player.getEyeHeight();
		// Feet at eye level along the ray first, then lifted onto whatever's in the way, then the full body height.
		double[] lifts = {0.0, 0.6, 1.2, eyeHeight};
		for (double distance = reach; distance >= MIN_DISTANCE; distance -= STEP) {
			Vec3 feet = eye.add(look.scale(distance)).subtract(0.0, eyeHeight, 0.0);
			for (double lift : lifts) {
				Vec3 candidate = feet.add(0.0, lift, 0.0);
				AABB box = player.getDimensions(player.getPose()).makeBoundingBox(candidate);
				if (level.noCollision(player, box) && !touchesLava(level, box)) {
					return candidate;
				}
			}
		}
		return null;
	}

	private static boolean touchesLava(ServerLevel level, AABB box) {
		return level.getBlockStates(box.inflate(0.5)).anyMatch(state -> state.getFluidState().is(FluidTags.LAVA));
	}

	private static boolean nearGround(ServerLevel level, Vec3 feet) {
		BlockPos.MutableBlockPos pos = BlockPos.containing(feet).mutable();
		for (int i = 0; i <= SAFE_DROP; i++) {
			if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
				return true;
			}
			pos.move(0, -1, 0);
		}
		return false;
	}
}
