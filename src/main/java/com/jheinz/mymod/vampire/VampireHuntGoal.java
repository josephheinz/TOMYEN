package com.jheinz.mymod.vampire;

import java.util.EnumSet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.herbalism.HerbalWards;
import com.jheinz.mymod.holy.CrucifixItem;

/**
 * How a vampire hunts. While its prey is looking at it, it slips round to the side, out of view. Once the
 * prey looks away it crouches and creeps in behind them, then lunges and bites. After a bite it backs off
 * into the dark before circling in again.
 */
class VampireHuntGoal extends Goal {
	/** The prey counts as looking at it within about 50 degrees of where they face. */
	private static final double WATCHED_COS = 0.64;
	private static final double STRIKE_RANGE = 3.5;
	/** Close enough to bite even while being watched. */
	private static final double POINT_BLANK = 2.0;
	private static final double CREEP_SPEED = 1.0;
	private static final double FLANK_SPEED = 1.15;
	private static final double LUNGE_SPEED = 1.6;
	private static final double RETREAT_SPEED = 1.3;
	/** How far behind the prey it aims for while creeping in. */
	private static final double BEHIND_DISTANCE = 1.5;
	private static final int ATTACK_COOLDOWN = 20;
	/** How far out it circles someone holding a crucifix, just outside the ward. */
	private static final double WARD_CIRCLE = CrucifixItem.WARD_RADIUS + 1.5;

	private final Vampire vampire;
	private int attackCooldown;
	private int retreatTicks;
	private int repathTicks;
	/** Which way it circles round when flanking: 1 or -1. */
	private int flankSide = 1;

	VampireHuntGoal(Vampire vampire) {
		this.vampire = vampire;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = vampire.getTarget();
		return target != null && target.isAlive() && vampire.canAttack(target);
	}

	@Override
	public boolean canContinueToUse() {
		LivingEntity target = vampire.getTarget();
		if (target == null || !target.isAlive() || !vampire.canAttack(target)) {
			return false;
		}
		return !(target instanceof Player player) || !player.isCreative() && !player.isSpectator();
	}

	@Override
	public void start() {
		repathTicks = 0;
		flankSide = vampire.getRandom().nextBoolean() ? 1 : -1;
	}

	@Override
	public void stop() {
		vampire.setSwooping(false);
		vampire.setStalking(false);
		vampire.setAggressive(false);
		vampire.getNavigation().stop();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		LivingEntity target = vampire.getTarget();
		if (target == null) {
			return;
		}
		attackCooldown = Math.max(0, attackCooldown - 1);
		repathTicks = Math.max(0, repathTicks - 1);
		vampire.getLookControl().setLookAt(target, 30.0f, 30.0f);

		if (retreatTicks > 0) {
			retreatTicks--;
			vampire.setSwooping(false);
			vampire.setStalking(false);
			vampire.setAggressive(false);
			if (repathTicks == 0) {
				Vec3 away = DefaultRandomPos.getPosAway(vampire, 12, 4, target.position());
				if (away != null) {
					vampire.getNavigation().moveTo(away.x, away.y, away.z, RETREAT_SPEED);
				}
				repathTicks = 20;
			}
			return;
		}

		if (CrucifixItem.isHeldBy(target) || HerbalWards.repelsVampires(target)) {
			// Held at bay: prowl round just outside the ward, waiting for the crucifix to be put away (or the garlic to wear off).
			vampire.setStalking(false);
			vampire.setAggressive(false);
			vampire.setSwooping(false);
			if (repathTicks == 0) {
				Vec3 offset = vampire.position().subtract(target.position());
				float angle = (float) Math.atan2(offset.z, offset.x) + flankSide * 35.0f * Mth.DEG_TO_RAD;
				Vec3 point = target.position().add(Mth.cos(angle) * WARD_CIRCLE, 0.0, Mth.sin(angle) * WARD_CIRCLE);
				vampire.getNavigation().moveTo(point.x, point.y, point.z, FLANK_SPEED);
				repathTicks = 10;
			}
			return;
		}

		if (attackCooldown == 0 && vampire.isWithinMeleeAttackRange(target) && vampire.getSensing().hasLineOfSight(target)) {
			vampire.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			attackCooldown = ATTACK_COOLDOWN;
			if (vampire.doHurtTarget((ServerLevel) vampire.level(), target)) {
				// Hit and run: melt back into the dark, then come round again from somewhere else.
				retreatTicks = 30 + vampire.getRandom().nextInt(30);
				flankSide = -flankSide;
				repathTicks = 0;
			}
			return;
		}

		double distanceSq = vampire.distanceToSqr(target);
		boolean watched = isWatchedBy(target);
		vampire.setSwooping(false);
		if (distanceSq < STRIKE_RANGE * STRIKE_RANGE && (!watched || distanceSq < POINT_BLANK * POINT_BLANK)) {
			vampire.setStalking(false);
			vampire.setAggressive(true);
			// Floating, it dives down to bite.
			vampire.setSwooping(true);
			if (repathTicks == 0) {
				vampire.getNavigation().moveTo(target, LUNGE_SPEED);
				repathTicks = 4;
			}
		} else if (watched) {
			vampire.setStalking(false);
			vampire.setAggressive(false);
			if (repathTicks == 0) {
				Vec3 flank = flankPoint(target);
				vampire.getNavigation().moveTo(flank.x, flank.y, flank.z, FLANK_SPEED);
				repathTicks = 10;
			}
		} else {
			vampire.setStalking(true);
			vampire.setAggressive(false);
			if (repathTicks == 0) {
				Vec3 behind = target.position().subtract(horizontalLook(target).scale(BEHIND_DISTANCE));
				vampire.getNavigation().moveTo(behind.x, behind.y, behind.z, CREEP_SPEED);
				repathTicks = 5;
			}
		}
	}

	/** Whether the target is a player looking its way with nothing in between. Mobs never notice it coming. */
	private boolean isWatchedBy(LivingEntity target) {
		if (!(target instanceof Player)) {
			return false;
		}
		Vec3 toVampire = vampire.getEyePosition().subtract(target.getEyePosition()).normalize();
		return target.getViewVector(1.0f).dot(toVampire) > WATCHED_COS && target.hasLineOfSight(vampire);
	}

	/** A point out to the side of the target's view, at roughly the current distance. */
	private Vec3 flankPoint(LivingEntity target) {
		Vec3 offset = vampire.position().subtract(target.position());
		double distance = Mth.clamp(Math.sqrt(offset.x * offset.x + offset.z * offset.z), 5.0, 10.0);
		float angle = (float) Math.atan2(offset.z, offset.x) + flankSide * 70.0f * Mth.DEG_TO_RAD;
		return target.position().add(Mth.cos(angle) * distance, 0.0, Mth.sin(angle) * distance);
	}

	private static Vec3 horizontalLook(LivingEntity entity) {
		Vec3 look = entity.getViewVector(1.0f);
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		return flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
	}
}
