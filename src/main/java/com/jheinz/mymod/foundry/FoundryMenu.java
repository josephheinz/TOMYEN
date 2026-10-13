package com.jheinz.mymod.foundry;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.util.Mth;

import com.jheinz.mymod.station.ModMenus;

/** Two inputs, a lava fuel slot and an output, over the player's inventory. */
public class FoundryMenu extends AbstractContainerMenu {
	// Slot positions, shared with the screen's background texture.
	public static final int INPUT_X = 44;
	public static final int INPUT_Y = 17;
	public static final int FUEL_X = 53;
	public static final int FUEL_Y = 53;
	public static final int OUTPUT_X = 116;
	public static final int OUTPUT_Y = 35;

	private static final int INVENTORY_START = 4;
	private static final int HOTBAR_START = INVENTORY_START + 27;
	private static final int INVENTORY_END = HOTBAR_START + 9;

	private final Container container;
	private final ContainerData data;

	public FoundryMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, new SimpleContainer(4), new SimpleContainerData(FoundryBlockEntity.DATA_COUNT));
	}

	public FoundryMenu(int containerId, Inventory inventory, Container container, ContainerData data) {
		super(ModMenus.FOUNDRY, containerId);
		checkContainerSize(container, 4);
		checkContainerDataCount(data, FoundryBlockEntity.DATA_COUNT);
		this.container = container;
		this.data = data;
		for (int i = 0; i < FoundryBlockEntity.INPUT_COUNT; i++) {
			addSlot(new Slot(container, i, INPUT_X + i * 18, INPUT_Y));
		}
		addSlot(new Slot(container, FoundryBlockEntity.FUEL_SLOT, FUEL_X, FUEL_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.is(Items.LAVA_BUCKET);
			}
		});
		addSlot(new Slot(container, FoundryBlockEntity.OUTPUT_SLOT, OUTPUT_X, OUTPUT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}
		});
		addStandardInventorySlots(inventory, 8, 84);
		addDataSlots(data);
	}

	/** How full of heat the Foundry is, 0 to 1. */
	public float heat() {
		int total = data.get(FoundryBlockEntity.DATA_HEAT_TOTAL);
		return total == 0 ? 0.0f : Mth.clamp((float) data.get(FoundryBlockEntity.DATA_HEAT) / total, 0.0f, 1.0f);
	}

	/** The Foundry's temperature, in °C. */
	public int temperature() {
		return data.get(FoundryBlockEntity.DATA_TEMPERATURE);
	}

	/** How hot the current recipe needs the Foundry, in °C; 0 if it doesn't care. */
	public int requiredTemperature() {
		return data.get(FoundryBlockEntity.DATA_REQUIRED_TEMPERATURE);
	}

	/** The hottest the current recipe lets the Foundry be, in °C; 0 if it doesn't care. */
	public int maxTemperature() {
		return data.get(FoundryBlockEntity.DATA_MAX_TEMPERATURE);
	}

	/** How far along the current smelt is, 0 to 1. */
	public float progress() {
		int total = data.get(FoundryBlockEntity.DATA_PROGRESS_TOTAL);
		return total == 0 ? 0.0f : Mth.clamp((float) data.get(FoundryBlockEntity.DATA_PROGRESS) / total, 0.0f, 1.0f);
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
			boolean moved = stack.is(Items.LAVA_BUCKET)
					? moveItemStackTo(stack, FoundryBlockEntity.FUEL_SLOT, FoundryBlockEntity.FUEL_SLOT + 1, false)
					: FoundryRecipes.isIngredient(stack) && moveItemStackTo(stack, 0, FoundryBlockEntity.INPUT_COUNT, false);
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
