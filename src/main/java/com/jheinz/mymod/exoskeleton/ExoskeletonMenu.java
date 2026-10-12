package com.jheinz.mymod.exoskeleton;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import com.jheinz.mymod.ModComponents;
import com.jheinz.mymod.station.ModMenus;

/**
 * The module sockets of the exoskeleton in the player's hand, over their inventory. Like the Neuralink's, the
 * sockets are stored on the suit itself, so every change is written straight back to it; while the menu is
 * open the suit can't be moved.
 */
public class ExoskeletonMenu extends AbstractContainerMenu {
	// Slot positions, shared with the screen: the sockets in a column, the inventory below.
	public static final int MODULE_X = 10;
	public static final int MODULE_Y = 20;
	public static final int MODULE_SPACING = 20;
	public static final int INVENTORY_X = 8;
	public static final int INVENTORY_Y = 114;
	public static final int HEIGHT = INVENTORY_Y + 82;

	private static final int INVENTORY_START = ExoskeletonItem.MODULE_SLOTS;
	private static final int HOTBAR_START = INVENTORY_START + 27;
	private static final int INVENTORY_END = HOTBAR_START + 9;

	private final Inventory inventory;
	private final InteractionHand hand;
	/** The inventory index of the exoskeleton being edited. */
	private final int deviceSlot;
	private final SimpleContainer modules;

	/** Client side: the server only opens this from an exoskeleton in hand, the main hand if it holds one. */
	public ExoskeletonMenu(int containerId, Inventory inventory) {
		this(containerId, inventory,
				inventory.player.getMainHandItem().getItem() instanceof ExoskeletonItem ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
	}

	public ExoskeletonMenu(int containerId, Inventory inventory, InteractionHand hand) {
		super(ModMenus.EXOSKELETON, containerId);
		this.inventory = inventory;
		this.hand = hand;
		this.deviceSlot = hand == InteractionHand.MAIN_HAND ? inventory.getSelectedSlot() : Inventory.SLOT_OFFHAND;
		this.modules = new SimpleContainer(ExoskeletonItem.MODULE_SLOTS) {
			@Override
			public void setChanged() {
				super.setChanged();
				ItemStack device = device();
				if (device.getItem() instanceof ExoskeletonItem) {
					device.set(ModComponents.EXOSKELETON_MODULES, ItemContainerContents.fromItems(getItems()));
				}
			}
		};
		device().getOrDefault(ModComponents.EXOSKELETON_MODULES, ItemContainerContents.EMPTY).copyInto(modules.getItems());
		for (int i = 0; i < ExoskeletonItem.MODULE_SLOTS; i++) {
			addSlot(new ModuleSlot(i, MODULE_X, socketY(i)));
		}
		addStandardInventorySlots(inventory, INVENTORY_X, INVENTORY_Y);
	}

	public static int socketY(int socket) {
		return MODULE_Y + socket * MODULE_SPACING;
	}

	private ItemStack device() {
		return inventory.player.getItemInHand(hand);
	}

	/** Whether a slot holds the exoskeleton being edited (the screen marks it). */
	public boolean isDeviceSlot(Slot slot) {
		return slot.container == inventory && slot.getContainerSlot() == deviceSlot;
	}

	@Override
	public void clicked(int slotIndex, int buttonNum, ContainerInput input, Player player) {
		boolean onDevice = slotIndex >= 0 && slotIndex < slots.size() && isDeviceSlot(slots.get(slotIndex));
		if (onDevice || (input == ContainerInput.SWAP && buttonNum == deviceSlot)) {
			return;
		}
		super.clicked(slotIndex, buttonNum, input, player);
	}

	@Override
	public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
		return !isDeviceSlot(slot) && super.canTakeItemForPickAll(stack, slot);
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
		} else if (!(stack.getItem() instanceof ExoModuleItem) || !moveItemStackTo(stack, 0, INVENTORY_START, false)) {
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
	public boolean stillValid(Player player) {
		return device().getItem() instanceof ExoskeletonItem;
	}

	/** Takes one module, and not one the suit already has in another socket. */
	private class ModuleSlot extends Slot {
		ModuleSlot(int index, int x, int y) {
			super(modules, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			if (!(stack.getItem() instanceof ExoModuleItem item)) {
				return false;
			}
			for (int i = 0; i < modules.getContainerSize(); i++) {
				if (i != getContainerSlot() && modules.getItem(i).getItem() instanceof ExoModuleItem other
						&& other.module() == item.module()) {
					return false;
				}
			}
			return true;
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}
	}
}
