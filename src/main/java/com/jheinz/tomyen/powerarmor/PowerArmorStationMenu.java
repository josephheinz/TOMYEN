package com.jheinz.tomyen.powerarmor;

import java.util.List;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The rack of a Power Armor Station over the player's inventory: the six piece slots laid out as a body
 * (helmet on top, arms either side of the torso as seen from the front, legs below) and the fusion core
 * under them. Everything the screen shows (marks, conditions, charge) comes from the slots themselves.
 */
public class PowerArmorStationMenu extends AbstractContainerMenu {
	// Slot positions, shared with the screen, in suit-slot order: helmet, torso, left arm, right arm, left leg,
	// right leg, core. Facing the suit, its right arm is on the viewer's left.
	public static final int[][] SLOT_POSITIONS = {{44, 22}, {44, 46}, {68, 46}, {20, 46}, {56, 72}, {32, 72}, {44, 98}};
	public static final int INVENTORY_X = 8;
	public static final int INVENTORY_Y = 140;
	public static final int HEIGHT = INVENTORY_Y + 82;

	private static final int INVENTORY_START = PowerArmorSuit.SIZE;
	private static final int HOTBAR_START = INVENTORY_START + 27;
	private static final int INVENTORY_END = HOTBAR_START + 9;

	private final Container rack;

	public PowerArmorStationMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, new SimpleContainer(PowerArmorSuit.SIZE));
	}

	public PowerArmorStationMenu(int containerId, Inventory inventory, Container rack) {
		super(PowerArmors.STATION_MENU, containerId);
		checkContainerSize(rack, PowerArmorSuit.SIZE);
		this.rack = rack;
		for (int i = 0; i < PowerArmorSuit.SIZE; i++) {
			addSlot(new RackSlot(rack, i, SLOT_POSITIONS[i][0], SLOT_POSITIONS[i][1]));
		}
		addStandardInventorySlots(inventory, INVENTORY_X, INVENTORY_Y);
	}

	/** The rack as a suit, as the client last saw it. */
	public PowerArmorSuit suit() {
		ItemStack[] stacks = new ItemStack[PowerArmorSuit.SIZE];
		for (int i = 0; i < stacks.length; i++) {
			stacks[i] = rack.getItem(i);
		}
		return PowerArmorSuit.of(List.of(stacks));
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
			for (int i = 0; i < PowerArmorSuit.SIZE && !moved; i++) {
				moved = PowerArmorStationBlockEntity.fits(i, stack) && moveItemStackTo(stack, i, i + 1, false);
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
		return rack.stillValid(player);
	}

	/** Takes one of the piece (or core) it's for. */
	private static class RackSlot extends Slot {
		RackSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return PowerArmorStationBlockEntity.fits(getContainerSlot(), stack);
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}
	}
}
