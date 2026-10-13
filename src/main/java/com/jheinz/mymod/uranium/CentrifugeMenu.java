package com.jheinz.mymod.uranium;

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

import com.jheinz.mymod.industry.RefineryBlockEntity;

/** The raw uranium slot over the fuel slot, and the enriched and depleted outputs, over the player's inventory. */
public class CentrifugeMenu extends AbstractContainerMenu {
	// Slot positions, shared with the screen's background texture (tools/gen_ores_tu.py).
	public static final int INPUT_X = 56;
	public static final int INPUT_Y = 17;
	public static final int FUEL_X = 56;
	public static final int FUEL_Y = 53;
	public static final int ENRICHED_X = 116;
	public static final int ENRICHED_Y = 22;
	public static final int DEPLETED_X = 116;
	public static final int DEPLETED_Y = 48;

	private static final int INVENTORY_START = CentrifugeBlockEntity.SIZE;
	private static final int HOTBAR_START = INVENTORY_START + 27;
	private static final int INVENTORY_END = HOTBAR_START + 9;

	private final Container container;
	private final ContainerData data;

	public CentrifugeMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, new SimpleContainer(CentrifugeBlockEntity.SIZE), new SimpleContainerData(CentrifugeBlockEntity.DATA_COUNT));
	}

	public CentrifugeMenu(int containerId, Inventory inventory, Container container, ContainerData data) {
		super(Uranium.CENTRIFUGE_MENU, containerId);
		checkContainerSize(container, CentrifugeBlockEntity.SIZE);
		checkContainerDataCount(data, CentrifugeBlockEntity.DATA_COUNT);
		this.container = container;
		this.data = data;
		addSlot(new Slot(container, CentrifugeBlockEntity.INPUT_SLOT, INPUT_X, INPUT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.is(Uranium.RAW_URANIUM);
			}
		});
		addSlot(new Slot(container, CentrifugeBlockEntity.FUEL_SLOT, FUEL_X, FUEL_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return RefineryBlockEntity.burnTicks(stack) > 0;
			}
		});
		addSlot(new OutputSlot(container, CentrifugeBlockEntity.ENRICHED_SLOT, ENRICHED_X, ENRICHED_Y));
		addSlot(new OutputSlot(container, CentrifugeBlockEntity.DEPLETED_SLOT, DEPLETED_X, DEPLETED_Y));
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
		int total = data.get(CentrifugeBlockEntity.DATA_BURN_TOTAL);
		return total == 0 ? 0.0f : Mth.clamp((float) data.get(CentrifugeBlockEntity.DATA_BURN) / total, 0.0f, 1.0f);
	}

	/** How far along the current batch is, 0 to 1. */
	public float progress() {
		return Mth.clamp((float) data.get(CentrifugeBlockEntity.DATA_PROGRESS) / CentrifugeBlockEntity.SPIN_TICKS, 0.0f, 1.0f);
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
			boolean moved = stack.is(Uranium.RAW_URANIUM)
					&& moveItemStackTo(stack, CentrifugeBlockEntity.INPUT_SLOT, CentrifugeBlockEntity.INPUT_SLOT + 1, false)
					|| RefineryBlockEntity.burnTicks(stack) > 0
							&& moveItemStackTo(stack, CentrifugeBlockEntity.FUEL_SLOT, CentrifugeBlockEntity.FUEL_SLOT + 1, false);
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
