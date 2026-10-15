package com.jheinz.mymod.survival;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * The Scavenger's Satchel opened from the hand: its nine slots in a 3x3 grid over the player's inventory. It's laid
 * out exactly as a dispenser's menu ({@link MenuType#GENERIC_3x3}), so the client shows it with the dispenser screen;
 * on the server the slots refuse satchels and anything else that can't go in a container item (shulker boxes), and the
 * satchel being worn can't be moved while it's open. Every change is written straight back to the satchel.
 */
public class SatchelMenu extends AbstractContainerMenu {
	public static final int SIZE = 9;

	private final ItemStack satchel;
	private final InteractionHand hand;
	private final SimpleContainer contents;

	public SatchelMenu(int containerId, Inventory inventory, ItemStack satchel, InteractionHand hand) {
		super(MenuType.GENERIC_3x3, containerId);
		this.satchel = satchel;
		this.hand = hand;
		this.contents = new SimpleContainer(SIZE) {
			@Override
			public void setChanged() {
				super.setChanged();
				save();
			}
		};
		satchel.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(contents.getItems());
		for (int y = 0; y < 3; y++) {
			for (int x = 0; x < 3; x++) {
				addSlot(new Slot(contents, x + y * 3, 62 + x * 18, 17 + y * 18) {
					@Override
					public boolean mayPlace(ItemStack stack) {
						return fits(stack);
					}
				});
			}
		}
		addStandardInventorySlots(inventory, 8, 84);
	}

	/** Whether a stack may go in a satchel: not another satchel, nor anything that refuses to sit in a container item. */
	public static boolean fits(ItemStack stack) {
		return !(stack.getItem() instanceof SatchelItem) && stack.getItem().canFitInsideContainerItems();
	}

	private void save() {
		satchel.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents.getItems()));
	}

	@Override
	public void clicked(int slotIndex, int button, ContainerInput input, Player player) {
		// The satchel itself stays put while it's open.
		if (slotIndex >= 0 && slotIndex < slots.size() && slots.get(slotIndex).getItem() == satchel) {
			return;
		}
		if (input == ContainerInput.SWAP && (button == 40 ? player.getOffhandItem() : player.getInventory().getItem(button)) == satchel) {
			return;
		}
		super.clicked(slotIndex, button, input, player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		Slot slot = slots.get(slotIndex);
		if (!slot.hasItem() || slot.getItem() == satchel) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack moved = stack.copy();
		if (slotIndex < SIZE) {
			if (!moveItemStackTo(stack, SIZE, slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (!fits(stack) || !moveItemStackTo(stack, 0, SIZE, false)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		if (stack.getCount() == moved.getCount()) {
			return ItemStack.EMPTY;
		}
		slot.onTake(player, stack);
		return moved;
	}

	@Override
	public boolean stillValid(Player player) {
		return player.getItemInHand(hand) == satchel;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		save();
	}
}
