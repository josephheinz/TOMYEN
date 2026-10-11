package com.jheinz.mymod.vampire;

import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;

/**
 * Walking paths, followed while floating a couple of blocks up: paths still run along the ground (the
 * path start is found by looking down from the vampire), but it can follow them without touching it.
 */
class HoverNavigation extends GroundPathNavigation {
	private final Vampire vampire;

	HoverNavigation(Vampire vampire, Level level) {
		super(vampire, level);
		this.vampire = vampire;
	}

	@Override
	protected boolean canUpdatePath() {
		return super.canUpdatePath() || vampire.isHovering();
	}

	// Path points sit on the ground; count one as reached from a couple of blocks above it.
	@Override
	public float getMaxVerticalDistanceToWaypoint() {
		return vampire.isHovering() ? 3.5f : super.getMaxVerticalDistanceToWaypoint();
	}
}
