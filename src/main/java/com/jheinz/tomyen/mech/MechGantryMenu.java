package com.jheinz.tomyen.mech;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A Mech Gantry's slots over the player's inventory, laid out as the walker seen from the front: the shoulder
 * part on top, the torso with an arm either side and each arm's hardpoint outside it, the legs below, and the
 * power plant and its core off to one side. Everything the screen shows comes from the slots themselves.
 */
public class MechGantryMenu extends AbstractContainerMenu {
	/** Slot positions in {@link MechLoadout} order. Facing the walker, its left arm is on the viewer's right. */
	public static final int[][] SLOT_POSITIONS = {{62, 40}, {62, 68}, {90, 40}, {34, 40}, {148, 22}, {148, 50}, {116, 56}, {8, 56}, {62, 14}};
	public static final int INVENTORY_X = 8;
	public static final int INVENTORY_Y = 140;
	public static final int HEIGHT = INVENTORY_Y + 82;

	private static final int INVENTORY_START = MechLoadout.SIZE;
	private static final int HOTBAR_START = INVENTORY_START + 27;
	private static final int INVENTORY_END = HOTBAR_START + 9;

	private final Container gantry;

	public MechGantryMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, new SimpleContainer(MechLoadout.SIZE));
	}

	public MechGantryMenu(int containerId, Inventory inventory, Container gantry) {
		super(Mechs.GANTRY_MENU, containerId);
		checkContainerSize(gantry, MechLoadout.SIZE);
		this.gantry = gantry;
		for (int i = 0; i < MechLoadout.SIZE; i++) {
			addSlot(new PartSlot(gantry, i, SLOT_POSITIONS[i][0], SLOT_POSITIONS[i][1]));
		}
		addStandardInventorySlots(inventory, INVENTORY_X, INVENTORY_Y);
	}

	/** The fitted parts, as the client last saw them. */
	public List<ItemStack> parts() {
		List<ItemStack> parts = new ArrayList<>();
		for (int i = 0; i < MechLoadout.SIZE; i++) {
			parts.add(gantry.getItem(i));
		}
		return parts;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		if (index < INVENTORY_START) {
			if (!moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, true)) {
				return ItemStack.EMPTY;
			}
		} else {
			boolean moved = false;
			for (int i = 0; i < MechLoadout.SIZE && !moved; i++) {
				moved = MechLoadout.fits(i, stack) && !slots.get(i).hasItem() && moveItemStackTo(stack, i, i + 1, false);
			}
			if (!moved) {
				boolean fromInventory = index < HOTBAR_START;
				if (!moveItemStackTo(stack, fromInventory ? HOTBAR_START : INVENTORY_START, fromInventory ? INVENTORY_END : HOTBAR_START, false)) {
					return ItemStack.EMPTY;
				}
			}
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		if (stack.getCount() == original.getCount()) {
			return ItemStack.EMPTY;
		}
		slot.onTake(player, stack);
		return original;
	}

	@Override
	public boolean stillValid(Player player) {
		return gantry.stillValid(player);
	}

	/** Takes one of the part it's for. */
	private static class PartSlot extends Slot {
		PartSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return MechLoadout.fits(getContainerSlot(), stack);
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}
	}
}
