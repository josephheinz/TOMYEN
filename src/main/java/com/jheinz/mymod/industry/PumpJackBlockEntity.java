package com.jheinz.mymod.industry;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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

/**
 * A Pump Jack's works: empty canisters in, crude oil canisters out, one every {@link #PUMP_TICKS} while it stands
 * on a live deposit (see {@link OilDeposits}). It needs no fuel; it's slow instead. Each canister drains one
 * block of oil from the pocket. Hoppers feed empties in from the top and sides and take full ones from below.
 */
public class PumpJackBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
	public static final int INPUT_SLOT = 0;
	public static final int OUTPUT_SLOT = 1;
	/** Twenty seconds a canister. */
	public static final int PUMP_TICKS = 400;
	/** How often it looks for its seep again when it has none. */
	private static final int SURVEY_TICKS = 40;

	// Status codes, shown by the screen.
	public static final int STATUS_NO_SEEP = 0;
	public static final int STATUS_DRY = 1;
	public static final int STATUS_IDLE = 2;
	public static final int STATUS_PUMPING = 3;

	// ContainerData indices, shared with the menu.
	public static final int DATA_PROGRESS = 0;
	public static final int DATA_STATUS = 1;
	public static final int DATA_COUNT = 2;

	private static final int[] OUTPUT_SLOTS = {OUTPUT_SLOT};
	private static final int[] INPUT_SLOTS = {INPUT_SLOT};
	private static final Component TITLE = Component.translatable("container.mymod.pump_jack");

	private NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
	private int progress;
	private int status = STATUS_NO_SEEP;
	private int survey;
	/** The block of oil it's drawing on, until it's pumped. */
	private @Nullable BlockPos oil;
	/** Client: the crank's angle in radians, now and last tick, for the renderer. */
	private float crank;
	private float crankO;

	private final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			return index == DATA_PROGRESS ? progress : index == DATA_STATUS ? status : 0;
		}

		@Override
		public void set(int index, int value) {
			if (index == DATA_PROGRESS) {
				progress = value;
			} else if (index == DATA_STATUS) {
				status = value;
			}
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	public PumpJackBlockEntity(BlockPos pos, BlockState state) {
		super(Industry.PUMP_JACK_ENTITY, pos, state);
	}

	/** Turns the crank while it's pumping: a stroke every three seconds. */
	public static void clientTick(Level level, BlockPos pos, BlockState state, PumpJackBlockEntity jack) {
		jack.crankO = jack.crank;
		if (state.getValue(PumpJackBlock.ACTIVE)) {
			jack.crank += Mth.TWO_PI / 60.0f;
		}
	}

	public float crank(float partialTicks) {
		return Mth.lerp(partialTicks, crankO, crank);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, PumpJackBlockEntity jack) {
		if (jack.oil != null && !OilDeposits.isOilSource(level.getFluidState(jack.oil))) {
			// Someone else got to that block first: look again straight away.
			jack.oil = null;
			jack.survey = 0;
		}
		if (--jack.survey <= 0) {
			BlockPos seep = OilDeposits.findSeep(level, pos);
			if (seep == null) {
				jack.oil = null;
			} else if (jack.oil == null) {
				jack.oil = OilDeposits.findOil(level, seep);
			}
			jack.status = seep == null ? STATUS_NO_SEEP : jack.oil == null ? STATUS_DRY : STATUS_IDLE;
			// A dry well is surveyed less often: it's a long way down.
			jack.survey = jack.status == STATUS_DRY ? SURVEY_TICKS * 5 : SURVEY_TICKS;
		}

		boolean pumping = jack.oil != null && jack.items.get(INPUT_SLOT).is(Industry.EMPTY_CANISTER) && jack.hasRoom();
		if (jack.oil != null) {
			jack.status = pumping ? STATUS_PUMPING : STATUS_IDLE;
		}
		if (pumping && ++jack.progress >= PUMP_TICKS) {
			jack.progress = 0;
			OilDeposits.drain(level, jack.oil);
			jack.oil = null;
			jack.survey = 0;
			jack.items.get(INPUT_SLOT).shrink(1);
			ItemStack output = jack.items.get(OUTPUT_SLOT);
			if (output.isEmpty()) {
				jack.items.set(OUTPUT_SLOT, new ItemStack(Industry.CRUDE_OIL_CANISTER));
			} else {
				output.grow(1);
			}
			level.playSound(null, pos, SoundEvents.BUCKET_FILL_LAVA, SoundSource.BLOCKS, 0.8f, 0.7f);
			setChanged(level, pos, state);
		} else if (!pumping && jack.progress > 0) {
			jack.progress = 0;
			setChanged(level, pos, state);
		}

		if (state.getValue(PumpJackBlock.ACTIVE) != pumping) {
			level.setBlock(pos, state.setValue(PumpJackBlock.ACTIVE, pumping), Block.UPDATE_ALL);
		}
	}

	private boolean hasRoom() {
		ItemStack output = items.get(OUTPUT_SLOT);
		return output.isEmpty() || output.is(Industry.CRUDE_OIL_CANISTER) && output.getCount() < output.getMaxStackSize();
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, items);
		progress = input.getIntOr("progress", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
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
		return new PumpJackMenu(containerId, inventory, this, data);
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return slot == INPUT_SLOT && stack.is(Industry.EMPTY_CANISTER);
	}

	@Override
	public int[] getSlotsForFace(Direction direction) {
		return direction == Direction.DOWN ? OUTPUT_SLOTS : INPUT_SLOTS;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
		return canPlaceItem(slot, stack);
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
		return slot == OUTPUT_SLOT;
	}
}
