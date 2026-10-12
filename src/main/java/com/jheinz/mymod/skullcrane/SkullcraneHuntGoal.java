package com.jheinz.mymod.skullcrane;

import java.util.EnumSet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * How a skullcrane hunts.
 * <ul>
 * <li>Prey it can't carry off (too big, shut in a tank, or it's only just let go and is regrouping) it harries:
 * circling {@value #ORBIT_RADIUS} blocks out over its head with a jittery drone flight, and now and then swooping in
 * to scratch it with its claws and darting back out.
 * <li>Prey it can carry, it grabs, but only with help. It hangs back circling until enough of the pack are in on the
 * hunt, close by and ready, to lift the prey between them ({@link SkullcraneGrip#holdersNeeded}); then every one of
 * them dives in at once and clamps on over its shoulders. Once anyone's clamped on, the rest of the pack piles in too,
 * even on the way up, to speed the lift.
 * <li>Clamped on but not yet lifting, it scratches away at its victim every {@value #CLING_SCRATCH} ticks, its jaw
 * chattering. Its grip does the rest.
 * </ul>
 */
class SkullcraneHuntGoal extends Goal {
	private static final double ORBIT_RADIUS = 4.5;
	/** How far over its prey's head it circles. */
	private static final double ORBIT_HEIGHT = 2.0;
	/** How close to the top of its prey's head it has to get to clamp on. */
	private static final double LATCH_RANGE = 1.3;
	/** How far off packmates still count as ready to join a grab. */
	private static final double GATHER_RANGE = 14.0;
	private static final double DIVE_SPEED = 0.55;
	private static final int SCRATCH_COOLDOWN = 30;
	private static final int CLING_SCRATCH = 25;
	private static final int SWOOP_TICKS = 40;

	private final Skullcrane skull;
	private int scratchCooldown;
	private int swoopTicks;
	private int repathTicks;
	private int chatterTicks;
	private float orbitAngle;
	/** Which way it circles: 1 or -1. */
	private int orbitDirection = 1;

	SkullcraneHuntGoal(Skullcrane skull) {
		this.skull = skull;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = skull.getTarget();
		return target != null && target.isAlive() && skull.canAttack(target);
	}

	@Override
	public boolean canContinueToUse() {
		return canUse() || skull.isClamped();
	}

	@Override
	public void start() {
		LivingEntity target = skull.getTarget();
		Vec3 offset = skull.position().subtract(target.position());
		orbitAngle = (float) Math.atan2(offset.z, offset.x);
		orbitDirection = skull.getRandom().nextBoolean() ? 1 : -1;
		scratchCooldown = 10 + skull.getRandom().nextInt(20);
		swoopTicks = 0;
		repathTicks = 0;
		skull.setAggressive(true);
		skull.playSound(Skullcranes.CHATTER, 1.0f, 1.0f + skull.getRandom().nextFloat() * 0.2f);
	}

	@Override
	public void stop() {
		skull.setAggressive(false);
		skull.getNavigation().stop();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		LivingEntity target = skull.getTarget();
		if (target == null) {
			return;
		}
		ServerLevel level = (ServerLevel) skull.level();
		scratchCooldown = Math.max(0, scratchCooldown - 1);
		repathTicks = Math.max(0, repathTicks - 1);
		if (skull.isClamped()) {
			clinging(level, target);
			return;
		}
		skull.getLookControl().setLookAt(target, 30.0f, 30.0f);

		if (skull.grabCooldown == 0 && SkullcraneGrip.canLift(target)) {
			SkullcraneGrip grip = SkullcraneGrip.on(target);
			if (grip != null ? grip.hasRoom() : packReady(target)) {
				grab(level, target);
				return;
			}
		}
		harry(level, target);
	}

	/** Whether enough of the pack (counting itself) are close by, after the same prey and ready, to lift it. */
	private boolean packReady(LivingEntity target) {
		int ready = 1;
		int needed = SkullcraneGrip.holdersNeeded(target);
		for (Skullcrane mate : skull.packmates()) {
			if (ready >= needed) {
				break;
			}
			if (mate.getTarget() == target && !mate.isClamped() && mate.grabCooldown == 0 && mate.distanceTo(target) < GATHER_RANGE) {
				ready++;
			}
		}
		return ready >= needed && skull.distanceTo(target) < GATHER_RANGE;
	}

	/** Dives at the top of the prey's head and clamps on when it gets there. */
	private void grab(ServerLevel level, LivingEntity target) {
		Vec3 point = target.position().add(0.0, target.getBbHeight() + 0.4, 0.0);
		if (skull.position().distanceTo(point) < LATCH_RANGE) {
			skull.getNavigation().stop();
			SkullcraneGrip.latch(level, skull, target);
			return;
		}
		flyAt(point, 1.6);
		chatter();
	}

	/** Circles over the prey, swooping in now and then to scratch it. */
	private void harry(ServerLevel level, LivingEntity target) {
		if (swoopTicks > 0) {
			swoopTicks--;
			flyAt(target.position().add(0.0, target.getBbHeight() * 0.7, 0.0), 1.5);
			chatter();
			if (scratchCooldown == 0 && skull.getBoundingBox().inflate(0.6).intersects(target.getBoundingBox())) {
				skull.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				skull.doHurtTarget(level, target);
				scratchCooldown = SCRATCH_COOLDOWN;
				swoopTicks = 0;
				// Darts back out.
				Vec3 away = skull.position().subtract(target.position()).horizontal();
				away = away.lengthSqr() < 1.0e-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
				skull.setDeltaMovement(away.scale(0.5).add(0.0, 0.35, 0.0));
				repathTicks = 8;
			}
			return;
		}
		if (scratchCooldown == 0 && skull.getRandom().nextInt(30) == 0 && skull.getSensing().hasLineOfSight(target)) {
			swoopTicks = SWOOP_TICKS;
			skull.getNavigation().stop();
			return;
		}
		if (repathTicks == 0) {
			if (skull.getRandom().nextInt(5) == 0) {
				orbitDirection = -orbitDirection;
			}
			orbitAngle += orbitDirection * (20.0f + skull.getRandom().nextFloat() * 25.0f) * Mth.DEG_TO_RAD;
			double radius = ORBIT_RADIUS * (0.8 + 0.4 * skull.getRandom().nextDouble());
			double x = target.getX() + Mth.cos(orbitAngle) * radius;
			double z = target.getZ() + Mth.sin(orbitAngle) * radius;
			double y = target.getY() + target.getBbHeight() + ORBIT_HEIGHT + skull.getRandom().nextDouble();
			if (!skull.getNavigation().moveTo(x, y, z, 1.2)) {
				skull.getMoveControl().setWantedPosition(x, y, z, 1.2);
			}
			repathTicks = 10;
		}
	}

	/** Clamped on: it scratches at its victim until the lift starts. */
	private void clinging(ServerLevel level, LivingEntity victim) {
		SkullcraneGrip grip = skull.grip();
		chatter();
		if (grip != null && !grip.isLifting() && scratchCooldown == 0) {
			skull.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			victim.hurtServer(level, skull.damageSources().mobAttack(skull), 1.0f);
			skull.playSound(Skullcranes.SCRATCH, 0.8f, 1.0f + skull.getRandom().nextFloat() * 0.3f);
			scratchCooldown = CLING_SCRATCH;
		}
	}

	/** Straight at a point while it can see it; round obstacles otherwise. */
	private void flyAt(Vec3 point, double speedModifier) {
		boolean clear = skull.level().clip(new ClipContext(skull.position(), point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, skull))
				.getType() == HitResult.Type.MISS;
		if (clear) {
			skull.getNavigation().stop();
			Vec3 to = point.subtract(skull.position());
			double distance = to.length();
			Vec3 wanted = to.scale(Math.min(DIVE_SPEED, distance * 0.4) / Math.max(distance, 1.0e-4));
			skull.setDeltaMovement(skull.getDeltaMovement().lerp(wanted, 0.3));
			float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0f;
			skull.setYRot(yaw);
			skull.yBodyRot = yaw;
		} else if (repathTicks == 0) {
			skull.getNavigation().moveTo(point.x, point.y, point.z, speedModifier);
			repathTicks = 5;
		}
	}

	private void chatter() {
		if (--chatterTicks <= 0) {
			skull.playSound(Skullcranes.CHATTER, 0.7f, 0.9f + skull.getRandom().nextFloat() * 0.4f);
			chatterTicks = 12 + skull.getRandom().nextInt(10);
		}
	}
}
