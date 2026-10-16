package com.jheinz.tomyen.gunsmith;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Moving light for the flashlight and the flares, the vanilla way: an invisible light block placed where the light falls and
 * taken away again when it moves on, so the world around it really lights up (mobs see it too). Each owner has at most one;
 * light only ever goes into air or still water, and only a light block is ever taken away, so nothing else is touched.
 */
final class LightSpots {
	private record Spot(ResourceKey<Level> level, BlockPos pos) {
	}

	private static final Map<Object, Spot> SPOTS = new HashMap<>();

	private LightSpots() {
	}

	/** Moves {@code owner}'s light to {@code pos} at {@code brightness} (0-15), or takes it away with a null pos. */
	static void move(ServerLevel level, Object owner, @Nullable BlockPos pos, int brightness) {
		Spot old = SPOTS.get(owner);
		if (pos != null && old != null && old.level() == level.dimension() && old.pos().equals(pos)) {
			return;
		}
		if (old != null) {
			SPOTS.remove(owner);
			ServerLevel oldLevel = level.getServer().getLevel(old.level());
			if (oldLevel != null) {
				clear(oldLevel, old.pos());
			}
		}
		if (pos != null && place(level, pos, brightness)) {
			SPOTS.put(owner, new Spot(level.dimension(), pos.immutable()));
		}
	}

	private static boolean place(ServerLevel level, BlockPos pos, int brightness) {
		if (!level.isLoaded(pos)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		boolean water = state.is(Blocks.WATER) && level.getFluidState(pos).isSource() && level.getFluidState(pos).is(FluidTags.WATER);
		if (!state.isAir() && !water) {
			return false;
		}
		BlockState light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, brightness).setValue(LightBlock.WATERLOGGED, water);
		return level.setBlock(pos, light, Block.UPDATE_CLIENTS);
	}

	private static void clear(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (state.is(Blocks.LIGHT)) {
			level.setBlock(pos, state.getValue(LightBlock.WATERLOGGED) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(),
					Block.UPDATE_CLIENTS);
		}
	}

	/** Takes every light away, e.g. as the server stops. */
	static void clearAll(MinecraftServer server) {
		for (Spot spot : SPOTS.values()) {
			ServerLevel level = server.getLevel(spot.level());
			if (level != null) {
				clear(level, spot.pos());
			}
		}
		SPOTS.clear();
	}
}
