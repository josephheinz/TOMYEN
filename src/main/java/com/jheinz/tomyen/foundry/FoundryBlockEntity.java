package com.jheinz.tomyen.foundry;

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

import com.jheinz.tomyen.ModBlocks;

/**
 * A furnace that runs on lava only. A lava bucket in the fuel slot fills the Foundry with heat (the empty bucket
 * stays in the slot), and heat is only spent while something is smelting, so a half-used bucket's worth waits
 * for the next job. Hoppers feed the inputs from above and lava from the sides, and take output and empty
 * buckets from below.
 * <p>
 * While it burns, the Foundry heats up by {@link #TEMPERATURE_RISE}°C a tick to {@link #MAX_TEMPERATURE}, and cools
 * by {@link #TEMPERATURE_FALL} a tick when it's idle. A recipe with a minimum temperature (aluminium's 1400°C, say)
 * burns heat while the Foundry warms up but only makes progress once it's hot enough. One with a maximum (a quench,
 * like froststeel's) holds the Foundry at that temperature while it works, and waits, unlit, while a hotter Foundry
 * cools down to it.
 */
public class FoundryBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
	public static final int INPUT_COUNT = 2;
	public static final int FUEL_SLOT = 2;
	public static final int OUTPUT_SLOT = 3;
	/** Ticks of smelting one lava bucket powers: twenty silicon. */
	public static final int HEAT_PER_BUCKET = 4000;
	public static final int AMBIENT_TEMPERATURE = 20;
	public static final int MAX_TEMPERATURE = 1600;
	/** Degrees a tick it heats up while burning: from cold to 1400°C in about 17 seconds. */
	public static final int TEMPERATURE_RISE = 4;
	public static final int TEMPERATURE_FALL = 2;

	// ContainerData indices, shared with the menu.
	public static final int DATA_HEAT = 0;
	public static final int DATA_HEAT_TOTAL = 1;
	public static final int DATA_PROGRESS = 2;
	public static final int DATA_PROGRESS_TOTAL = 3;
	public static final int DATA_TEMPERATURE = 4;
	/** The current recipe's minimum temperature, or 0. */
	public static final int DATA_REQUIRED_TEMPERATURE = 5;
	/** The current recipe's maximum temperature, or 0. */
	public static final int DATA_MAX_TEMPERATURE = 6;
	public static final int DATA_COUNT = 7;

	private static final int[] TOP_SLOTS = {0, 1};
	private static final int[] SIDE_SLOTS = {FUEL_SLOT};
	private static final int[] BOTTOM_SLOTS = {OUTPUT_SLOT, FUEL_SLOT};
	private static final Component TITLE = Component.translatable("container.tomyen.foundry");

	private NonNullList<ItemStack> items = NonNullList.withSize(4, ItemStack.EMPTY);
	private int heat;
	private int heatTotal;
	private int progress;
	private int progressTotal;
	private int temperature = AMBIENT_TEMPERATURE;
	private int requiredTemperature;
	private int maxTemperature;

	private final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case DATA_HEAT -> heat;
				case DATA_HEAT_TOTAL -> heatTotal;
				case DATA_PROGRESS -> progress;
				case DATA_PROGRESS_TOTAL -> progressTotal;
				case DATA_TEMPERATURE -> temperature;
				case DATA_REQUIRED_TEMPERATURE -> requiredTemperature;
				case DATA_MAX_TEMPERATURE -> maxTemperature;
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
			switch (index) {
				case DATA_HEAT -> heat = value;
				case DATA_HEAT_TOTAL -> heatTotal = value;
				case DATA_PROGRESS -> progress = value;
				case DATA_PROGRESS_TOTAL -> progressTotal = value;
				case DATA_TEMPERATURE -> temperature = value;
				case DATA_REQUIRED_TEMPERATURE -> requiredTemperature = value;
				case DATA_MAX_TEMPERATURE -> maxTemperature = value;
				default -> {
				}
			}
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	public FoundryBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.FOUNDRY_ENTITY, pos, state);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, FoundryBlockEntity foundry) {
		FoundryRecipe recipe = FoundryRecipes.find(foundry);
		boolean canSmelt = recipe != null && foundry.hasRoomFor(recipe.result(foundry));
		boolean changed = false;

		if (canSmelt && foundry.heat == 0 && foundry.items.get(FUEL_SLOT).is(Items.LAVA_BUCKET)) {
			foundry.heat = foundry.heatTotal = HEAT_PER_BUCKET;
			foundry.items.set(FUEL_SLOT, new ItemStack(Items.BUCKET));
			changed = true;
		}

		// A quench won't start in a Foundry hotter than it allows: the Foundry sits unlit until it has cooled down to it.
		boolean tooHot = canSmelt && recipe.hasMaxTemperature() && foundry.temperature > recipe.maxTemperature();
		boolean smelting = canSmelt && foundry.heat > 0 && !tooHot;
		foundry.requiredTemperature = recipe == null ? 0 : recipe.minTemperature();
		foundry.maxTemperature = recipe == null ? 0 : recipe.maxTemperature();
		int ceiling = recipe != null && recipe.hasMaxTemperature() ? recipe.maxTemperature() : MAX_TEMPERATURE;
		int temperature = smelting ? Math.min(ceiling, foundry.temperature + TEMPERATURE_RISE)
				: Math.max(AMBIENT_TEMPERATURE, foundry.temperature - TEMPERATURE_FALL);
		if (temperature != foundry.temperature) {
			foundry.temperature = temperature;
			changed = true;
		}
		if (smelting && foundry.temperature < recipe.minTemperature()) {
			// Still warming up: the burn goes into the heat.
			foundry.heat--;
			foundry.progressTotal = recipe.cookTicks();
			changed = true;
		} else if (smelting) {
			foundry.heat--;
			foundry.progressTotal = recipe.cookTicks();
			if (++foundry.progress >= foundry.progressTotal) {
				foundry.progress = 0;
				ItemStack result = recipe.result(foundry);
				recipe.consume(foundry);
				ItemStack output = foundry.items.get(OUTPUT_SLOT);
				if (output.isEmpty()) {
					foundry.items.set(OUTPUT_SLOT, result);
				} else {
					output.grow(recipe.resultCount());
				}
			}
			changed = true;
		} else if (foundry.progress > 0) {
			// Without a smeltable input the work is lost; without heat it cools back slowly.
			foundry.progress = canSmelt ? Math.max(0, foundry.progress - 2) : 0;
			changed = true;
		}

		if (state.getValue(FoundryBlock.LIT) != smelting) {
			level.setBlock(pos, state.setValue(FoundryBlock.LIT, smelting), Block.UPDATE_ALL);
		}
		if (changed) {
			setChanged(level, pos, state);
		}
	}

	private boolean hasRoomFor(ItemStack result) {
		ItemStack output = items.get(OUTPUT_SLOT);
		return output.isEmpty() || ItemStack.isSameItemSameComponents(output, result)
				&& output.getCount() + result.getCount() <= output.getMaxStackSize();
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, items);
		heat = input.getIntOr("heat", 0);
		heatTotal = input.getIntOr("heat_total", 0);
		progress = input.getIntOr("progress", 0);
		progressTotal = input.getIntOr("progress_total", 0);
		temperature = input.getIntOr("temperature", AMBIENT_TEMPERATURE);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("heat", heat);
		output.putInt("heat_total", heatTotal);
		output.putInt("progress", progress);
		output.putInt("progress_total", progressTotal);
		output.putInt("temperature", temperature);
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
		return new FoundryMenu(containerId, inventory, this, data);
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return switch (slot) {
			case FUEL_SLOT -> stack.is(Items.LAVA_BUCKET);
			case OUTPUT_SLOT -> false;
			default -> true;
		};
	}

	@Override
	public int[] getSlotsForFace(Direction direction) {
		return direction == Direction.DOWN ? BOTTOM_SLOTS : direction == Direction.UP ? TOP_SLOTS : SIDE_SLOTS;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction direction) {
		return canPlaceItem(slot, stack);
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
		// Only empty buckets come back out of the fuel slot.
		return direction == Direction.DOWN && (slot == OUTPUT_SLOT || stack.is(Items.BUCKET));
	}
}
