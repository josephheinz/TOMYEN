package com.jheinz.tomyen.industry;

import org.jspecify.annotations.Nullable;

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

/** Three inputs, the fuel slot, the product and two byproducts, over the player's inventory. */
public class RefineryMenu extends AbstractContainerMenu {
	// Slot positions, shared with the screen's background texture (tools/gen_industry.py).
	public static final int INPUT_X = 26;
	public static final int INPUT_Y = 17;
	public static final int FUEL_X = 44;
	public static final int FUEL_Y = 53;
	public static final int OUTPUT_X = 116;
	public static final int OUTPUT_Y = 22;
	public static final int BYPRODUCT_X = 116;
	public static final int BYPRODUCT_Y = 48;

	private static final int INVENTORY_START = RefineryBlockEntity.SIZE;
	private static final int HOTBAR_START = INVENTORY_START + 27;
	private static final int INVENTORY_END = HOTBAR_START + 9;

	private final Container container;
	private final ContainerData data;

	public RefineryMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, new SimpleContainer(RefineryBlockEntity.SIZE), new SimpleContainerData(RefineryBlockEntity.DATA_COUNT));
	}

	public RefineryMenu(int containerId, Inventory inventory, Container container, ContainerData data) {
		super(Industry.REFINERY_MENU, containerId);
		checkContainerSize(container, RefineryBlockEntity.SIZE);
		checkContainerDataCount(data, RefineryBlockEntity.DATA_COUNT);
		this.container = container;
		this.data = data;
		for (int i = 0; i < RefineryBlockEntity.INPUT_COUNT; i++) {
			addSlot(new Slot(container, i, INPUT_X + i * 18, INPUT_Y));
		}
		addSlot(new Slot(container, RefineryBlockEntity.FUEL_SLOT, FUEL_X, FUEL_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return RefineryBlockEntity.burnTicks(stack) > 0;
			}
		});
		addSlot(new OutputSlot(container, RefineryBlockEntity.OUTPUT_SLOT, OUTPUT_X, OUTPUT_Y));
		addSlot(new OutputSlot(container, RefineryBlockEntity.BYPRODUCT_SLOT, BYPRODUCT_X, BYPRODUCT_Y));
		addSlot(new OutputSlot(container, RefineryBlockEntity.BYPRODUCT_SLOT + 1, BYPRODUCT_X + 18, BYPRODUCT_Y));
		addStandardInventorySlots(inventory, 8, 84);
		addDataSlots(data);
	}

	private static class OutputSlot extends Slot {
		OutputSlot(Container container, int slot, int x, int y) {
			super(container, slot, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}
	}

	/** How much of the current fuel item is left, 0 to 1. */
	public float burn() {
		int total = data.get(RefineryBlockEntity.DATA_BURN_TOTAL);
		return total == 0 ? 0.0f : Mth.clamp((float) data.get(RefineryBlockEntity.DATA_BURN) / total, 0.0f, 1.0f);
	}

	/** How far along the current batch is, 0 to 1. */
	public float progress() {
		int total = data.get(RefineryBlockEntity.DATA_PROGRESS_TOTAL);
		return total == 0 ? 0.0f : Mth.clamp((float) data.get(RefineryBlockEntity.DATA_PROGRESS) / total, 0.0f, 1.0f);
	}

	/** The recipe the inputs make, or null. */
	public @Nullable RefineryRecipe recipe() {
		int index = data.get(RefineryBlockEntity.DATA_RECIPE) - 1;
		return index >= 0 && index < RefineryRecipes.ALL.size() ? RefineryRecipes.ALL.get(index) : null;
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
			boolean moved = RefineryRecipes.isIngredient(stack) && moveItemStackTo(stack, 0, RefineryBlockEntity.INPUT_COUNT, false)
					|| RefineryBlockEntity.burnTicks(stack) > 0
							&& moveItemStackTo(stack, RefineryBlockEntity.FUEL_SLOT, RefineryBlockEntity.FUEL_SLOT + 1, false);
			if (!moved) {
				boolean fromInventory = index < HOTBAR_START;
				if (!moveItemStackTo(stack, fromInventory ? HOTBAR_START : INVENTORY_START,
						fromInventory ? INVENTORY_END : HOTBAR_START, false)) {
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
