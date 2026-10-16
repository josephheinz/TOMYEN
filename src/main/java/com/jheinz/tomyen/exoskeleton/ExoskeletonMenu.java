package com.jheinz.tomyen.exoskeleton;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import com.jheinz.tomyen.ModComponents;
import com.jheinz.tomyen.station.ModMenus;

/**
 * The sockets of the exoskeleton in the player's hand, over their inventory: modules, armor and an elytra, by
 * the rules in {@link ExoSockets}. Like the Neuralink's, the sockets are stored on the suit itself, so every
 * change is written straight back to it; while the menu is open the suit can't be moved.
 */
public class ExoskeletonMenu extends AbstractContainerMenu {
	// Slot positions, shared with the screen: the sockets in a column, the inventory below.
	public static final int SOCKET_X = 10;
	public static final int SOCKET_Y = 18;
	public static final int SOCKET_SPACING = 20;
	public static final int INVENTORY_X = 8;
	public static final int INVENTORY_Y = SOCKET_Y + ExoSockets.COUNT * SOCKET_SPACING + 12;
	public static final int HEIGHT = INVENTORY_Y + 82;

	private static final int INVENTORY_START = ExoSockets.COUNT;
	private static final int HOTBAR_START = INVENTORY_START + 27;
	private static final int INVENTORY_END = HOTBAR_START + 9;

	private final Inventory inventory;
	private final InteractionHand hand;
	/** The inventory index of the exoskeleton being edited. */
	private final int deviceSlot;
	private final SimpleContainer sockets;

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
		this.sockets = new SimpleContainer(ExoSockets.COUNT) {
			@Override
			public void setChanged() {
				super.setChanged();
				ItemStack device = device();
				if (device.getItem() instanceof ExoskeletonItem) {
					device.set(ModComponents.EXOSKELETON_MODULES, ItemContainerContents.fromItems(getItems()));
				}
			}
		};
		device().getOrDefault(ModComponents.EXOSKELETON_MODULES, ItemContainerContents.EMPTY).copyInto(sockets.getItems());
		for (int i = 0; i < ExoSockets.COUNT; i++) {
			addSlot(new SocketSlot(i, SOCKET_X, socketY(i)));
		}
		addStandardInventorySlots(inventory, INVENTORY_X, INVENTORY_Y);
	}

	public static int socketY(int socket) {
		return SOCKET_Y + socket * SOCKET_SPACING;
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
		} else if (!ExoSockets.fits(stack) || !moveItemStackTo(stack, 0, INVENTORY_START, false)) {
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

	/** Takes one item: a module the suit doesn't have yet, or armor for a slot no other socket fills. */
	private class SocketSlot extends Slot {
		SocketSlot(int index, int x, int y) {
			super(sockets, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return ExoSockets.mayPlace(sockets.getItems(), getContainerSlot(), stack);
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}
	}
}
