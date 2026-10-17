package com.jheinz.tomyen.fusion;

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

/** The two inputs either side of the containment ring, the fuel under it and the output, over the player's inventory. */
public class FusionReactorMenu extends AbstractContainerMenu {
	// Slot positions, shared with the screen's background texture (tools/gen_fusion.py).
	public static final int FIRST_X = 26;
	public static final int FIRST_Y = 17;
	public static final int SECOND_X = 26;
	public static final int SECOND_Y = 53;
	public static final int FUEL_X = 80;
	public static final int FUEL_Y = 60;
	public static final int OUTPUT_X = 134;
	public static final int OUTPUT_Y = 35;

	private static final int INVENTORY_START = FusionReactorBlockEntity.SIZE;
	private static final int HOTBAR_START = INVENTORY_START + 27;
	private static final int INVENTORY_END = HOTBAR_START + 9;

	private final Container container;
	private final ContainerData data;

	public FusionReactorMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, new SimpleContainer(FusionReactorBlockEntity.SIZE),
				new SimpleContainerData(FusionReactorBlockEntity.DATA_COUNT));
	}

	public FusionReactorMenu(int containerId, Inventory inventory, Container container, ContainerData data) {
		super(Fusions.REACTOR_MENU, containerId);
		checkContainerSize(container, FusionReactorBlockEntity.SIZE);
		checkContainerDataCount(data, FusionReactorBlockEntity.DATA_COUNT);
		this.container = container;
		this.data = data;
		addSlot(new InputSlot(container, FusionReactorBlockEntity.FIRST_SLOT, FIRST_X, FIRST_Y));
		addSlot(new InputSlot(container, FusionReactorBlockEntity.SECOND_SLOT, SECOND_X, SECOND_Y));
		addSlot(new Slot(container, FusionReactorBlockEntity.FUEL_SLOT, FUEL_X, FUEL_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return FusionReactorBlockEntity.fuelTicks(stack) > 0;
			}
		});
		addSlot(new Slot(container, FusionReactorBlockEntity.OUTPUT_SLOT, OUTPUT_X, OUTPUT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}
		});
		addStandardInventorySlots(inventory, 8, 84);
		addDataSlots(data);
	}

	private static class InputSlot extends Slot {
		InputSlot(Container container, int slot, int x, int y) {
			super(container, slot, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return FusionReactorBlockEntity.fuelTicks(stack) == 0;
		}
	}

	/** How much of the current fuel item is left, 0 to 1. */
	public float burn() {
		int total = data.get(FusionReactorBlockEntity.DATA_BURN_TOTAL);
		return total == 0 ? 0.0f : Mth.clamp((float) data.get(FusionReactorBlockEntity.DATA_BURN) / total, 0.0f, 1.0f);
	}

	/** How far along the current fusion is, 0 to 1. */
	public float progress() {
		int total = Math.max(1, data.get(FusionReactorBlockEntity.DATA_PROGRESS_TOTAL));
		return Mth.clamp((float) data.get(FusionReactorBlockEntity.DATA_PROGRESS) / total, 0.0f, 1.0f);
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
		} else {
			boolean moved = FusionReactorBlockEntity.fuelTicks(stack) > 0
					? moveItemStackTo(stack, FusionReactorBlockEntity.FUEL_SLOT, FusionReactorBlockEntity.FUEL_SLOT + 1, false)
					: moveItemStackTo(stack, FusionReactorBlockEntity.FIRST_SLOT, FusionReactorBlockEntity.SECOND_SLOT + 1, false);
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
		return container.stillValid(player);
	}
}
