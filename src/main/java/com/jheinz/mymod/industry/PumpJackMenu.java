package com.jheinz.mymod.industry;

import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** An empty-canister slot, a full-canister slot, and the jack's status, over the player's inventory. */
public class PumpJackMenu extends AbstractContainerMenu {
	// Slot positions, shared with the screen's background texture.
	public static final int INPUT_X = 44;
	public static final int INPUT_Y = 35;
	public static final int OUTPUT_X = 116;
	public static final int OUTPUT_Y = 35;

	private static final int INVENTORY_START = 2;
	private static final int HOTBAR_START = INVENTORY_START + 27;
	private static final int INVENTORY_END = HOTBAR_START + 9;

	private final Container container;
	private final ContainerData data;

	public PumpJackMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, new SimpleContainer(2), new SimpleContainerData(PumpJackBlockEntity.DATA_COUNT));
	}

	public PumpJackMenu(int containerId, Inventory inventory, Container container, ContainerData data) {
		super(Industry.PUMP_JACK_MENU, containerId);
		checkContainerSize(container, 2);
		checkContainerDataCount(data, PumpJackBlockEntity.DATA_COUNT);
		this.container = container;
		this.data = data;
		addSlot(new Slot(container, PumpJackBlockEntity.INPUT_SLOT, INPUT_X, INPUT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.is(Industry.EMPTY_CANISTER);
			}
		});
		addSlot(new Slot(container, PumpJackBlockEntity.OUTPUT_SLOT, OUTPUT_X, OUTPUT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}
		});
		addStandardInventorySlots(inventory, 8, 84);
		addDataSlots(data);
	}

	/** How far along the current canister is, 0 to 1. */
	public float progress() {
		return Mth.clamp((float) data.get(PumpJackBlockEntity.DATA_PROGRESS) / PumpJackBlockEntity.PUMP_TICKS, 0.0f, 1.0f);
	}

	/** One of PumpJackBlockEntity's STATUS_ codes. */
	public int status() {
		return data.get(PumpJackBlockEntity.DATA_STATUS);
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
			slot.onQuickCraft(stack, original);
		} else if (!(stack.is(Industry.EMPTY_CANISTER) && moveItemStackTo(stack, 0, 1, false))) {
			boolean fromInventory = index < HOTBAR_START;
			if (!moveItemStackTo(stack, fromInventory ? HOTBAR_START : INVENTORY_START, fromInventory ? INVENTORY_END : HOTBAR_START, false)) {
				return ItemStack.EMPTY;
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
		return container.stillValid(player);
	}
}
