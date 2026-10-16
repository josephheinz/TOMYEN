package com.jheinz.mymod.herbalism;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** What's steeping in a kettle, and how far along it is (the water and the stage are in the block state). */
public class KettleBlockEntity extends BlockEntity {
	/** How long an herb steeps over the heat: fifteen seconds. */
	public static final int STEEP_TICKS = 300;

	private @Nullable Item herb;
	private int progress;

	public KettleBlockEntity(BlockPos pos, BlockState state) {
		super(Herbalism.KETTLE_ENTITY, pos, state);
	}

	public @Nullable Item herb() {
		return herb;
	}

	/** Drops an herb in to steep. */
	void steep(Item herb) {
		this.herb = herb;
		progress = 0;
		setChanged();
	}

	/** Empty again: the last cup poured. */
	void clear() {
		herb = null;
		progress = 0;
		setChanged();
	}

	/** How far the steeping has got, 0 to 1. */
	float progress() {
		return Math.min(1.0f, progress / (float) STEEP_TICKS);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, KettleBlockEntity kettle) {
		if (state.getValue(KettleBlock.TEA) != KettleBlock.Tea.STEEPING || !KettleBlock.isHeated(level, pos)) {
			return;
		}
		if (++kettle.progress >= STEEP_TICKS) {
			level.setBlock(pos, state.setValue(KettleBlock.TEA, KettleBlock.Tea.READY), Block.UPDATE_ALL);
			level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.8f, 1.3f);
			level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.3f, 1.8f);
		}
		kettle.setChanged();
	}

	/** Broken mid-steep, the herb comes back out. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level instanceof ServerLevel && herb != null && state.getValue(KettleBlock.TEA) == KettleBlock.Tea.STEEPING) {
			Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new ItemStack(herb));
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		herb = input.read("herb", BuiltInRegistries.ITEM.byNameCodec()).orElse(null);
		progress = input.getIntOr("progress", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.storeNullable("herb", BuiltInRegistries.ITEM.byNameCodec(), herb);
		output.putInt("progress", progress);
	}
}
