package com.jheinz.tomyen.boar;

import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

/**
 * An adult boar goes for any player who comes too close to a piglet of the group. Sneaking doesn't help: it's the
 * piglets' squeals that give you away.
 */
class ProtectPigletsGoal extends NearestAttackableTargetGoal<Player> {
	/** A player this close to a piglet is a threat... */
	private static final double TOO_CLOSE = 4.0;
	/** ...to an adult whose piglet that is, within this far. */
	private static final double GUARD_RANGE = 12.0;

	ProtectPigletsGoal(Boar boar) {
		super(boar, Player.class, 10, true, false, (target, level) -> !boar.isBaby()
				&& !level.getEntitiesOfClass(Boar.class, target.getBoundingBox().inflate(TOO_CLOSE),
						piglet -> piglet.isBaby() && piglet.distanceToSqr(boar) < GUARD_RANGE * GUARD_RANGE).isEmpty());
	}

	@Override
	public boolean canUse() {
		return !mob.isBaby() && super.canUse();
	}

	@Override
	protected double getFollowDistance() {
		return GUARD_RANGE + TOO_CLOSE;
	}
}
