package com.jheinz.tomyen.banshee;

import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;

/**
 * Walking paths, followed while floating: paths still run along the ground (the path start is found by looking down
 * from her), but she follows them half a block up without ever touching it, as the vampire follows its HoverNavigation.
 */
class BansheeNavigation extends GroundPathNavigation {
	BansheeNavigation(Banshee banshee, Level level) {
		super(banshee, level);
		setCanFloat(true);
		setCanOpenDoors(false);
	}

	// She's never on the ground, which is what walking navigation waits for before it moves on.
	@Override
	protected boolean canUpdatePath() {
		return true;
	}

	// Path points sit on the ground; count one as reached from a little above it.
	@Override
	public float getMaxVerticalDistanceToWaypoint() {
		return 2.5f;
	}
}
