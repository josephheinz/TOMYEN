package com.jheinz.mymod.survival;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.napalm.Napalm;
import com.jheinz.mymod.napalm.NapalmFire;

/**
 * A thrown Molotov cocktail: a bottle of fish oil with a burning rag, trailing flame. It shatters on whatever it hits,
 * sets everything within {@link #RADIUS} blocks alight ({@link #DAMAGE} at the centre, less further out) and splashes a
 * few puddles of burning gel round the spot. Napalm's little cousin: no Napalm effect on a direct splash, a smaller
 * spread, and like napalm's gel the puddles never spread fire where mobGriefing is off.
 */
public class Molotov extends ThrowableItemProjectile {
	public static final double RADIUS = 2.5;
	public static final float DAMAGE = 3.0f;
	public static final int BURN_TICKS = 100;

	public Molotov(EntityType<? extends Molotov> type, Level level) {
		super(type, level);
	}

	public Molotov(Level level, LivingEntity thrower, ItemStack stack) {
		super(Survival.MOLOTOV, thrower, level, stack);
	}

	@Override
	protected Item getDefaultItem() {
		return Survival.MOLOTOV_COCKTAIL;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide() && !isRemoved()) {
			level().addParticle(ParticleTypes.FLAME, getX(), getY() + 0.15, getZ(), 0.0, 0.01, 0.0);
			if (tickCount % 2 == 0) {
				level().addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.2, getZ(), 0.0, 0.02, 0.0);
			}
		}
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (level() instanceof ServerLevel level) {
			Vec3 at = hit.getLocation().subtract(getDeltaMovement().normalize().scale(0.2));
			burst(level, at);
			discard();
		}
	}

	private void burst(ServerLevel level, Vec3 center) {
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(RADIUS, RADIUS * 0.75, RADIUS),
				entity -> entity.isAlive() && !entity.isSpectator())) {
			double distance = living.getBoundingBox().getCenter().distanceTo(center);
			if (distance > RADIUS) {
				continue;
			}
			float near = (float) (1.0 - distance / RADIUS);
			living.hurtServer(level, level.damageSources().source(Napalm.NAPALM, this, getOwner()), DAMAGE * (0.4f + 0.6f * near));
			if (!living.fireImmune() && !living.isInWater()) {
				living.igniteForTicks(Math.round(BURN_TICKS * (0.6f + 0.4f * near)));
			}
		}
		RandomSource random = level.getRandom();
		BlockPos middle = BlockPos.containing(center);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz) / 2.0;
				if (d > 1.0 || random.nextFloat() > 0.75f - 0.45f * d) {
					continue;
				}
				BlockPos floor = floorBelow(level, middle.offset(dx, 1, dz));
				if (floor != null) {
					NapalmFire.puddle(level, floor);
				}
			}
		}
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.GLASS_BOTTLE), center.x, center.y, center.z, 12,
				0.2, 0.2, 0.2, 0.15);
		level.sendParticles(ParticleTypes.FLAME, center.x, center.y + 0.4, center.z, 36, RADIUS * 0.35, 0.4, RADIUS * 0.35, 0.08);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y + 0.8, center.z, 12, RADIUS * 0.3, 0.5, RADIUS * 0.3, 0.02);
		level.playSound(null, center.x, center.y, center.z, Survival.MOLOTOV_SHATTER, SoundSource.NEUTRAL, 1.2f, 0.9f + random.nextFloat() * 0.2f);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 1.5f, 0.8f);
	}

	/** The first spot at or below {@code from}, within three blocks down, where a puddle could sit. */
	private static BlockPos floorBelow(ServerLevel level, BlockPos from) {
		BlockPos.MutableBlockPos pos = from.mutable();
		for (int i = 0; i <= 3; i++) {
			BlockState state = level.getBlockState(pos);
			if (!state.isAir() && !state.canBeReplaced() && !state.is(Napalm.BURNING_GEL)) {
				return null;
			}
			BlockPos below = pos.below();
			if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
				return pos.immutable();
			}
			pos.move(Direction.DOWN);
		}
		return null;
	}
}
