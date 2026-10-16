package com.jheinz.tomyen.industry;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The Metal Press's bed and tray: ingots waiting on the bed, and the plates it's stamped. A stroke of the ram takes
 * {@link #STROKE_TICKS}: it falls for the first half, stamps one ingot as it lands, and rises again; pulses
 * during a stroke are ignored, so a fast clock presses one ingot every 0.8 s at best. Clients are told when a
 * stroke starts so they can draw the ram, and are sent the items so they can draw what's on the bed. Hoppers load
 * ingots from the top and sides and take plates from below.
 */
public class MetalPressBlockEntity extends BlockEntity implements WorldlyContainer {
	public static final int INPUT_SLOT = 0;
	public static final int OUTPUT_SLOT = 1;
	public static final int STROKE_TICKS = 16;
	/** The tick of the stroke the ram meets the bed. */
	public static final int IMPACT_TICK = STROKE_TICKS / 2;
	private static final int STROKE_EVENT = 1;
	/** What it presses: one ingot, one plate. */
	private static final Map<Item, Item> PRESSINGS = Map.of(
			Industry.STEEL_INGOT, Industry.STEEL_PLATE,
			Industry.DURALUMIN_INGOT, Industry.DURALUMIN_SHEET);
	private static final int[] INPUT_SLOTS = {INPUT_SLOT};
	private static final int[] OUTPUT_SLOTS = {OUTPUT_SLOT};

	private NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
	/** Ticks into the current stroke, or 0 when the ram is up. */
	private int stroke;

	public MetalPressBlockEntity(BlockPos pos, BlockState state) {
		super(Industry.METAL_PRESS_ENTITY, pos, state);
	}

	public static boolean isPressable(ItemStack stack) {
		return PRESSINGS.containsKey(stack.getItem());
	}

	public static @Nullable Item pressed(ItemStack stack) {
		return PRESSINGS.get(stack.getItem());
	}

	public static void tick(Level level, BlockPos pos, BlockState state, MetalPressBlockEntity press) {
		if (press.stroke == 0) {
			return;
		}
		if (++press.stroke == IMPACT_TICK && level instanceof ServerLevel server) {
			press.stamp(server);
		}
		if (press.stroke >= STROKE_TICKS) {
			press.stroke = 0;
		}
	}

	/** A redstone pulse: brings the ram down, if it's up. */
	public void startStroke() {
		if (stroke == 0 && level != null) {
			stroke = 1;
			level.blockEvent(worldPosition, getBlockState().getBlock(), STROKE_EVENT, 0);
			level.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.6f, 0.6f);
		}
	}

	@Override
	public boolean triggerEvent(int b0, int b1) {
		if (b0 == STROKE_EVENT) {
			stroke = 1;
			return true;
		}
		return super.triggerEvent(b0, b1);
	}

	/** The ram lands: one ingot becomes a plate, if there's room in the tray. An empty bed just clangs. */
	private void stamp(ServerLevel level) {
		ItemStack input = items.get(INPUT_SLOT);
		Item result = pressed(input);
		ItemStack output = items.get(OUTPUT_SLOT);
		double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 0.45, z = worldPosition.getZ() + 0.5;
		if (result == null || !output.isEmpty() && (!output.is(result) || output.getCount() >= output.getMaxStackSize())) {
			level.playSound(null, worldPosition, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.4f, 1.6f);
			return;
		}
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, input.getItem()), x, y, z, 10, 0.2, 0.05, 0.2, 0.08);
		level.sendParticles(ParticleTypes.CRIT, x, y, z, 6, 0.2, 0.05, 0.2, 0.3);
		input.shrink(1);
		if (output.isEmpty()) {
			items.set(OUTPUT_SLOT, new ItemStack(result));
		} else {
			output.grow(1);
		}
		level.playSound(null, worldPosition, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.7f, 0.8f + level.getRandom().nextFloat() * 0.1f);
		setChanged();
	}

	/** How far down the ram is, 0 (up) to 1 (on the bed): a fast drop and a slower lift. */
	public float ramDrop(float partialTicks) {
		if (stroke == 0) {
			return 0.0f;
		}
		float t = Math.min(stroke + partialTicks, STROKE_TICKS);
		if (t < IMPACT_TICK) {
			float fall = t / IMPACT_TICK;
			return fall * fall;
		}
		return 1.0f - Mth.clamp((t - IMPACT_TICK) / (STROKE_TICKS - IMPACT_TICK), 0.0f, 1.0f);
	}

	/** Puts as much of a stack of ingots on the bed as fits. False if none did. */
	public boolean load(ItemStack stack) {
		ItemStack input = items.get(INPUT_SLOT);
		if (!input.isEmpty() && !ItemStack.isSameItemSameComponents(input, stack)) {
			return false;
		}
		int moved = Math.min(stack.getCount(), stack.getMaxStackSize() - input.getCount());
		if (moved <= 0) {
			return false;
		}
		if (input.isEmpty()) {
			items.set(INPUT_SLOT, stack.copyWithCount(moved));
		} else {
			input.grow(moved);
		}
		stack.shrink(moved);
		if (level != null) {
			level.playSound(null, worldPosition, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8f, 1.2f);
		}
		setChanged();
		return true;
	}

	/** Hands the plates over, or the ingots if there are no plates. */
	public void unload(Player player) {
		int slot = items.get(OUTPUT_SLOT).isEmpty() ? INPUT_SLOT : OUTPUT_SLOT;
		ItemStack stack = items.get(slot);
		items.set(slot, ItemStack.EMPTY);
		player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
		setChanged();
	}

	public ItemStack bed() {
		return items.get(INPUT_SLOT);
	}

	public ItemStack tray() {
		return items.get(OUTPUT_SLOT);
	}

	@Override
	public void setChanged() {
		super.setChanged();
		if (level != null && !level.isClientSide()) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items = NonNullList.withSize(2, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, items);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, items, true);
	}

	// --- Container, for hoppers and comparators ---------------------------------------------------------------

	@Override
	public int getContainerSize() {
		return items.size();
	}

	@Override
	public boolean isEmpty() {
		return items.stream().allMatch(ItemStack::isEmpty);
	}

	@Override
	public ItemStack getItem(int slot) {
		return items.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		ItemStack taken = ContainerHelper.removeItem(items, slot, count);
		if (!taken.isEmpty()) {
			setChanged();
		}
		return taken;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return ContainerHelper.takeItem(items, slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		items.set(slot, stack);
		setChanged();
	}

	@Override
	public boolean stillValid(Player player) {
		return player.position().closerThan(Vec3.atCenterOf(worldPosition), 8.0);
	}

	@Override
	public void clearContent() {
		items.clear();
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return slot == INPUT_SLOT && isPressable(stack);
	}

	@Override
	public int[] getSlotsForFace(Direction direction) {
		return direction == Direction.DOWN ? OUTPUT_SLOTS : INPUT_SLOTS;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
		return canPlaceItem(slot, stack) && (items.get(INPUT_SLOT).isEmpty() || ItemStack.isSameItemSameComponents(items.get(INPUT_SLOT), stack));
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
		return slot == OUTPUT_SLOT;
	}
}
