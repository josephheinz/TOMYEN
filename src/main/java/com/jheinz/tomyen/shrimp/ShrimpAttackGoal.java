package com.jheinz.tomyen.shrimp;

import java.util.EnumSet;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.GunItem;
import com.jheinz.tomyen.ModDamageTypes;

/**
 * How the Shrimp fights. It keeps {@value #NEAR}-{@value #FAR} blocks off its target, closing in when it's further or
 * out of sight, backing away when it's closer, and side-stepping now and then on land. With a clear shot it raises the
 * pistol and draws a bead for {@value #AIM_TICKS} ticks (the hammer clicks back), then fires its {@value Shrimp#ROUNDS}
 * rounds {@value #SHOT_INTERVAL} ticks apart: hitscan, like the player's guns, so plate carriers stop them the same
 * way, each straying up to {@value #SPREAD} degrees.
 *
 * <p>Then it has to reload: {@value Shrimp#RELOAD_TICKS} ticks thumbing the rounds back in and racking the slide, when it
 * can't shoot, stops backing off and only shuffles. That's the window to close in. Within reach of its claw it snaps at
 * whatever's there, reloading or not, every {@value #SNAP_COOLDOWN} ticks.
 */
class ShrimpAttackGoal extends Goal {
	private static final int AIM_TICKS = 14;
	private static final int SHOT_INTERVAL = 5;
	private static final float BULLET_DAMAGE = 3.5f;
	private static final float SPREAD = 3.5f;
	private static final double RANGE = 32.0;
	/** It only starts a string of shots within this range. */
	private static final double SHOOT_RANGE = 22.0;
	private static final double NEAR = 7.0;
	private static final double FAR = 14.0;
	private static final int SNAP_COOLDOWN = 20;
	/** Its claw reaches this far from its middle (plus the target's half-width). */
	private static final double SNAP_REACH = 1.6;
	/** Rest between one reload and the next string of shots. */
	private static final int VOLLEY_COOLDOWN = 20;

	private final Shrimp shrimp;
	private int stateTicks;
	private int shotsLeft;
	private int snapCooldown;
	private int volleyCooldown;
	private int repathTicks;
	private int strafeTicks;
	private float strafe;

	ShrimpAttackGoal(Shrimp shrimp) {
		this.shrimp = shrimp;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return shrimp.liveTarget() != null;
	}

	@Override
	public boolean canContinueToUse() {
		return shrimp.liveTarget() != null;
	}

	@Override
	public void start() {
		volleyCooldown = 10 + shrimp.getRandom().nextInt(20);
		repathTicks = 0;
		shrimp.setAggressive(true);
	}

	@Override
	public void stop() {
		// Losing its target mid-reload, it finishes off-screen: the gun's full when it next fights.
		shrimp.setAttackState(Shrimp.IDLE);
		shrimp.setAggressive(false);
		shrimp.getNavigation().stop();
		shrimp.getMoveControl().strafe(0.0f, 0.0f);
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		LivingEntity target = shrimp.liveTarget();
		if (target == null) {
			return;
		}
		ServerLevel level = (ServerLevel) shrimp.level();
		shrimp.getLookControl().setLookAt(target, 30.0f, 30.0f);
		snapCooldown = Math.max(0, snapCooldown - 1);
		volleyCooldown = Math.max(0, volleyCooldown - 1);
		repathTicks = Math.max(0, repathTicks - 1);
		boolean sees = shrimp.getSensing().hasLineOfSight(target);
		double distance = shrimp.distanceTo(target);

		if (snapCooldown == 0 && inReach(target)) {
			shrimp.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			shrimp.doHurtTarget(level, target);
			snapCooldown = SNAP_COOLDOWN;
		}

		switch (shrimp.attackState()) {
			case Shrimp.AIMING -> {
				if (!sees) {
					// Lost the shot: lower the gun and go after it.
					shrimp.setAttackState(Shrimp.IDLE);
				} else if (--stateTicks <= 0) {
					shrimp.setAttackState(Shrimp.FIRING);
					shotsLeft = Shrimp.ROUNDS;
					stateTicks = 0;
				}
			}
			case Shrimp.FIRING -> {
				if (--stateTicks <= 0) {
					fire(level, target);
					stateTicks = SHOT_INTERVAL;
					if (--shotsLeft <= 0) {
						shrimp.setAttackState(Shrimp.RELOADING);
						stateTicks = Shrimp.RELOAD_TICKS;
					}
				}
			}
			case Shrimp.RELOADING -> reload();
			default -> {
				if (volleyCooldown == 0 && sees && distance < SHOOT_RANGE) {
					shrimp.setAttackState(Shrimp.AIMING);
					stateTicks = AIM_TICKS;
					shrimp.playSound(Shrimps.COCK, 1.0f, 0.9f + shrimp.getRandom().nextFloat() * 0.2f);
				}
			}
		}
		move(target, distance, sees);
	}

