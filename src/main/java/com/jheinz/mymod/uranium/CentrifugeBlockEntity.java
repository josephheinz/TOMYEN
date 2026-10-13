package com.jheinz.mymod.uranium;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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

import com.jheinz.mymod.industry.RefineryBlockEntity;

/**
 * The Centrifuge: spins {@link #INPUT_COST} raw uranium apart into one enriched and {@link #DEPLETED_COUNT} depleted
 * uranium every {@link #SPIN_TICKS} ticks. It burns the Refinery's fuels ({@link RefineryBlockEntity#burnTicks}), and
 * like it only while it's working. Hoppers feed raw uranium from the top and fuel from the sides, and take the
 * products (and empty lava buckets) from below.
 * <p>
 * On the client the rotor spins up while the block is lit and runs down after, humming as it goes.
 */
public class CentrifugeBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
	public static final int INPUT_SLOT = 0;
	public static final int FUEL_SLOT = 1;
	public static final int ENRICHED_SLOT = 2;
	public static final int DEPLETED_SLOT = 3;
	public static final int SIZE = 4;
	public static final int INPUT_COST = 3;
	public static final int DEPLETED_COUNT = 2;
	public static final int SPIN_TICKS = 400;

	// ContainerData indices, shared with the menu.
	public static final int DATA_BURN = 0;
	public static final int DATA_BURN_TOTAL = 1;
	public static final int DATA_PROGRESS = 2;
	public static final int DATA_COUNT = 3;

	/** Rotor speed at full spin, degrees a tick, and how fast it gets there or runs down. */
	private static final float MAX_SPEED = 48.0f;
	private static final float SPIN_UP = 1.2f;
	private static final float SPIN_DOWN = 0.6f;
	/** The hum sample is a second and a half long; it's restarted a little before it ends. */
	private static final int HUM_TICKS = 28;

	private static final int[] TOP_SLOTS = {INPUT_SLOT};
	private static final int[] SIDE_SLOTS = {FUEL_SLOT};
	private static final int[] BOTTOM_SLOTS = {ENRICHED_SLOT, DEPLETED_SLOT, FUEL_SLOT};
	private static final Component TITLE = Component.translatable("container.mymod.centrifuge");

	private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
	private int burn;
	private int burnTotal;
	private int progress;
	// Client side only.
	private float rotor;
	private float rotorO;
	private float speed;
	private int humTicks;

	private final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case DATA_BURN -> burn;
				case DATA_BURN_TOTAL -> burnTotal;
				case DATA_PROGRESS -> progress;
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
			switch (index) {
				case DATA_BURN -> burn = value;
				case DATA_BURN_TOTAL -> burnTotal = value;
				case DATA_PROGRESS -> progress = value;
				default -> {
				}
			}
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	public CentrifugeBlockEntity(BlockPos pos, BlockState state) {
		super(Uranium.CENTRIFUGE_ENTITY, pos, state);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, CentrifugeBlockEntity centrifuge) {
		boolean canSpin = centrifuge.items.get(INPUT_SLOT).is(Uranium.RAW_URANIUM) && centrifuge.items.get(INPUT_SLOT).getCount() >= INPUT_COST
				&& centrifuge.fits(ENRICHED_SLOT, new ItemStack(Uranium.ENRICHED_URANIUM))
				&& centrifuge.fits(DEPLETED_SLOT, new ItemStack(Uranium.DEPLETED_URANIUM, DEPLETED_COUNT));
		boolean changed = false;

		if (canSpin && centrifuge.burn == 0) {
			ItemStack fuel = centrifuge.items.get(FUEL_SLOT);
			int ticks = RefineryBlockEntity.burnTicks(fuel);
			if (ticks > 0) {
				centrifuge.burn = centrifuge.burnTotal = ticks;
				if (fuel.is(Items.LAVA_BUCKET)) {
					centrifuge.items.set(FUEL_SLOT, new ItemStack(Items.BUCKET));
				} else {
					fuel.shrink(1);
				}
				changed = true;
			}
		}

		boolean working = canSpin && centrifuge.burn > 0;
		if (working) {
			centrifuge.burn--;
			if (++centrifuge.progress >= SPIN_TICKS) {
				centrifuge.progress = 0;
				centrifuge.items.get(INPUT_SLOT).shrink(INPUT_COST);
				centrifuge.put(ENRICHED_SLOT, new ItemStack(Uranium.ENRICHED_URANIUM));
				centrifuge.put(DEPLETED_SLOT, new ItemStack(Uranium.DEPLETED_URANIUM, DEPLETED_COUNT));
			}
			changed = true;
		} else if (centrifuge.progress > 0) {
			// Lose the batch if the uranium is taken out; let it run down slowly without fuel.
			centrifuge.progress = canSpin ? Math.max(0, centrifuge.progress - 2) : 0;
			changed = true;
		}

		if (state.getValue(CentrifugeBlock.LIT) != working) {
			level.setBlock(pos, state.setValue(CentrifugeBlock.LIT, working), Block.UPDATE_ALL);
		}
		if (changed) {
			setChanged(level, pos, state);
		}
	}

	public static void clientTick(Level level, BlockPos pos, BlockState state, CentrifugeBlockEntity centrifuge) {
		boolean lit = state.getValue(CentrifugeBlock.LIT);
		centrifuge.speed = lit ? Math.min(MAX_SPEED, centrifuge.speed + SPIN_UP) : Math.max(0.0f, centrifuge.speed - SPIN_DOWN);
		centrifuge.rotorO = centrifuge.rotor;
		centrifuge.rotor = (centrifuge.rotor + centrifuge.speed) % 360.0f;
		if (centrifuge.rotor < centrifuge.rotorO) {
			centrifuge.rotorO -= 360.0f;
		}
		float spin = centrifuge.speed / MAX_SPEED;
		if (spin > 0.2f && --centrifuge.humTicks <= 0) {
			centrifuge.humTicks = HUM_TICKS;
			level.playLocalSound(pos, Uranium.CENTRIFUGE_HUM, SoundSource.BLOCKS, 0.25f + 0.35f * spin, 0.6f + 0.6f * spin, false);
		}
	}

	/** The rotor's angle in degrees, for the renderer. */
	public float rotor(float partialTicks) {
		return Mth.lerp(partialTicks, rotorO, rotor);
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

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, items);
		burn = input.getIntOr("burn", 0);
		burnTotal = input.getIntOr("burn_total", 0);
		progress = input.getIntOr("progress", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("burn", burn);
		output.putInt("burn_total", burnTotal);
		output.putInt("progress", progress);
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
		return new CentrifugeMenu(containerId, inventory, this, data);
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return switch (slot) {
			case INPUT_SLOT -> stack.is(Uranium.RAW_URANIUM);
			case FUEL_SLOT -> RefineryBlockEntity.burnTicks(stack) > 0;
			default -> false;
		};
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
