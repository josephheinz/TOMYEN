package com.jheinz.tomyen.starforge;

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

import com.jheinz.tomyen.meteorite.Meteorites;

/** Gear, catalyst and starlight down the left, the infused gear on the right, over the player's inventory. */
public class StarForgeMenu extends AbstractContainerMenu {
	// Slot positions, shared with the screen and its background texture (tools/gen_star_forge.py).
	public static final int INPUT_X = 16;
	public static final int INPUT_Y = 18;
	public static final int CATALYST_X = 16;
	public static final int CATALYST_Y = 40;
	public static final int FUEL_X = 16;
	public static final int FUEL_Y = 62;
	public static final int OUTPUT_X = 144;
	public static final int OUTPUT_Y = 31;
	public static final int INVENTORY_Y = 116;

	private static final int INVENTORY_START = 4;
	private static final int HOTBAR_START = INVENTORY_START + 27;
	private static final int INVENTORY_END = HOTBAR_START + 9;

	private final Container container;
	private final ContainerData data;

	public StarForgeMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, new SimpleContainer(4), new SimpleContainerData(StarForgeBlockEntity.DATA_COUNT));
	}

	public StarForgeMenu(int containerId, Inventory inventory, Container container, ContainerData data) {
		super(StarForge.STAR_FORGE_MENU, containerId);
		checkContainerSize(container, 4);
		checkContainerDataCount(data, StarForgeBlockEntity.DATA_COUNT);
		this.container = container;
		this.data = data;
		addSlot(new Slot(container, StarForgeBlockEntity.INPUT_SLOT, INPUT_X, INPUT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return !InfusionKind.of(stack.copyWithCount(1)).isEmpty();
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
		addSlot(new Slot(container, StarForgeBlockEntity.CATALYST_SLOT, CATALYST_X, CATALYST_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return Infusion.byCatalyst(stack) != null;
			}
		});
		addSlot(new Slot(container, StarForgeBlockEntity.FUEL_SLOT, FUEL_X, FUEL_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.is(Meteorites.STAR_CRYSTAL_SHARD);
			}
		});
		addSlot(new Slot(container, StarForgeBlockEntity.OUTPUT_SLOT, OUTPUT_X, OUTPUT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}
		});
		addStandardInventorySlots(inventory, 8, INVENTORY_Y);
		addDataSlots(data);
	}

	/** How far along the infusion is, 0 to 1. */
	public float progress() {
		return Mth.clamp((float) data.get(StarForgeBlockEntity.DATA_PROGRESS) / StarForgeBlockEntity.INFUSE_TICKS, 0.0f, 1.0f);
	}

	/** How much of the burning shard's starlight is left, 0 to 1. */
	public float starlight() {
		return Mth.clamp((float) data.get(StarForgeBlockEntity.DATA_STARLIGHT) / StarForgeBlockEntity.STARLIGHT_PER_SHARD, 0.0f, 1.0f);
	}

	public StarForgeBlockEntity.Sky sky() {
		StarForgeBlockEntity.Sky[] skies = StarForgeBlockEntity.Sky.values();
		return skies[Math.floorMod(data.get(StarForgeBlockEntity.DATA_SKY), skies.length)];
	}

	/** What the forge would do with what's in its slots now. */
	public Infusing job() {
		return Infusing.of(container.getItem(StarForgeBlockEntity.INPUT_SLOT), container.getItem(StarForgeBlockEntity.CATALYST_SLOT));
	}

	public boolean hasFuel() {
		return data.get(StarForgeBlockEntity.DATA_STARLIGHT) > 0 || container.getItem(StarForgeBlockEntity.FUEL_SLOT).is(Meteorites.STAR_CRYSTAL_SHARD);
	}

	public boolean outputBlocked() {
		return !container.getItem(StarForgeBlockEntity.OUTPUT_SLOT).isEmpty();
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
			boolean moved;
			if (stack.is(Meteorites.STAR_CRYSTAL_SHARD)) {
				moved = moveItemStackTo(stack, StarForgeBlockEntity.FUEL_SLOT, StarForgeBlockEntity.FUEL_SLOT + 1, false);
			} else if (Infusion.byCatalyst(stack) != null) {
				moved = moveItemStackTo(stack, StarForgeBlockEntity.CATALYST_SLOT, StarForgeBlockEntity.CATALYST_SLOT + 1, false);
			} else if (!InfusionKind.of(stack).isEmpty()) {
				moved = moveItemStackTo(stack, StarForgeBlockEntity.INPUT_SLOT, StarForgeBlockEntity.INPUT_SLOT + 1, false);
			} else {
				moved = false;
			}
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
