package com.jheinz.mymod.skullcrane;

import java.util.EnumSet;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * With nothing to hunt, a pack drifts about together. Its {@link SkullcraneLeader} leads if there's one in range,
 * otherwise the eldest skullcrane (the one with the lowest entity id), wandering off a short way at a time; the rest keep close behind it, each picking its own spot a few blocks
 * from the leader at its own cruising height. One that's strayed further than {@value #STRAY} blocks hurries back.
 */
class SkullcranePackGoal extends Goal {
	private static final double WANDER = 12.0;
	private static final double FOLLOW = 3.5;
	private static final double STRAY = 8.0;

	private final Skullcrane skull;

	SkullcranePackGoal(Skullcrane skull) {
		this.skull = skull;
		setFlags(EnumSet.of(Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		return skull.getTarget() == null && !skull.isClamped() && !skull.isPanicking() && skull.getNavigation().isDone()
				&& skull.getRandom().nextInt(20) == 0;
	}

	@Override
	public boolean canContinueToUse() {
		return skull.getTarget() == null && !skull.isPanicking() && skull.getNavigation().isInProgress();
	}

	@Override
	public void start() {
		Skullcrane leader = skull.packLeader();
		if (leader == null) {
			leader = skull;
			for (Skullcrane mate : skull.packmates()) {
				if (mate.getId() < leader.getId()) {
					leader = mate;
				}
			}
		}
		var random = skull.getRandom();
		float angle = random.nextFloat() * Mth.TWO_PI;
		double x;
		double z;
		double speed = 0.8;
		if (leader == skull) {
			double distance = WANDER * (0.4 + 0.6 * random.nextDouble());
			x = skull.getX() + Mth.cos(angle) * distance;
			z = skull.getZ() + Mth.sin(angle) * distance;
		} else {
			double distance = FOLLOW * (0.5 + random.nextDouble());
			x = leader.getX() + Mth.cos(angle) * distance;
			z = leader.getZ() + Mth.sin(angle) * distance;
			if (skull.distanceTo(leader) > STRAY) {
				speed = 1.3;
			}
		}
		skull.getNavigation().moveTo(x, skull.cruiseY(x, z), z, speed);
	}
}
