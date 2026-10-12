package com.jheinz.mymod.neuralink;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.ModDamageTypes;

/**
 * Hold to fire a beam from each eye along the line of sight, out to {@link #RANGE} blocks. Mobs in the beam
 * burn and take {@link #DAMAGE} every half second. Held on one block, it slowly melts it: sand to glass, ice
 * to water, snow away; anything that burns catches fire, and anything else just smoulders.
 */
final class OpticLasers implements NeuralCore.Ability {
	public static final double RANGE = 32.0;
	private static final float DAMAGE = 3.0f;
	/** Matches a mob's invulnerability after a hit, so every pulse lands. */
	private static final int DAMAGE_INTERVAL = 10;
	private static final int BURN_TICKS = 80;
	/** How long the beam has to stay on a flammable block to light it. */
	private static final int IGNITE_TICKS = 30;

	@Override
	public boolean start(ServerPlayer player, NeuralCore.State state) {
		broadcast(player, true);
		player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 2.0f);
		return true;
	}

	@Override
	public boolean channel(ServerPlayer player, NeuralCore.State state) {
		ServerLevel level = (ServerLevel) player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0f).scale(RANGE));
		// Outlines rather than colliders, so the beam stops at (and burns) grass and snow layers too.
		BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		double reach = block.getType() == HitResult.Type.MISS ? RANGE : block.getLocation().distanceTo(eye);

		Entity target = NeuralTargets.lookedAt(player, reach, entity -> entity.isPickable());
		if (target != null) {
			state.meltPos = null;
			if (player.tickCount % DAMAGE_INTERVAL == 0) {
				target.hurtServer(level, level.damageSources().source(ModDamageTypes.OPTIC_LASER, player), DAMAGE);
				target.igniteForTicks(BURN_TICKS);
			}
			level.sendParticles(ParticleTypes.SMALL_FLAME, target.getX(), target.getY(0.6), target.getZ(), 2, 0.2, 0.3, 0.2, 0.01);
		} else if (block.getType() == HitResult.Type.BLOCK) {
			melt(level, player, state, block);
		}
		if (player.tickCount % 8 == 0) {
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.6f, 1.6f);
		}
		// Now and then, for anyone who came into view since it started.
		if (player.tickCount % 20 == 0) {
			broadcast(player, true);
		}
		return true;
	}

	@Override
	public void stop(ServerPlayer player, NeuralCore.State state, boolean interrupted) {
		broadcast(player, false);
		player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6f, 2.0f);
	}

	/** Heats the block the beam rests on, changing it once it's been held there long enough. */
	private static void melt(ServerLevel level, ServerPlayer player, NeuralCore.State state, BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		if (!pos.equals(state.meltPos)) {
			state.meltPos = pos;
			state.progress = 0;
		}
		state.progress++;
		Vec3 at = hit.getLocation();
		if (state.progress % 2 == 0) {
			level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.01);
		}
		if (state.progress % 6 == 0) {
			level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		if (!player.mayBuild() || !level.mayInteract(player, pos)) {
			return;
		}

		BlockState block = level.getBlockState(pos);
		BlockState result = null;
		int needed = 0;
		if (block.is(Blocks.SAND)) {
			result = Blocks.GLASS.defaultBlockState();
			needed = 30;
		} else if (block.is(Blocks.RED_SAND)) {
			result = Blocks.STAINED_GLASS.orange().defaultBlockState();
			needed = 30;
		} else if (block.is(Blocks.ICE) || block.is(Blocks.FROSTED_ICE)) {
			// Ice melted where water boils off (the Nether) just goes, like ice broken there.
			boolean evaporates = level.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, pos);
			result = evaporates ? Blocks.AIR.defaultBlockState() : Blocks.WATER.defaultBlockState();
			needed = 20;
		} else if (block.is(Blocks.PACKED_ICE)) {
			result = Blocks.ICE.defaultBlockState();
			needed = 40;
		} else if (block.is(Blocks.BLUE_ICE)) {
			result = Blocks.PACKED_ICE.defaultBlockState();
			needed = 60;
		} else if (block.is(Blocks.SNOW) || block.is(Blocks.POWDER_SNOW)) {
			result = Blocks.AIR.defaultBlockState();
			needed = 6;
		} else if (block.is(Blocks.SNOW_BLOCK)) {
			result = Blocks.AIR.defaultBlockState();
			needed = 15;
		}

		if (result != null) {
			if (state.progress >= needed) {
				level.setBlock(pos, result, Block.UPDATE_ALL);
				level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 1.8f);
				level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.02);
				state.meltPos = null;
			}
		} else if (block.ignitedByLava() && state.progress == IGNITE_TICKS) {
			BlockPos firePos = pos.relative(hit.getDirection());
			if (level.getBlockState(firePos).isAir()) {
				level.setBlock(firePos, BaseFireBlock.getState(level, firePos), Block.UPDATE_ALL);
			}
		}
	}

	/** Tells everyone who can see the player (and the player) to draw or stop drawing their beams. */
	private static void broadcast(ServerPlayer player, boolean on) {
		NeuralNetworking.Beam beam = new NeuralNetworking.Beam(player.getId(), on);
		for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
			ServerPlayNetworking.send(watcher, beam);
		}
		ServerPlayNetworking.send(player, beam);
	}
}
