package com.jheinz.tomyen.schnozzle;

import java.util.EnumSet;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;

/** With nobody to chase, it drifts slowly about at cruising height, a short way at a time. */
class SchnozzleDriftGoal extends Goal {
	private static final double WANDER = 14.0;

	private final Schnozzle schnozzle;

	SchnozzleDriftGoal(Schnozzle schnozzle) {
		this.schnozzle = schnozzle;
		setFlags(EnumSet.of(Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		return schnozzle.getTarget() == null && schnozzle.getNavigation().isDone() && schnozzle.getRandom().nextInt(50) == 0;
	}

	@Override
	public boolean canContinueToUse() {
		return schnozzle.getTarget() == null && schnozzle.getNavigation().isInProgress();
	}

	@Override
	public void start() {
		float angle = schnozzle.getRandom().nextFloat() * Mth.TWO_PI;
		double distance = WANDER * (0.4 + 0.6 * schnozzle.getRandom().nextDouble());
		double x = schnozzle.getX() + Mth.cos(angle) * distance;
		double z = schnozzle.getZ() + Mth.sin(angle) * distance;
		schnozzle.getNavigation().moveTo(x, schnozzle.cruiseY(x, z), z, 0.8);
	}
}
