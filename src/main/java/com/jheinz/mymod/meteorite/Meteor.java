package com.jheinz.mymod.meteorite;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A meteor coming down: a fireball that flies dead straight (no gravity, no drag) trailing flame and smoke, and
 * whistles as it comes. It strikes the first block, creature or water in its path with a {@link #EXPLOSION_POWER}
 * explosion. Unless it fell in water, it leaves a fresh crater with a small meteorite core in it; with
 * {@code mobGriefing} off it breaks nothing, and its core just comes to rest on the ground.
 * <p>
 * Summoned with no motion (by {@code /summon mymod:meteor}), it falls straight down. It burns up after
 * {@link #LIFETIME} ticks if it hasn't hit anything.
 */
public class Meteor extends ThrowableProjectile {
	public static final float SPEED = 1.6f;
	public static final float EXPLOSION_POWER = 4.0f;
	public static final int CRATER_RADIUS_MIN = 4;
	public static final int CORE_RADIUS = 3;
	private static final int LIFETIME = 600;
	/** Loud enough to carry about 160 blocks. */
	private static final float WHISTLE_VOLUME = 10.0f;

	/** Whether it has had its first tick (and whistle) since it was created or loaded. */
	private boolean started;

	public Meteor(EntityType<? extends Meteor> type, Level level) {
		super(type, level);
	}

	/** A meteor at {@code from} heading for {@code to}. */
	public Meteor(Level level, Vec3 from, Vec3 to) {
		super(Meteorites.METEOR, level);
		setPos(from);
		setDeltaMovement(to.subtract(from).normalize().scale(SPEED));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	protected double getDefaultGravity() {
		return 0.0;
	}

	@Override
	protected float getAirDrag() {
		return 1.0f;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 256.0 * 256.0;
	}

	@Override
	public boolean isOnFire() {
		return false;
	}

	@Override
	public void tick() {
		if (!started) {
			started = true;
			if (getDeltaMovement().lengthSqr() < 1.0e-4) {
				setDeltaMovement(0.0, -SPEED, 0.0);
			}
			if (!level().isClientSide()) {
				level().playSound(null, this, Meteorites.METEOR_WHISTLE, SoundSource.AMBIENT, WHISTLE_VOLUME, 0.9f + random.nextFloat() * 0.2f);
			}
		}
		super.tick();
		if (level().isClientSide()) {
			trail();
		} else if (isAlive()) {
			if (isInWater() || isInLava()) {
				impact((ServerLevel) level());
			} else if (tickCount > LIFETIME || getY() < level().getMinY()) {
				discard();
			}
		}
	}

	/** A line of flame along the last tick's path, smoke behind; drawn however far off the meteor is. */
	private void trail() {
		Vec3 motion = getDeltaMovement();
		for (int i = 0; i < 2; i++) {
			double back = i * 0.5;
			level().addAlwaysVisibleParticle(ParticleTypes.FLAME, true, getX() - motion.x * back, getY() + 0.5 - motion.y * back,
					getZ() - motion.z * back, 0.0, 0.0, 0.0);
		}
		level().addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, getX() - motion.x, getY() + 0.5 - motion.y, getZ() - motion.z,
				0.0, 0.02, 0.0);
	}

	@Override
	protected void onHit(HitResult result) {
		super.onHit(result);
		if (level() instanceof ServerLevel level && isAlive()) {
			impact(level);
		}
	}

	private void impact(ServerLevel level) {
		Vec3 at = position();
		boolean griefing = level.getGameRules().get(GameRules.MOB_GRIEFING);
		boolean wet = isInWater() || isInLava();
		// MOB explosions only break blocks when mobGriefing is on.
		level.explode(this, at.x, at.y + 0.5, at.z, EXPLOSION_POWER, griefing && !wet, Level.ExplosionInteraction.MOB);
		level.playSound(null, at.x, at.y, at.z, Meteorites.METEOR_IMPACT, SoundSource.AMBIENT, 12.0f, 0.8f + random.nextFloat() * 0.2f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 8.0f, 0.6f);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1.0, at.z, 60, 2.0, 1.5, 2.0, 0.08);
		level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.5, at.z, 24, 1.5, 0.5, 1.5, 0.0);
		if (!wet) {
			int x = blockPosition().getX(), z = blockPosition().getZ();
			if (griefing) {
				int crater = CRATER_RADIUS_MIN + random.nextInt(2);
				MeteoriteGenerator.place(level, random, x, z, crater, CORE_RADIUS, true, Block.UPDATE_ALL);
			} else {
				MeteoriteGenerator.placeCoreOnly(level, random, x, z, CORE_RADIUS - 1, Block.UPDATE_ALL);
			}
		}
		discard();
	}
}
