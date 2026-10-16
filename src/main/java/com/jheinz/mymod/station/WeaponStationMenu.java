package com.jheinz.mymod.station;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.jheinz.mymod.ModBlocks;

/**
 * Four input slots and a result slot, like a smithing table. The server matches the inputs
 * against {@link StationRecipes} (or fits attachments to a gun, see {@link AttachmentFitting}) and puts
 * the result in the result slot; taking it consumes the inputs.
 */
public class WeaponStationMenu extends AbstractContainerMenu {
	public static final int INPUT_COUNT = 4;
	public static final int RESULT_SLOT = INPUT_COUNT;

	// Slot positions, shared with the screen's background texture.
	public static final int INPUT_X = 17;
	public static final int SLOT_Y = 47;
	public static final int RESULT_X = 129;

	private static final int INVENTORY_START = RESULT_SLOT + 1;
	private static final int HOTBAR_START = INVENTORY_START + 27;
	private static final int INVENTORY_END = HOTBAR_START + 9;

	private final ContainerLevelAccess access;
	private final Player player;
	private final ResultContainer result = new ResultContainer();
	private final Container inputs = new SimpleContainer(INPUT_COUNT) {
		@Override
		public void setChanged() {
			super.setChanged();
			slotsChanged(this);
		}
	};

	public WeaponStationMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, ContainerLevelAccess.NULL);
	}

	public WeaponStationMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
		super(ModMenus.WEAPON_STATION, containerId);
		this.access = access;
		this.player = inventory.player;
		for (int i = 0; i < INPUT_COUNT; i++) {
			addSlot(new Slot(inputs, i, INPUT_X + i * 18, SLOT_Y));
		}
		addSlot(new ResultSlot(result, RESULT_X, SLOT_Y));
		addStandardInventorySlots(inventory, 8, 84);
	}

	@Override
	public void slotsChanged(Container container) {
		super.slotsChanged(container);
		if (container == inputs) {
			// Only the server knows a real level/pos; the client gets the result slot synced.
			access.execute((level, pos) -> {
				ItemStack fitted = AttachmentFitting.result(inputs);
				StationRecipe recipe = fitted == null ? StationRecipes.find(inputs, player) : null;
				result.setItem(0, fitted != null ? fitted : recipe == null ? ItemStack.EMPTY : recipe.result());
			});
		}
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		access.execute((level, pos) -> clearContainer(player, inputs));
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();

		if (index == RESULT_SLOT) {
			if (!moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, true)) {
				return ItemStack.EMPTY;
			}
			slot.onQuickCraft(stack, original);
		} else if (index < RESULT_SLOT) {
			if (!moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, false)) {
				return ItemStack.EMPTY;
			}
		} else if (!moveItemStackTo(stack, 0, INPUT_COUNT, false)) {
			boolean fromInventory = index < HOTBAR_START;
			if (!moveItemStackTo(stack, fromInventory ? HOTBAR_START : INVENTORY_START,
					fromInventory ? INVENTORY_END : HOTBAR_START, false)) {
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
	public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
		return slot.container != result && super.canTakeItemForPickAll(stack, slot);
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(access, player, ModBlocks.WEAPON_STATION);
	}

	/** Output only; taking the result pays for it out of the input slots. */
	private class ResultSlot extends Slot {
		ResultSlot(Container container, int x, int y) {
			super(container, 0, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}

		@Override
		public void onTake(Player player, ItemStack stack) {
			if (AttachmentFitting.result(inputs) != null) {
				AttachmentFitting.consume(inputs);
			} else {
				StationRecipe recipe = StationRecipes.find(inputs, player);
				if (recipe != null) {
					recipe.consume(inputs);
				}
			}
			access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1.0f, 1.0f));
			super.onTake(player, stack);
		}
	}
}
