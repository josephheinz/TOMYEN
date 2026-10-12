package com.jheinz.mymod.schnozzle;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A snot rocket from a Schnozzle's nostril: a glob of booger under a trail of smoke and slime. Adapted from the
 * PG-7: it flies at a steady {@link #SPEED} without dropping, turns toward its target at most {@link #TURN_DEGREES}
 * a tick (so it can be dodged), and bursts on whatever it hits, within {@link #PROXIMITY} of the target, or when
 * {@link #LIFETIME} runs out. The blast is small ({@link #BLAST_POWER}) and does {@link #DAMAGE_SCALE} of normal
 * explosion damage; it breaks blocks only with mobGriefing on, and never hurts the Schnozzle that fired it.
 */
public class SnotRocket extends ThrowableItemProjectile {
	public static final double SPEED = 0.8;
	private static final int LIFETIME = 100;
	private static final double TURN_DEGREES = 3.0;
	private static final double PROXIMITY = 1.0;
	private static final float BLAST_POWER = 1.4f;
	private static final float DAMAGE_SCALE = 0.5f;

	/** Server only. */
	private @Nullable Entity target;

	public SnotRocket(EntityType<? extends SnotRocket> type, Level level) {
		super(type, level);
	}

	public SnotRocket(Level level, LivingEntity shooter, Vec3 from, Entity target) {
		super(Schnozzles.SNOT_ROCKET, from.x, from.y, from.z, level, new ItemStack(Schnozzles.GIANT_BOOGER));
		setOwner(shooter);
		this.target = target;
	}

	public void launch(Vec3 velocity) {
		setDeltaMovement(velocity);
		updateRotation();
		xRotO = getXRot();
		yRotO = getYRot();
	}

	@Override
	protected Item getDefaultItem() {
		return Schnozzles.GIANT_BOOGER;
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
	protected boolean canHitEntity(Entity entity) {
		return entity != getOwner() && !(entity instanceof SnotRocket) && super.canHitEntity(entity);
	}

	@Override
	public void tick() {
		if (!level().isClientSide() && target != null) {
			steer();
		}
		Vec3 before = position();
		super.tick();
		if (isRemoved()) {
			return;
		}
		if (level().isClientSide()) {
			trail(before);
		} else if (tickCount > LIFETIME || target != null && target.getBoundingBox().inflate(PROXIMITY).contains(position())) {
			burst(position());
		}
	}

	/** Same steering as the PG-7: part way round toward the target, keeping its speed. */
	private void steer() {
		if (!target.isAlive() || target.isRemoved() || target.level() != level()) {
			target = null;
			return;
		}
		Vec3 heading = getDeltaMovement().normalize();
		Vec3 wanted = target.getBoundingBox().getCenter().subtract(position()).normalize();
		double angle = Math.acos(Math.max(-1.0, Math.min(1.0, heading.dot(wanted))));
		double max = Math.toRadians(TURN_DEGREES);
		Vec3 next = wanted;
		if (angle > max) {
			double t = max / angle;
			next = heading.scale(Math.sin((1.0 - t) * angle)).add(wanted.scale(Math.sin(t * angle))).scale(1.0 / Math.sin(angle)).normalize();
		}
		setDeltaMovement(next.scale(SPEED));
		needsSync = true;
	}

	private void trail(Vec3 from) {
		Vec3 to = position();
		for (int i = 0; i < 3; i++) {
			Vec3 p = from.lerp(to, i / 3.0);
			level().addParticle(ParticleTypes.SMOKE, p.x, p.y + 0.2, p.z, 0.0, 0.01, 0.0);
			if (i == 0) {
				level().addParticle(ParticleTypes.LARGE_SMOKE, p.x, p.y + 0.2, p.z, 0.0, 0.02, 0.0);
			}
		}
		if (random.nextInt(2) == 0) {
			level().addParticle(ParticleTypes.ITEM_SLIME, getX(), getY() + 0.2, getZ(), 0.0, 0.0, 0.0);
		}
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (!level().isClientSide()) {
			burst(hit.getLocation());
		}
	}

	private void burst(Vec3 at) {
		level().explode(this, null, new Blast(getOwner()), at.x, at.y, at.z, BLAST_POWER, false, Level.ExplosionInteraction.MOB);
		discard();
	}

	/** Spares the shooter, and hits for {@link #DAMAGE_SCALE} of a normal blast. */
	private static class Blast extends ExplosionDamageCalculator {
		private final @Nullable Entity owner;

		Blast(@Nullable Entity owner) {
			this.owner = owner;
		}

		@Override
		public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
			return entity != owner;
		}

		@Override
		public float getEntityDamageAmount(Explosion explosion, Entity entity, float exposure) {
			return super.getEntityDamageAmount(explosion, entity, exposure) * DAMAGE_SCALE;
		}
	}
}
