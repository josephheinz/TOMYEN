package com.jheinz.tomyen.mech;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;

import org.jspecify.annotations.Nullable;

/**
 * A walker's Cargo Pod, opened from the cockpit (the cargo key) or from outside (sneak-use): a three-row chest.
 * Closes if the walker is destroyed, loses its pod, or the player walks off.
 */
public class MechCargoMenu extends ChestMenu {
	private static final double REACH = 8.0;

	private final @Nullable Mech mech;

	/** Client side: the slots are filled in by the server. */
	public MechCargoMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, new SimpleContainer(27), null);
	}

	public MechCargoMenu(int containerId, Inventory inventory, Container cargo, @Nullable Mech mech) {
		super(Mechs.CARGO_MENU, containerId, inventory, cargo, 3);
		this.mech = mech;
	}

	@Override
	public boolean stillValid(Player player) {
		return mech == null || mech.isAlive() && mech.hasCargoPod() && (mech.hasPassenger(player) || player.distanceToSqr(mech) < REACH * REACH);
	}
}
