package com.jheinz.mymod.tank;

import java.util.EnumSet;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * How one of the bank's tanks hunts on its own (only a collector tank ever has a target): it closes to within
 * {@link #ENGAGE_RANGE} blocks with a clear view of its target, stops, and fires whenever the gun has come to
 * bear and reloaded. It follows a path where it can find one; where it can't (a big hull finds few), it drives
 * straight at the target, crushing what it can, and backs off and turns if it gets stuck.
 */
public class TankAttackGoal extends Goal {
	private static final double ENGAGE_RANGE = 22.0;
	private static final double MAX_RANGE = 48.0;
	private static final int REPATH_TICKS = 20;
	private static final int STUCK_CHECK_TICKS = 40;
	private static final int REVERSE_TICKS = 25;

	private final Tank tank;
	private int repath;
	private boolean direct;
	private int stuckCheck;
	private Vec3 lastCheck = Vec3.ZERO;

	public TankAttackGoal(Tank tank) {
		this.tank = tank;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = tank.getTarget();
		return target != null && target.isAlive() && tank.crew() == null;
	}

	@Override
	public void start() {
		repath = 0;
		stuckCheck = STUCK_CHECK_TICKS;
		lastCheck = tank.position();
	}

	@Override
	public void stop() {
		tank.aimAt(null);
		tank.getNavigation().stop();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		LivingEntity target = tank.getTarget();
		if (target == null) {
			return;
		}
		double distance = tank.distanceTo(target);
		boolean sees = tank.getSensing().hasLineOfSight(target);
		tank.aimAt(target.position().add(0.0, target.getBbHeight() * 0.4, 0.0));
		TankMoveControl move = (TankMoveControl) tank.getMoveControl();

		boolean closing = distance > ENGAGE_RANGE || !sees;
		if (closing && !move.reversing()) {
			if (--repath <= 0) {
				repath = REPATH_TICKS;
				direct = !tank.getNavigation().moveTo(target, 1.0);
			}
			if (direct || tank.getNavigation().isDone()) {
				move.setWantedPosition(target.getX(), target.getY(), target.getZ(), 1.0);
			}
			// Not getting anywhere: back up and turn away a little, then try again.
			if (--stuckCheck <= 0) {
				stuckCheck = STUCK_CHECK_TICKS;
				if (tank.position().distanceToSqr(lastCheck) < 0.25) {
					tank.getNavigation().stop();
					move.reverse(REVERSE_TICKS);
					repath = REVERSE_TICKS;
				}
				lastCheck = tank.position();
			}
		} else if (!closing) {
			tank.getNavigation().stop();
			stuckCheck = STUCK_CHECK_TICKS;
			lastCheck = tank.position();
		}

		if (sees && distance < MAX_RANGE && tank.aimSettled() && tank.gunnery().aiReady()) {
			tank.gunnery().aiFire(target, tank.gunDirection(1.0f));
		}
	}

	/**
	 * Drives a tank like one: it pivots towards where it's going (no faster than a crew could) and only moves off
	 * once it's facing roughly that way, slowing for the turn; and it can back up for a while to get unstuck.
	 */
	public static class TankMoveControl extends MoveControl<Tank> {
		private static final float TURN_SPEED = 2.5f;
		private static final float DRIVE_WITHIN = 60.0f;
		private int reverse;

		public TankMoveControl(Tank tank) {
			super(tank);
		}

		void reverse(int ticks) {
			reverse = ticks;
			operation = Operation.WAIT;
		}

		boolean reversing() {
			return reverse > 0;
		}

		@Override
		public void tick() {
			float top = (float) mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * mob.speedFactor();
			if (reverse > 0) {
				reverse--;
				turn(TURN_SPEED * 0.6f);
				mob.setSpeed(top * 0.6f);
				mob.setZza(-1.0f);
				return;
			}
			if (operation != Operation.MOVE_TO) {
				mob.setSpeed(0.0f);
				mob.setZza(0.0f);
				return;
			}
			operation = Operation.WAIT;
			double dx = wantedX - mob.getX();
			double dz = wantedZ - mob.getZ();
			if (dx * dx + dz * dz < 0.5) {
				mob.setSpeed(0.0f);
				mob.setZza(0.0f);
				return;
			}
			float want = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
			float off = Mth.wrapDegrees(want - mob.getYRot());
			turn(Mth.clamp(off, -TURN_SPEED, TURN_SPEED) * (0.4f + 0.6f * mob.partCondition(TankPart.TRACKS)));
			float speed = Math.abs(off) > DRIVE_WITHIN ? 0.0f : (float) (speedModifier * top * Mth.cos(off * Mth.DEG_TO_RAD));
			mob.setSpeed(speed);
			// Mob.setSpeed also sets the forward input to the speed; drive at full input instead, as a player would.
			mob.setZza(speed > 0.0f ? 1.0f : 0.0f);
		}

		private void turn(float degrees) {
			float yaw = mob.getYRot() + degrees;
			mob.setYRot(yaw);
			mob.setYBodyRot(yaw);
		}
	}
}