	/** Rounds thumbed in one by one through the gate, then the slide racked. */
	private void reload() {
		int elapsed = Shrimp.RELOAD_TICKS - stateTicks;
		// The client's animation pushes a round in at the same moments (see ShrimpModel).
		if (elapsed > 0 && elapsed % 12 == 6 && elapsed < 12 * Shrimp.ROUNDS) {
			shrimp.playSound(Shrimps.RELOAD_ROUND, 0.8f, 1.0f + 0.08f * (elapsed / 12));
		}
		if (elapsed == Shrimp.RELOAD_TICKS - 12) {
			shrimp.playSound(Shrimps.RELOAD_RACK, 1.0f, 1.0f);
		}
		if (--stateTicks <= 0) {
			shrimp.setAttackState(Shrimp.IDLE);
			volleyCooldown = VOLLEY_COOLDOWN + shrimp.getRandom().nextInt(20);
		}
	}

	/**
	 * Keeps its distance: closes in when far or out of sight, backs off when near (unless it's busy reloading, when it only
	 * shuffles), and while it's comfortable stands its ground, side-stepping on land.
	 */
	private void move(LivingEntity target, double distance, boolean sees) {
		boolean reloading = shrimp.attackState() == Shrimp.RELOADING;
		boolean shooting = shrimp.attackState() == Shrimp.AIMING || shrimp.attackState() == Shrimp.FIRING;
		if (!sees || distance > FAR) {
			shrimp.getMoveControl().strafe(0.0f, 0.0f);
			if (repathTicks == 0) {
				shrimp.getNavigation().moveTo(target, reloading ? 0.6 : 1.1);
				repathTicks = 10;
			}
			return;
		}
		if (distance < NEAR && !reloading && repathTicks == 0) {
			Vec3 away = DefaultRandomPos.getPosAway(shrimp, 8, 4, target.position());
			if (away != null) {
				shrimp.getNavigation().moveTo(away.x, away.y, away.z, 1.2);
				repathTicks = 15;
				return;
			}
		}
		if (shrimp.getNavigation().isDone() || shooting) {
			shrimp.getNavigation().stop();
			if (shrimp.isInWater()) {
				return;
			}
			if (--strafeTicks <= 0) {
				strafe = shrimp.getRandom().nextInt(3) - 1;
				strafeTicks = 20 + shrimp.getRandom().nextInt(30);
			}
			shrimp.getMoveControl().strafe(0.0f, strafe * (reloading ? 0.2f : 0.45f));
		}
	}

	private boolean inReach(LivingEntity target) {
		double reach = SNAP_REACH + target.getBbWidth() / 2.0;
		Vec3 to = target.position().subtract(shrimp.position());
		return to.horizontalDistanceSqr() < reach * reach && to.y > -1.5 && to.y < shrimp.getBbHeight();
	}

	/** One round, hitscan from the muzzle at the target's chest, straying up to {@link #SPREAD} degrees. */
	private void fire(ServerLevel level, LivingEntity target) {
		Vec3 muzzle = shrimp.muzzle();
		Vec3 aim = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0).subtract(muzzle);
		float yaw = (float) Math.toDegrees(Math.atan2(-aim.x, aim.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(aim.y, aim.horizontalDistance()));
		float radius = SPREAD * Mth.sqrt(shrimp.getRandom().nextFloat());
		float angle = shrimp.getRandom().nextFloat() * Mth.TWO_PI;
		Vec3 look = Vec3.directionFromRotation(pitch + radius * Mth.sin(angle), yaw + radius * Mth.cos(angle));
		Vec3 end = muzzle.add(look.scale(RANGE));

		HitResult blockHit = level.clip(new ClipContext(muzzle, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shrimp));
		if (blockHit.getType() != HitResult.Type.MISS) {
			end = blockHit.getLocation();
		}
		AABB sweep = shrimp.getBoundingBox().expandTowards(end.subtract(muzzle)).inflate(1.0);
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(shrimp, muzzle, end, sweep,
				entity -> !entity.isSpectator() && entity.isPickable() && !(entity instanceof Shrimp), muzzle.distanceToSqr(end));
		if (entityHit != null) {
			end = entityHit.getLocation();
			DamageSource source = new DamageSource(
					level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ModDamageTypes.BULLET), shrimp);
			entityHit.getEntity().hurtServer(level, source, BULLET_DAMAGE);
			level.sendParticles(ParticleTypes.CRIT, end.x, end.y, end.z, 6, 0.1, 0.1, 0.1, 0.2);
		}
		level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 3, 0.03, 0.03, 0.03, 0.01);
		level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 1, 0.0, 0.0, 0.0, 0.0);
		GunItem.drawTracer(level, muzzle, end, false);
		level.playSound(null, muzzle.x, muzzle.y, muzzle.z, Shrimps.PISTOL_SHOT, SoundSource.HOSTILE, 1.6f,
				0.95f + shrimp.getRandom().nextFloat() * 0.1f);
		shrimp.countShot();
	}
}
