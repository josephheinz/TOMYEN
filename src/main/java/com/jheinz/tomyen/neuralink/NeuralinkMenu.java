package com.jheinz.tomyen.neuralink;

import net.minecraft.core.NonNullList;
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
import com.jheinz.tomyen.powerarmor.PowerArmorPieceItem;
import com.jheinz.tomyen.station.ModMenus;

/**
 * The module sockets of the Neuralink in the player's hand, over their inventory. The sockets are stored on
 * the Neuralink itself, so every change is written straight back to it; while the menu is open the Neuralink
 * can't be moved. A Mk II has five sockets to the original's three, so the panel grows to fit them. A power
 * armor helmet's built-in sockets (five, like a Mk II) open here too.
 */
public class NeuralinkMenu extends AbstractContainerMenu {
	// Slot positions, shared with the screen.
	public static final int MODULE_X = 76;
	public static final int INVENTORY_X = 17;

	/**
	 * Where things go for a Neuralink with this many sockets (y relative to the panel's top).
	 *
	 * @param holoBottom bottom of the hologram panel the sockets are wired to
	 * @param dividerY   the rule between the sockets and the inventory
	 */
	public record Layout(int sockets, int moduleY, int spacing, int holoBottom, int dividerY, int inventoryY, int height) {
		public static Layout of(int sockets) {
			// Three sockets get room to breathe; more pack a little closer and the panel grows taller.
			int spacing = sockets <= 3 ? 24 : 20;
			int moduleY = sockets <= 3 ? 26 : 24;
			int holoBottom = moduleY + (sockets - 1) * spacing + 26;
			int inventoryY = holoBottom + 20;
			return new Layout(sockets, moduleY, spacing, holoBottom, holoBottom + 6, inventoryY, inventoryY + 82);
		}

		public int socketY(int socket) {
			return moduleY + socket * spacing;
		}
	}

	private final Inventory inventory;
	private final InteractionHand hand;
	/** The inventory index of the Neuralink being edited. */
	private final int deviceSlot;
	private final Layout layout;
	private final int inventoryStart;
	private final int hotbarStart;
	private final int inventoryEnd;
	private final SimpleContainer modules;

	/** Client side: the server only opens this from a socketed item in hand, the main hand if it holds one. */
	public NeuralinkMenu(int containerId, Inventory inventory) {
		this(containerId, inventory,
				NeuralinkItem.moduleSlots(inventory.player.getMainHandItem()) > 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
	}

	public NeuralinkMenu(int containerId, Inventory inventory, InteractionHand hand) {
		super(ModMenus.NEURALINK, containerId);
		this.inventory = inventory;
		this.hand = hand;
		this.deviceSlot = hand == InteractionHand.MAIN_HAND ? inventory.getSelectedSlot() : Inventory.SLOT_OFFHAND;
		this.layout = Layout.of(Math.max(1, NeuralinkItem.moduleSlots(device())));
		this.inventoryStart = layout.sockets();
		this.hotbarStart = inventoryStart + 27;
		this.inventoryEnd = hotbarStart + 9;
		this.modules = new SimpleContainer(layout.sockets()) {
			@Override
			public void setChanged() {
				super.setChanged();
				ItemStack device = device();
				if (NeuralinkItem.moduleSlots(device) > 0) {
					device.set(ModComponents.NEURALINK_MODULES, ItemContainerContents.fromItems(getItems()));
				}
			}
		};
		NonNullList<ItemStack> installed = modules.getItems();
		device().getOrDefault(ModComponents.NEURALINK_MODULES, ItemContainerContents.EMPTY).copyInto(installed);
		for (int i = 0; i < layout.sockets(); i++) {
			addSlot(new ModuleSlot(i, MODULE_X, layout.socketY(i)));
		}
		addStandardInventorySlots(inventory, INVENTORY_X, layout.inventoryY());
	}

	public Layout layout() {
		return layout;
	}

	/** Whether this is the upgraded, five-socket Neuralink. */
	public boolean isMk2() {
		return layout.sockets() > 3;
	}

	/** Whether the sockets being edited are a power armor helmet's rather than a Neuralink's. */
	public boolean isHelmet() {
		return PowerArmorPieceItem.isHelmet(device());
	}

	private ItemStack device() {
		return inventory.player.getItemInHand(hand);
	}

	/** Whether a slot holds the Neuralink being edited (the screen marks it). */
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

		if (index < inventoryStart) {
			if (!moveItemStackTo(stack, inventoryStart, inventoryEnd, true)) {
				return ItemStack.EMPTY;
			}
		} else if (!(stack.getItem() instanceof NeuralinkModuleItem) || !moveItemStackTo(stack, 0, inventoryStart, false)) {
			boolean fromInventory = index < hotbarStart;
			if (!moveItemStackTo(stack, fromInventory ? hotbarStart : inventoryStart,
					fromInventory ? inventoryEnd : hotbarStart, false)) {
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
		return NeuralinkItem.moduleSlots(device()) > 0;
	}

	/** Takes one module, and not one the Neuralink already has in another socket. */
	private class ModuleSlot extends Slot {
		ModuleSlot(int index, int x, int y) {
			super(modules, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			if (!(stack.getItem() instanceof NeuralinkModuleItem item)) {
				return false;
			}
			for (int i = 0; i < modules.getContainerSize(); i++) {
				if (i != getContainerSlot() && modules.getItem(i).getItem() instanceof NeuralinkModuleItem other
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
