package com.jheinz.mymod.launcher;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A PG-7 in flight. The booster throws it out of the tube; a few ticks later the sustainer motor lights and
 * pushes it up to full speed, burning for {@link #MOTOR_TICKS} under a trail of smoke and flame. It drops only
 * slightly and explodes on whatever it hits (TNT-like, breaking blocks unless mobGriefing is off), or in the air
 * when its self-destruct runs out.
 *
 * <p>A homing rocket given a target turns toward it every tick, but only {@link #TURN_DEGREES} a tick and at a
 * lower top speed, so a target can outrun it or duck behind cover. It goes off within {@link #PROXIMITY} blocks
 * of the target, and flies straight on if the target dies or disappears. Steering happens on the server, which
 * sends the new heading every tick.
 */
public class PG7Rocket extends ThrowableItemProjectile {
	/** Ticks before the sustainer lights. */
	private static final int BOOST_TICKS = 3;
	private static final int MOTOR_TICKS = 60;
	private static final int LIFETIME = 100;
	private static final double ACCELERATION = 0.25;
	private static final double MAX_SPEED = 4.5;
	private static final double HOMING_MAX_SPEED = 3.0;
	private static final double TURN_DEGREES = 5.0;
	private static final double PROXIMITY = 1.5;
	private static final float BLAST_POWER = 3.0f;
	/** Ticks the shooter is safe from their own rocket, while it clears them. */
	private static final int CLEAR_TICKS = 6;

	/** Server only. */
	private Entity target;

	public PG7Rocket(EntityType<? extends PG7Rocket> type, Level level) {
		super(type, level);
	}

	public PG7Rocket(Level level, LivingEntity shooter, Vec3 muzzle, ItemStack rocket, Entity target) {
		super(Launchers.ROCKET, muzzle.x, muzzle.y, muzzle.z, level, rocket);
		setOwner(shooter);
		this.target = target;
	}

	/** Sends it off along {@code velocity}, facing that way from the first frame. */
	public void launch(Vec3 velocity) {
		setDeltaMovement(velocity);
		updateRotation();
		xRotO = getXRot();
		yRotO = getYRot();
	}

	@Override
	protected Item getDefaultItem() {
		return Launchers.PG7_ROCKET;
	}

	public boolean homing() {
		return getItem().is(Launchers.PG7_HOMING_ROCKET);
	}

	@Override
	protected double getDefaultGravity() {
		return 0.01;
	}

	/** The motor makes up for the drag. */
	@Override
	protected float getAirDrag() {
		return 1.0f;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 160.0 * 160.0;
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		return !(entity == getOwner() && tickCount < CLEAR_TICKS) && super.canHitEntity(entity);
	}

	@Override
	public void tick() {
		if (!level().isClientSide() && target != null) {
			steer();
		}
		if (tickCount >= BOOST_TICKS && tickCount < MOTOR_TICKS) {
			Vec3 velocity = getDeltaMovement();
			double speed = velocity.length();
			double top = homing() ? HOMING_MAX_SPEED : MAX_SPEED;
			if (speed > 1.0e-4 && speed < top) {
				setDeltaMovement(velocity.scale(Math.min(top, speed + ACCELERATION) / speed));
			}
		}
		Vec3 before = position();
		super.tick();
		if (isRemoved()) {
			return;
		}
		if (level().isClientSide()) {
			trail(before);
		} else if (tickCount > LIFETIME || target != null && target.getBoundingBox().inflate(PROXIMITY).contains(position())) {
			detonate(position());
		}
	}

	/** Turns toward the target by at most {@link #TURN_DEGREES}, keeping its speed; lets go once the target's gone. */
	private void steer() {
		if (!target.isAlive() || target.isRemoved() || target.level() != level()) {
			target = null;
			return;
		}
		Vec3 velocity = getDeltaMovement();
		double speed = velocity.length();
		if (speed < 1.0e-4) {
			return;
		}
		Vec3 heading = velocity.scale(1.0 / speed);
		Vec3 wanted = target.getBoundingBox().getCenter().subtract(position()).normalize();
		double angle = Math.acos(Math.max(-1.0, Math.min(1.0, heading.dot(wanted))));
		double max = Math.toRadians(TURN_DEGREES);
		Vec3 next = wanted;
		if (angle > max) {
			// Part way round the great circle from heading to wanted.
			double t = max / angle;
			next = heading.scale(Math.sin((1.0 - t) * angle)).add(wanted.scale(Math.sin(t * angle))).scale(1.0 / Math.sin(angle)).normalize();
		}
		setDeltaMovement(next.scale(speed));
		needsSync = true;
	}

	private void trail(Vec3 from) {
		Vec3 to = position();
		Vec3 back = getDeltaMovement().normalize().scale(-0.55);
		boolean burning = tickCount < MOTOR_TICKS;
		int steps = 4;
		for (int i = 0; i < steps; i++) {
			Vec3 p = from.lerp(to, i / (double) steps).add(back);
			double jitter = 0.02;
			level().addParticle(ParticleTypes.CLOUD, p.x, p.y, p.z, random.nextGaussian() * jitter, random.nextGaussian() * jitter,
					random.nextGaussian() * jitter);
			if (burning && i % 2 == 0) {
				level().addParticle(ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, 0.0, 0.01, 0.0);
				level().addParticle(ParticleTypes.FLAME, p.x, p.y, p.z, back.x * 0.1, back.y * 0.1, back.z * 0.1);
			}
		}
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (!level().isClientSide()) {
			detonate(hit.getLocation());
		}
	}

	private void detonate(Vec3 at) {
		level().explode(this, at.x, at.y, at.z, BLAST_POWER, false, Level.ExplosionInteraction.MOB);
		discard();
	}
}
