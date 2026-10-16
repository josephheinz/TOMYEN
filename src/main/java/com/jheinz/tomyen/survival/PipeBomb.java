package com.jheinz.tomyen.survival;

import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A thrown pipe bomb: it clatters and bounces to rest, its fuse spitting sparks, and goes off {@link #FUSE_TICKS}
 * after it left the hand. The blast hurts everything within {@link #RADIUS} blocks (up to {@link #DAMAGE}, falling off
 * with distance) and throws it back, but leaves blocks alone; machines caught in it ({@link Survival#MACHINES}) are
 * Stunned for {@link #STUN_TICKS}, long enough to get a crowbar into their panels.
 */
public class PipeBomb extends ThrowableItemProjectile {
	public static final int FUSE_TICKS = 40;
	public static final double RADIUS = 4.0;
	public static final float DAMAGE = 8.0f;
	public static final int STUN_TICKS = 80;

	public PipeBomb(EntityType<? extends PipeBomb> type, Level level) {
		super(type, level);
	}

	public PipeBomb(Level level, LivingEntity thrower, ItemStack stack) {
		super(Survival.PIPE_BOMB, thrower, level, stack);
	}

	@Override
	protected Item getDefaultItem() {
		return Survival.PIPE_BOMB_ITEM;
	}

	@Override
	public void tick() {
		super.tick();
		if (isRemoved()) {
			return;
		}
		if (level().isClientSide()) {
			level().addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.2, getZ(), 0.0, 0.02, 0.0);
			if (tickCount % 2 == 0) {
				level().addParticle(ParticleTypes.SMALL_FLAME, getX(), getY() + 0.2, getZ(), 0.0, 0.01, 0.0);
			}
		} else {
			if (tickCount % 8 == 1) {
				level().playSound(null, getX(), getY(), getZ(), Survival.PIPE_BOMB_FUSE, SoundSource.NEUTRAL, 0.6f, 1.0f + tickCount / 80.0f);
			}
			if (tickCount >= FUSE_TICKS && level() instanceof ServerLevel level) {
				explode(level);
			}
		}
	}

	/** Bounces off what it hits, losing most of its speed, and settles on the ground. */
	@Override
	protected void onHitBlock(BlockHitResult hit) {
		super.onHitBlock(hit);
		Vec3 motion = getDeltaMovement();
		Direction face = hit.getDirection();
		double x = motion.x * 0.5;
		double y = motion.y * 0.5;
		double z = motion.z * 0.5;
		switch (face.getAxis()) {
			case X -> x = -motion.x * 0.3;
			case Y -> y = -motion.y * 0.3;
			case Z -> z = -motion.z * 0.3;
		}
		if (face == Direction.UP && Math.abs(y) < 0.08) {
			y = 0.0;
			x *= 0.6;
			z *= 0.6;
		}
		setDeltaMovement(x, y, z);
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		setDeltaMovement(getDeltaMovement().scale(-0.15));
	}

	private void explode(ServerLevel level) {
		Vec3 center = position().add(0.0, 0.15, 0.0);
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(RADIUS),
				entity -> entity.isAlive() && !entity.isSpectator())) {
			Vec3 to = living.getBoundingBox().getCenter().subtract(center);
			double distance = to.length();
			if (distance > RADIUS) {
				continue;
			}
			float near = (float) (1.0 - distance / RADIUS);
			living.hurtServer(level, level.damageSources().explosion(this, getOwner()), DAMAGE * near + 1.0f);
			Vec3 push = distance > 0.01 ? to.normalize() : new Vec3(0.0, 1.0, 0.0);
			double strength = 0.9 * near * (1.0 - living.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE));
			living.push(push.x * strength, 0.25 + 0.3 * near, push.z * strength);
			living.needsSync = true;
			if (living.is(Survival.MACHINES)) {
				living.addEffect(new MobEffectInstance(Survival.STUNNED, STUN_TICKS), getOwner());
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, living.getX(), living.getY(0.6), living.getZ(), 16, 0.4, 0.5, 0.4, 0.2);
			}
		}
		level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y + 0.3, center.z, 16, 0.6, 0.4, 0.6, 0.04);
		level.sendParticles(ParticleTypes.CRIT, center.x, center.y + 0.2, center.z, 24, 0.3, 0.3, 0.3, 0.6);
		level.playSound(null, center.x, center.y, center.z, Survival.PIPE_BOMB_BLAST, SoundSource.NEUTRAL, 2.0f,
				0.95f + level.getRandom().nextFloat() * 0.1f);
		discard();
	}
}
