package com.jheinz.mymod.napalm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.ModEffects;

/**
 * What napalm does where it lands, shared by the flamethrower's stream, the napalm PG-7 and the grenade. Things it
 * hits burn with the Napalm effect; it leaves puddles of burning gel ({@link BurningGelBlock}) on the ground, and runs
 * down walls to puddle at their feet. Gel only ever goes into empty space, or over grass and the like when mobGriefing
 * allows. Ordinary fire, which can spread and burn blocks away, is only lit where fire may spread: mobGriefing on and
 * within the fire spread gamerule's radius of a player ({@link #mayStartFires}).
 */
public final class NapalmFire {
	/** Chance a glob landing on the ground leaves a puddle (or refreshes the one there). */
	private static final float STREAM_PUDDLE_CHANCE = 0.4f;
	/** Chance a glob hitting something flammable side-on sets it alight. */
	private static final float STREAM_FIRE_CHANCE = 0.25f;
	/** How far gel runs down a wall looking for ground to puddle on. */
	private static final int DRIP_DEPTH = 4;

	private NapalmFire() {
	}

	/** Whether napalm here may light ordinary fire and burn blocks: mobGriefing, and fire allowed to spread here. */
	public static boolean mayStartFires(ServerLevel level, BlockPos pos) {
		return level.getGameRules().get(GameRules.MOB_GRIEFING) && level.canSpreadFireAround(pos);
	}

	/**
	 * Burns something napalm hit: {@code damage} as napalm, then (unless it's fire-proof or in water) sets it alight
	 * and gives it the Napalm effect for {@code burnTicks}, keeping whichever burn is longer.
	 */
	public static void scorch(ServerLevel level, Entity target, Entity owner, float damage, int burnTicks) {
		target.hurtServer(level, level.damageSources().source(Napalm.NAPALM, owner), damage);
		if (target.fireImmune() || target.isInWater() || !target.isAlive()) {
			return;
		}
		target.igniteForTicks(burnTicks);
		if (target instanceof LivingEntity living) {
			living.addEffect(new MobEffectInstance(ModEffects.NAPALM, burnTicks), owner);
		}
	}

	/** Where a glob of the stream hit a block: maybe a puddle under it, maybe fire on what it hit. */
	public static void land(ServerLevel level, BlockHitResult hit, Entity owner) {
		RandomSource random = level.getRandom();
		BlockPos at = hit.getBlockPos().relative(hit.getDirection());
		Vec3 where = hit.getLocation();
		if (random.nextInt(3) == 0) {
			level.sendParticles(ParticleTypes.FLAME, where.x, where.y, where.z, 2, 0.15, 0.05, 0.15, 0.02);
		}
		if (hit.getDirection() == Direction.UP) {
			if (random.nextFloat() < STREAM_PUDDLE_CHANCE) {
				puddle(level, at);
			}
			return;
		}
		BlockState struck = level.getBlockState(hit.getBlockPos());
		if (hit.getDirection() != Direction.DOWN && struck.ignitedByLava() && random.nextFloat() < STREAM_FIRE_CHANCE
				&& mayStartFires(level, at) && BaseFireBlock.canBePlacedAt(level, at, hit.getDirection().getOpposite())) {
			level.setBlockAndUpdate(at, BaseFireBlock.getState(level, at));
			return;
		}
		// Sticky gel runs down walls (and drips off ceilings) and pools at the bottom.
		if (random.nextFloat() < STREAM_PUDDLE_CHANCE * 0.75f) {
			BlockPos floor = floorBelow(level, at, DRIP_DEPTH);
			if (floor != null) {
				puddle(level, floor);
			}
		}
	}

	/**
	 * A splash of napalm bursting at {@code center}: everything within {@code radius} takes up to {@code damage} (less
	 * further out) and burns for {@code burnTicks}, and the ground round it is carpeted in gel, densest in the middle.
	 * The gel goes on to light flammable blocks round it where fire may spread.
	 */
	public static void splash(ServerLevel level, Vec3 center, double radius, float damage, int burnTicks, Entity owner) {
		RandomSource random = level.getRandom();
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius, radius * 0.75, radius),
				entity -> entity.isAlive() && !entity.isSpectator())) {
			double distance = living.getBoundingBox().getCenter().distanceTo(center);
			if (distance <= radius) {
				float near = (float) (1.0 - distance / radius);
				scorch(level, living, owner, damage * (0.35f + 0.65f * near), Math.round(burnTicks * (0.6f + 0.4f * near)));
			}
		}
		BlockPos middle = BlockPos.containing(center);
		int r = (int) Math.ceil(radius);
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz) / radius;
				if (d > 1.0 || random.nextFloat() > 0.9f - 0.45f * d) {
					continue;
				}
				BlockPos floor = floorBelow(level, middle.offset(dx, 2, dz), 5);
				if (floor == null) {
					continue;
				}
				puddle(level, floor);
			}
		}
		level.sendParticles(ParticleTypes.FLAME, center.x, center.y + 0.5, center.z, 60, radius * 0.4, 0.6, radius * 0.4, 0.12);
		level.sendParticles(ParticleTypes.LAVA, center.x, center.y + 0.3, center.z, 18, radius * 0.3, 0.3, radius * 0.3, 0.0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y + 1.0, center.z, 30, radius * 0.4, 0.8, radius * 0.4, 0.03);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 3.0f, 0.5f);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 2.0f, 0.45f);
	}

	/**
	 * Lays (or refreshes) a puddle of burning gel at {@code pos}. It needs solid ground under it and empty space,
	 * or with mobGriefing on, something like grass it can burn away. Returns whether there's gel there now.
	 */
	public static boolean puddle(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.is(Napalm.BURNING_GEL)) {
			BurningGelBlock.refresh(level, pos, state);
			return true;
		}
		if (!level.getFluidState(pos).isEmpty()) {
			return false;
		}
		boolean replaceable = state.isAir() || state.canBeReplaced() && level.getGameRules().get(GameRules.MOB_GRIEFING);
		BlockState gel = Napalm.BURNING_GEL.defaultBlockState();
		if (!replaceable || !gel.canSurvive(level, pos)) {
			return false;
		}
		level.setBlockAndUpdate(pos, gel);
		return true;
	}

	/** The first spot at or below {@code from}, within {@code depth} blocks down, that gel could sit in. */
	private static BlockPos floorBelow(ServerLevel level, BlockPos from, int depth) {
		BlockPos.MutableBlockPos pos = from.mutable();
		for (int i = 0; i <= depth; i++) {
			BlockState state = level.getBlockState(pos);
			if (!state.isAir() && !state.canBeReplaced() && !state.is(Napalm.BURNING_GEL)) {
				return null;
			}
			if (Napalm.BURNING_GEL.defaultBlockState().canSurvive(level, pos)) {
				return pos.immutable();
			}
			pos.move(Direction.DOWN);
		}
		return null;
	}
}
