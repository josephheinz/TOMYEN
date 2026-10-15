package com.jheinz.mymod.starforge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
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

import com.jheinz.mymod.meteorite.Meteorites;

/**
 * Infuses one piece of gear at a time: gear in the input, a catalyst beside it and star crystal shards as starlight.
 * Over {@link #INFUSE_TICKS} ticks the catalyst's {@link Infusion} goes into the gear, which comes out in the output.
 * <p>
 * The night-sky rule: it works only by night (the sky dark enough that monsters walk), under open sky (full sky light
 * on the block above it, so glass lets starlight through but stone doesn't), in a dimension with a sky and a day
 * (not the Nether or the End). Rain clouds hide half the stars: in rain it works at half speed. Anywhere else it's
 * dormant: the work waits, and resumes when night falls again. Each shard is {@link #STARLIGHT_PER_SHARD} ticks of
 * starlight, spent only while it works, so an infusion takes four.
 * <p>
 * Hoppers feed gear and catalysts from above and shards from the sides, and take the infused gear from below.
 */
public class StarForgeBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
	public static final int INPUT_SLOT = 0;
	public static final int CATALYST_SLOT = 1;
	public static final int FUEL_SLOT = 2;
	public static final int OUTPUT_SLOT = 3;
	public static final int INFUSE_TICKS = 160;
	public static final int STARLIGHT_PER_SHARD = 40;
	private static final int SKY_CHECK_INTERVAL = 20;

	/** How the sky over the forge is: whether it works, and if not, why not. */
	public enum Sky {
		DAY(false), ROOFED(false), NO_SKY(false), CLEAR(true), CLOUDED(true);

		public final boolean works;

		Sky(boolean works) {
			this.works = works;
		}
	}

	// ContainerData indices, shared with the menu.
	public static final int DATA_PROGRESS = 0;
	public static final int DATA_STARLIGHT = 1;
	public static final int DATA_SKY = 2;
	public static final int DATA_COUNT = 3;

	private static final int[] TOP_SLOTS = {INPUT_SLOT, CATALYST_SLOT};
	private static final int[] SIDE_SLOTS = {FUEL_SLOT};
	private static final int[] BOTTOM_SLOTS = {OUTPUT_SLOT};
	private static final Component TITLE = Component.translatable("container.mymod.star_forge");

	private NonNullList<ItemStack> items = NonNullList.withSize(4, ItemStack.EMPTY);
	private int progress;
	private int starlight;
	private Sky sky = Sky.DAY;
	private boolean skyChecked;

	private final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case DATA_PROGRESS -> progress;
				case DATA_STARLIGHT -> starlight;
				case DATA_SKY -> sky.ordinal();
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
			switch (index) {
				case DATA_PROGRESS -> progress = value;
				case DATA_STARLIGHT -> starlight = value;
				case DATA_SKY -> sky = Sky.values()[Math.floorMod(value, Sky.values().length)];
				default -> {
				}
			}
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	public StarForgeBlockEntity(BlockPos pos, BlockState state) {
		super(StarForge.STAR_FORGE_ENTITY, pos, state);
	}

	/** The sky over a forge at this spot. */
	public static Sky skyAt(Level level, BlockPos pos) {
		if (!level.dimensionType().hasSkyLight() || level.dimensionType().hasFixedTime()) {
			return Sky.NO_SKY;
		}
		if (!level.isDarkOutside()) {
			return Sky.DAY;
		}
		if (!level.canSeeSky(pos.above())) {
			return Sky.ROOFED;
		}
		return level.isRaining() ? Sky.CLOUDED : Sky.CLEAR;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, StarForgeBlockEntity forge) {
		boolean changed = false;
		if (!forge.skyChecked || level.getGameTime() % SKY_CHECK_INTERVAL == 0) {
			forge.sky = skyAt(level, pos);
			forge.skyChecked = true;
		}

		Infusing job = Infusing.of(forge.items.get(INPUT_SLOT), forge.items.get(CATALYST_SLOT));
		boolean canWork = job.ready() && forge.items.get(OUTPUT_SLOT).isEmpty();
		boolean working = false;
		if (canWork && forge.sky.works) {
			ItemStack fuel = forge.items.get(FUEL_SLOT);
			if (forge.starlight == 0 && fuel.is(Meteorites.STAR_CRYSTAL_SHARD)) {
				fuel.shrink(1);
				forge.starlight = STARLIGHT_PER_SHARD;
				changed = true;
			}
			if (forge.starlight > 0) {
				working = true;
				// Under clouds, every other tick.
				if (forge.sky == Sky.CLEAR || level.getGameTime() % 2 == 0) {
					forge.starlight--;
					forge.progress++;
					changed = true;
				}
				if (forge.progress >= INFUSE_TICKS) {
					forge.finish((ServerLevel) level, pos, job.infusion());
				}
			}
		} else if (!canWork && forge.progress > 0) {
			// Take the gear or catalyst away and the work is lost; a dormant sky only pauses it.
			forge.progress = 0;
			changed = true;
		}

		BlockState updated = state.setValue(StarForgeBlock.STARLIT, forge.sky.works).setValue(StarForgeBlock.FORGING, working);
		if (updated != state) {
			level.setBlock(pos, updated, Block.UPDATE_ALL);
			state = updated;
		}
		if (changed) {
			setChanged(level, pos, state);
		}
	}

	private void finish(ServerLevel level, BlockPos pos, Infusion infusion) {
		progress = 0;
		ItemStack result = InfusionEffects.infuse(items.get(INPUT_SLOT).copy(), infusion);
		items.set(INPUT_SLOT, ItemStack.EMPTY);
		items.get(CATALYST_SLOT).shrink(1);
		items.set(OUTPUT_SLOT, result);
		double x = pos.getX() + 0.5, y = pos.getY() + 0.9, z = pos.getZ() + 0.5;
		level.sendParticles(ParticleTypes.END_ROD, x, y, z, 24, 0.2, 0.3, 0.2, 0.06);
		level.sendParticles(ParticleTypes.GLOW, x, y, z, 12, 0.3, 0.2, 0.3, 0.0);
		level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0f, 1.2f);
		level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.6f, 1.6f);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, items);
		progress = input.getIntOr("progress", 0);
		starlight = input.getIntOr("starlight", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("progress", progress);
		output.putInt("starlight", starlight);
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
		return new StarForgeMenu(containerId, inventory, this, data);
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return switch (slot) {
			case INPUT_SLOT -> !InfusionKind.of(stack.copyWithCount(1)).isEmpty() && items.get(INPUT_SLOT).isEmpty();
			case CATALYST_SLOT -> Infusion.byCatalyst(stack) != null;
			case FUEL_SLOT -> stack.is(Meteorites.STAR_CRYSTAL_SHARD);
			default -> false;
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
		return direction == Direction.DOWN && slot == OUTPUT_SLOT;
	}
}
