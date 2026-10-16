package com.jheinz.tomyen.industry;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The Refinery: three input slots, a fuel slot, the product and two byproduct slots. Like the Foundry it only
 * burns fuel while it's working, so a half-burnt lump waits for the next batch; unlike it, it takes ordinary
 * fuel ({@link #burnTicks}). Hoppers feed inputs from the top, fuel from the sides, and take products (and empty
 * lava buckets) from below.
 */
public class RefineryBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
	public static final int INPUT_COUNT = 3;
	public static final int FUEL_SLOT = 3;
	public static final int OUTPUT_SLOT = 4;
	public static final int BYPRODUCT_SLOT = 5;
	public static final int SIZE = 7;

	// ContainerData indices, shared with the menu.
	public static final int DATA_BURN = 0;
	public static final int DATA_BURN_TOTAL = 1;
	public static final int DATA_PROGRESS = 2;
	public static final int DATA_PROGRESS_TOTAL = 3;
	/** Which recipe is running, as its index in {@link RefineryRecipes#ALL} plus one; 0 for none. */
	public static final int DATA_RECIPE = 4;
	public static final int DATA_COUNT = 5;

	private static final int[] TOP_SLOTS = {0, 1, 2};
	private static final int[] SIDE_SLOTS = {FUEL_SLOT};
	private static final int[] BOTTOM_SLOTS = {OUTPUT_SLOT, BYPRODUCT_SLOT, BYPRODUCT_SLOT + 1, FUEL_SLOT};
	private static final Component TITLE = Component.translatable("container.tomyen.refinery");

	private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
	private int burn;
	private int burnTotal;
	private int progress;
	private int progressTotal;
	private int recipeIndex;

	private final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case DATA_BURN -> burn;
				case DATA_BURN_TOTAL -> burnTotal;
				case DATA_PROGRESS -> progress;
				case DATA_PROGRESS_TOTAL -> progressTotal;
				case DATA_RECIPE -> recipeIndex;
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
			switch (index) {
				case DATA_BURN -> burn = value;
				case DATA_BURN_TOTAL -> burnTotal = value;
				case DATA_PROGRESS -> progress = value;
				case DATA_PROGRESS_TOTAL -> progressTotal = value;
				case DATA_RECIPE -> recipeIndex = value;
				default -> {
				}
			}
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	public RefineryBlockEntity(BlockPos pos, BlockState state) {
		super(Industry.REFINERY_ENTITY, pos, state);
	}

	/** Ticks of refining a fuel item powers: coal 1600, a coal block 16000, a lava bucket 20000, tar 800. */
	public static int burnTicks(ItemStack stack) {
		if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) {
			return 1600;
		}
		if (stack.is(Items.COAL_BLOCK)) {
			return 16000;
		}
		if (stack.is(Items.LAVA_BUCKET)) {
			return 20000;
		}
		return stack.is(Industry.TAR) ? 800 : 0;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, RefineryBlockEntity refinery) {
		RefineryRecipe recipe = RefineryRecipes.find(refinery);
		boolean canRefine = recipe != null && refinery.hasRoomFor(recipe);
		int index = canRefine ? RefineryRecipes.ALL.indexOf(recipe) + 1 : 0;
		boolean changed = index != refinery.recipeIndex;
		refinery.recipeIndex = index;

		if (canRefine && refinery.burn == 0) {
			ItemStack fuel = refinery.items.get(FUEL_SLOT);
			int ticks = burnTicks(fuel);
			if (ticks > 0) {
				refinery.burn = refinery.burnTotal = ticks;
				if (fuel.is(Items.LAVA_BUCKET)) {
					refinery.items.set(FUEL_SLOT, new ItemStack(Items.BUCKET));
				} else {
					fuel.shrink(1);
				}
				changed = true;
			}
		}

		boolean working = canRefine && refinery.burn > 0;
		if (working) {
			refinery.burn--;
			refinery.progressTotal = recipe.ticks();
			if (++refinery.progress >= refinery.progressTotal) {
				refinery.progress = 0;
				recipe.consume(refinery);
				refinery.put(OUTPUT_SLOT, recipe.result());
				List<RefineryRecipe.Output> byproducts = recipe.byproducts();
				for (int i = 0; i < byproducts.size(); i++) {
					refinery.put(BYPRODUCT_SLOT + i, byproducts.get(i).stack());
				}
			}
			changed = true;
		} else if (refinery.progress > 0) {
			// Lose the batch if the inputs are taken out; let it cool back slowly without fuel.
			refinery.progress = canRefine ? Math.max(0, refinery.progress - 2) : 0;
			changed = true;
		}

		if (state.getValue(RefineryBlock.LIT) != working) {
			level.setBlock(pos, state.setValue(RefineryBlock.LIT, working), Block.UPDATE_ALL);
		}
		if (changed) {
			setChanged(level, pos, state);
		}
	}

	private void put(int slot, ItemStack stack) {
		ItemStack existing = items.get(slot);
		if (existing.isEmpty()) {
			items.set(slot, stack);
		} else {
			existing.grow(stack.getCount());
		}
	}

	private boolean fits(int slot, ItemStack stack) {
		ItemStack existing = items.get(slot);
		return existing.isEmpty() || ItemStack.isSameItemSameComponents(existing, stack)
				&& existing.getCount() + stack.getCount() <= existing.getMaxStackSize();
	}

	private boolean hasRoomFor(RefineryRecipe recipe) {
		if (!fits(OUTPUT_SLOT, recipe.result())) {
			return false;
		}
		for (int i = 0; i < recipe.byproducts().size(); i++) {
			if (!fits(BYPRODUCT_SLOT + i, recipe.byproducts().get(i).stack())) {
				return false;
			}
		}
		return true;
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, items);
		burn = input.getIntOr("burn", 0);
		burnTotal = input.getIntOr("burn_total", 0);
		progress = input.getIntOr("progress", 0);
		progressTotal = input.getIntOr("progress_total", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("burn", burn);
		output.putInt("burn_total", burnTotal);
		output.putInt("progress", progress);
		output.putInt("progress_total", progressTotal);
		ContainerHelper.saveAllItems(output, items);
	}

	@Override
	protected Component getDefaultName() {
		return TITLE;
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> items) {
		this.items = items;
	}

	@Override
	public int getContainerSize() {
		return items.size();
	}

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		return new RefineryMenu(containerId, inventory, this, data);
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		if (slot == FUEL_SLOT) {
			return burnTicks(stack) > 0;
		}
		return slot < INPUT_COUNT;
	}

	@Override
	public int[] getSlotsForFace(Direction direction) {
		return direction == Direction.DOWN ? BOTTOM_SLOTS : direction == Direction.UP ? TOP_SLOTS : SIDE_SLOTS;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
		return canPlaceItem(slot, stack);
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
		// Only empty buckets come back out of the fuel slot.
		return direction == Direction.DOWN && (slot != FUEL_SLOT || stack.is(Items.BUCKET));
	}
}
