package com.jheinz.mymod.fishing;

import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.jheinz.mymod.MyMod;

/**
 * The Tackle Bench: a rod slot and four part slots (line, hook, reel, lure). The part slots show what's fitted to the
 * rod in the rod slot, and editing them edits the rod: put a part in and it's fitted, take one out and it's removed,
 * so the rod comes out with whatever the slots held. The parts live on the rod ({@link RodParts}), never in the
 * bench, so closing the menu only hands back the rod. The server does the syncing; the client is sent the result.
 */
public class TackleBenchMenu extends AbstractContainerMenu {
	public static final int ROD_SLOT = 0;
	public static final int PART_SLOTS = PartSlot.values().length;
	private static final int BENCH_SIZE = 1 + PART_SLOTS;

	// Slot positions, shared with the screen's background texture.
	public static final int ROD_X = 20;
	public static final int ROD_Y = 36;
	public static final int[] PART_X = {52, 52, 74, 74};
	public static final int[] PART_Y = {26, 46, 26, 46};

	private static final int INVENTORY_START = BENCH_SIZE;
	private static final int HOTBAR_START = INVENTORY_START + 27;
	private static final int INVENTORY_END = HOTBAR_START + 9;

	private final ContainerLevelAccess access;
	private final Container bench = new SimpleContainer(BENCH_SIZE) {
		@Override
		public void setChanged() {
			super.setChanged();
			slotsChanged(this);
		}
	};
	/** The rod whose parts are laid out in the part slots, or null. */
	private ItemStack shownRod;
	private boolean syncing;

	public TackleBenchMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, ContainerLevelAccess.NULL);
	}

	public TackleBenchMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
		super(Fishing.TACKLE_BENCH_MENU, containerId);
		this.access = access;
		addSlot(new RodSlot(bench, ROD_SLOT, ROD_X, ROD_Y));
		for (PartSlot part : PartSlot.values()) {
			addSlot(new PartSlotView(bench, part, PART_X[part.ordinal()], PART_Y[part.ordinal()]));
		}
		addStandardInventorySlots(inventory, 8, 84);
	}

	/** The rod in the bench, for the screen's stats readout (empty if none). */
	public ItemStack rod() {
		return bench.getItem(ROD_SLOT);
	}

	@Override
	public void slotsChanged(Container container) {
		super.slotsChanged(container);
		if (container == bench && !syncing) {
			access.execute((level, pos) -> sync());
		}
	}

	private void sync() {
		ItemStack rod = bench.getItem(ROD_SLOT);
		syncing = true;
		try {
			if (rod.isEmpty()) {
				if (shownRod != null) {
					// The rod's been taken, its parts with it.
					for (int i = 1; i < BENCH_SIZE; i++) {
						bench.setItem(i, ItemStack.EMPTY);
					}
				}
				shownRod = null;
			} else if (rod != shownRod) {
				shownRod = rod;
				RodParts parts = RodParts.of(rod);
				for (PartSlot slot : PartSlot.values()) {
					bench.setItem(1 + slot.ordinal(), parts.stack(slot));
				}
			} else {
				RodParts parts = RodParts.EMPTY;
				for (PartSlot slot : PartSlot.values()) {
					parts = parts.with(slot, bench.getItem(1 + slot.ordinal()));
				}
				if (!parts.equals(RodParts.of(rod))) {
					if (parts.isEmpty()) {
						rod.remove(Fishing.ROD_PARTS);
					} else {
						rod.set(Fishing.ROD_PARTS, parts);
					}
					access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.6f, 1.4f));
				}
			}
		} finally {
			syncing = false;
		}
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		access.execute((level, pos) -> {
			// The part slots are only a view of the rod's parts: drop the view, hand back the rod.
			syncing = true;
			for (int i = 1; i < BENCH_SIZE; i++) {
				bench.removeItemNoUpdate(i);
			}
			syncing = false;
			clearContainer(player, bench);
		});
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		if (index < BENCH_SIZE) {
			if (!moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, true)) {
				return ItemStack.EMPTY;
			}
		} else if (stack.getItem() instanceof RodItem) {
			if (!moveItemStackTo(stack, ROD_SLOT, ROD_SLOT + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (stack.getItem() instanceof RodPartItem part && !rod().isEmpty()) {
			int target = 1 + part.slot().ordinal();
			if (!moveItemStackTo(stack, target, target + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else {
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
		return stillValid(access, player, Fishing.TACKLE_BENCH);
	}

	private static class RodSlot extends Slot {
		RodSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return stack.getItem() instanceof RodItem;
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}

		@Override
		public Identifier getNoItemIcon() {
			return MyMod.id("container/slot/fishing_rod");
		}
	}

	/** One part slot: takes only its kind of part, and only while there's a rod to fit it to. */
	private class PartSlotView extends Slot {
		private final PartSlot part;

		PartSlotView(Container container, PartSlot part, int x, int y) {
			super(container, 1 + part.ordinal(), x, y);
			this.part = part;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return !rod().isEmpty() && stack.getItem() instanceof RodPartItem item && item.slot() == part;
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}

		@Override
		public Identifier getNoItemIcon() {
			return rod().isEmpty() ? null : MyMod.id("container/slot/rod_" + part.id());
		}
	}
}
