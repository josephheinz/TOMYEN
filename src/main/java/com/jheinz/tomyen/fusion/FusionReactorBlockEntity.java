package com.jheinz.tomyen.fusion;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import com.jheinz.tomyen.soul.Souls;
import com.jheinz.tomyen.uranium.Uranium;

/**
 * The Fusion Reactor: fuses the two input stacks' items into the fusion they make (any {@link FusionRecipe}, pond,
 * reactor or either, tier 1 or 2). It never fails, and an invalid pair just doesn't start. One fusion takes
 * {@value #FUSE_TICKS} ticks, a tier-2 one twice that, and it burns fuel only while working:
 * <ul>
 * <li>a Uranium Fuel Rod: {@value #ROD_TICKS} ticks, eight tier-1 fusions;
 * <li>a Soul Charge: {@value #SOUL_TICKS} ticks, two.
 * </ul>
 * Taking an input out loses the progress; running out of fuel lets it run down slowly. Hoppers feed the inputs from
 * the top (first free one), fuel from the sides, and take the output from below.
 */
public class FusionReactorBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
	public static final int FIRST_SLOT = 0;
	public static final int SECOND_SLOT = 1;
	public static final int FUEL_SLOT = 2;
	public static final int OUTPUT_SLOT = 3;
	public static final int SIZE = 4;
	public static final int FUSE_TICKS = 300;
	public static final int ROD_TICKS = 2400;
	public static final int SOUL_TICKS = 600;

	public static final int DATA_BURN = 0;
	public static final int DATA_BURN_TOTAL = 1;
	public static final int DATA_PROGRESS = 2;
	public static final int DATA_PROGRESS_TOTAL = 3;
	public static final int DATA_COUNT = 4;

	private static final int[] TOP_SLOTS = {FIRST_SLOT, SECOND_SLOT};
	private static final int[] SIDE_SLOTS = {FUEL_SLOT};
	private static final int[] BOTTOM_SLOTS = {OUTPUT_SLOT};
	private static final Component TITLE = Component.translatable("container.tomyen.fusion_reactor");

	private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
	private int burn;
	private int burnTotal;
	private int progress;
	private int progressTotal = FUSE_TICKS;

	private final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case DATA_BURN -> burn;
				case DATA_BURN_TOTAL -> burnTotal;
				case DATA_PROGRESS -> progress;
				case DATA_PROGRESS_TOTAL -> progressTotal;
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
				default -> {
				}
			}
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	public FusionReactorBlockEntity(BlockPos pos, BlockState state) {
		super(Fusions.REACTOR_ENTITY, pos, state);
	}

	/** Ticks of fusing one fuel item gives; 0 if it isn't fuel. */
	public static int fuelTicks(ItemStack stack) {
		if (stack.is(Uranium.URANIUM_FUEL_ROD)) {
			return ROD_TICKS;
		}
		if (stack.is(Souls.SOUL_CHARGE)) {
			return SOUL_TICKS;
		}
		return 0;
	}

	/** The fusion the inputs make, if the output has room for it. */
	private @Nullable FusionRecipe current() {
		ItemStack a = items.get(FIRST_SLOT);
		ItemStack b = items.get(SECOND_SLOT);
		if (a.isEmpty() || b.isEmpty()) {
			return null;
		}
		FusionRecipe recipe = FusionRecipe.find(a, b);
		if (recipe == null) {
			return null;
		}
		ItemStack out = items.get(OUTPUT_SLOT);
		ItemStack result = recipe.resultStack();
		boolean fits = out.isEmpty() || ItemStack.isSameItemSameComponents(out, result) && out.getCount() + result.getCount() <= out.getMaxStackSize();
		return fits ? recipe : null;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, FusionReactorBlockEntity reactor) {
		FusionRecipe recipe = reactor.current();
		boolean changed = false;
		if (recipe != null && reactor.burn == 0) {
			ItemStack fuel = reactor.items.get(FUEL_SLOT);
			int ticks = fuelTicks(fuel);
			if (ticks > 0) {
				reactor.burn = reactor.burnTotal = ticks;
				fuel.shrink(1);
				changed = true;
			}
		}
		boolean working = recipe != null && reactor.burn > 0;
		if (working) {
			reactor.progressTotal = FUSE_TICKS * recipe.tier();
			reactor.burn--;
			if (++reactor.progress >= reactor.progressTotal) {
				reactor.progress = 0;
				reactor.items.get(FIRST_SLOT).shrink(1);
				reactor.items.get(SECOND_SLOT).shrink(1);
				ItemStack out = reactor.items.get(OUTPUT_SLOT);
				if (out.isEmpty()) {
					reactor.items.set(OUTPUT_SLOT, recipe.resultStack());
				} else {
					out.grow(recipe.count());
				}
				if (level instanceof ServerLevel server) {
					server.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0f, 1.4f);
					server.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.2f, 0.8f);
				}
			} else if (reactor.progress % 40 == 1 && level instanceof ServerLevel server) {
				server.playSound(null, pos, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.7f, 1.0f + 0.6f * reactor.progress / reactor.progressTotal);
			}
			changed = true;
		} else if (reactor.progress > 0) {
			reactor.progress = recipe != null ? Math.max(0, reactor.progress - 2) : 0;
			changed = true;
		}
		if (state.getValue(FusionReactorBlock.LIT) != working) {
			level.setBlock(pos, state.setValue(FusionReactorBlock.LIT, working), Block.UPDATE_ALL);
		}
		if (changed) {
			setChanged(level, pos, state);
		}
	}

	public static void clientTick(Level level, BlockPos pos, BlockState state, FusionReactorBlockEntity reactor) {
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, items);
		burn = input.getIntOr("burn", 0);
		burnTotal = input.getIntOr("burn_total", 0);
		progress = input.getIntOr("progress", 0);
		progressTotal = input.getIntOr("progress_total", FUSE_TICKS);
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
		return new FusionReactorMenu(containerId, inventory, this, data);
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return switch (slot) {
			case FIRST_SLOT, SECOND_SLOT -> fuelTicks(stack) == 0;
			case FUEL_SLOT -> fuelTicks(stack) > 0;
			default -> false;
		};
	}

	@Override
	public int[] getSlotsForFace(Direction direction) {
		return direction == Direction.DOWN ? BOTTOM_SLOTS : direction == Direction.UP ? TOP_SLOTS : SIDE_SLOTS;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
		// From the top, fill the first input before the second, one item at a time.
		if (direction == Direction.UP && slot == SECOND_SLOT && items.get(FIRST_SLOT).isEmpty()) {
			return false;
		}
		return canPlaceItem(slot, stack) && (slot == FUEL_SLOT || items.get(slot).isEmpty());
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
		return direction == Direction.DOWN && slot == OUTPUT_SLOT;
	}
}
