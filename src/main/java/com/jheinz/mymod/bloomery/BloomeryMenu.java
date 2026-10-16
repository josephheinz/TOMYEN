package com.jheinz.mymod.bloomery;

import net.minecraft.server.level.ServerLevel;
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

import com.jheinz.mymod.bloomery.BloomeryBlockEntity.Fuel;
import com.jheinz.mymod.bloomery.BloomeryBlockEntity.Stroke;

/**
 * The ore and fuel slots, the bloom slot, and the ingot slot the forge step fills, over the player's inventory. Two
 * buttons: {@link #PUMP} works the bellows and {@link #FORGE} strikes the bloom.
 */
public class BloomeryMenu extends AbstractContainerMenu {
	public static final int PUMP = 0;
	public static final int FORGE = 1;
	// Slot positions, shared with the screen and its background (tools/gen_bloomery.py).
	public static final int ORE_X = 38;
	public static final int ORE_Y = 17;
	public static final int FUEL_X = 38;
	public static final int FUEL_Y = 53;
	public static final int BLOOM_X = 92;
	public static final int BLOOM_Y = 35;
	public static final int INGOT_X = 144;
	public static final int INGOT_Y = 35;

	private static final int INVENTORY_START = BloomeryBlockEntity.SLOTS;
	private static final int HOTBAR_START = INVENTORY_START + 27;
	private static final int INVENTORY_END = HOTBAR_START + 9;

	private final Container container;
	private final ContainerData data;

	public BloomeryMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, new SimpleContainer(BloomeryBlockEntity.SLOTS), new SimpleContainerData(BloomeryBlockEntity.DATA_COUNT));
	}

	public BloomeryMenu(int containerId, Inventory inventory, Container container, ContainerData data) {
		super(Bloomery.BLOOMERY_MENU, containerId);
		checkContainerSize(container, BloomeryBlockEntity.SLOTS);
		checkContainerDataCount(data, BloomeryBlockEntity.DATA_COUNT);
		this.container = container;
		this.data = data;
		addSlot(new Slot(container, BloomeryBlockEntity.ORE_SLOT, ORE_X, ORE_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return BloomeryBlockEntity.isOre(stack);
			}
		});
		addSlot(new Slot(container, BloomeryBlockEntity.FUEL_SLOT, FUEL_X, FUEL_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return BloomeryBlockEntity.isFuel(stack);
			}
		});
		addSlot(new Slot(container, BloomeryBlockEntity.BLOOM_SLOT, BLOOM_X, BLOOM_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}
		});
		addSlot(new Slot(container, BloomeryBlockEntity.INGOT_SLOT, INGOT_X, INGOT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}
		});
		addStandardInventorySlots(inventory, 8, 84);
		addDataSlots(data);
	}

	@Override
	public boolean clickMenuButton(Player player, int buttonId) {
		if (!(container instanceof BloomeryBlockEntity bloomery) || !(player.level() instanceof ServerLevel level)) {
			return buttonId == PUMP || buttonId == FORGE;
		}
		if (buttonId == PUMP) {
			bloomery.pump(level);
			return true;
		}
		if (buttonId == FORGE) {
			bloomery.forge(level);
			return true;
		}
		return false;
	}

	public int temperature() {
		return data.get(BloomeryBlockEntity.DATA_TEMPERATURE);
	}

	/** How much of the burning piece of fuel is left, 0 to 1. */
	public float burn() {
		return Mth.clamp((float) data.get(BloomeryBlockEntity.DATA_BURN) / BloomeryBlockEntity.BURN_TICKS, 0.0f, 1.0f);
	}

	public boolean lit() {
		return data.get(BloomeryBlockEntity.DATA_BURN) > 0;
	}

	/** How far the bloom has gathered, 0 to 1. */
	public float progress() {
		return Mth.clamp((float) data.get(BloomeryBlockEntity.DATA_PROGRESS) / BloomeryBlockEntity.BLOOM_WORK, 0.0f, 1.0f);
	}

	public int sinceStroke() {
		return data.get(BloomeryBlockEntity.DATA_SINCE);
	}

	public int combo() {
		return data.get(BloomeryBlockEntity.DATA_COMBO);
	}

	public Stroke stroke() {
		return Stroke.of(data.get(BloomeryBlockEntity.DATA_STROKE));
	}

	public int forgeBlows() {
		return data.get(BloomeryBlockEntity.DATA_FORGE);
	}

	public Fuel fuel() {
		return Fuel.of(data.get(BloomeryBlockEntity.DATA_FUEL));
	}

	public boolean hasBloom() {
		return !getSlot(BloomeryBlockEntity.BLOOM_SLOT).getItem().isEmpty();
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
			boolean moved = BloomeryBlockEntity.isOre(stack)
					? moveItemStackTo(stack, BloomeryBlockEntity.ORE_SLOT, BloomeryBlockEntity.ORE_SLOT + 1, false)
					: BloomeryBlockEntity.isFuel(stack) && moveItemStackTo(stack, BloomeryBlockEntity.FUEL_SLOT, BloomeryBlockEntity.FUEL_SLOT + 1, false);
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
