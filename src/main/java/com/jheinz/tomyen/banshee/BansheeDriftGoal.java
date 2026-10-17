package com.jheinz.tomyen.banshee;

import java.util.EnumSet;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

/** With nobody to haunt, she drifts slowly about, a short way at a time, pausing a while between (as the Schnozzle does). */
class BansheeDriftGoal extends Goal {
	private static final int WANDER = 12;
	private static final double SPEED = 0.6;

	private final Banshee banshee;

	BansheeDriftGoal(Banshee banshee) {
		this.banshee = banshee;
		setFlags(EnumSet.of(Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		return banshee.getTarget() == null && banshee.getNavigation().isDone() && banshee.getRandom().nextInt(60) == 0;
	}

	@Override
	public boolean canContinueToUse() {
		return banshee.getTarget() == null && banshee.getNavigation().isInProgress();
	}

	@Override
	public void start() {
		Vec3 to = LandRandomPos.getPos(banshee, WANDER, 4);
		if (to != null) {
			banshee.getNavigation().moveTo(to.x, to.y, to.z, SPEED);
		}
	}
}
