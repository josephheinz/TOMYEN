package com.jheinz.tomyen.tank;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

/**
 * A tank's ammo rack opened from outside (sneak-use) or from the gunner's seat: a two-row chest whose slots
 * take only what the rack holds (see {@link TankRack#accepts}). Closes if the tank is destroyed or the player
 * walks off.
 */
public class TankRackMenu extends ChestMenu {
	private static final double REACH = 8.0;

	private final @Nullable Tank tank;

	/** Client side: the slots are filled in by the server. */
	public TankRackMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, new TankRack(null), null);
	}

	public TankRackMenu(int containerId, Inventory inventory, Container rack, @Nullable Tank tank) {
		super(Tanks.RACK_MENU, containerId, inventory, rack, TankRack.SIZE / 9);
		this.tank = tank;
		// ChestMenu's own slots take anything: swap the rack's for ones that only take shells and gunpowder.
		for (int i = 0; i < TankRack.SIZE; i++) {
			Slot plain = slots.get(i);
			Slot filtered = new Slot(rack, plain.getContainerSlot(), plain.x, plain.y) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return TankRack.accepts(stack);
				}
			};
			filtered.index = i;
			slots.set(i, filtered);
		}
	}

	@Override
	public boolean stillValid(Player player) {
		return tank == null || tank.isAlive() && (tank.hasPassenger(player) || player.distanceToSqr(tank) < REACH * REACH);
	}
}
