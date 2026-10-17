package com.jheinz.tomyen.isle;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

/**
 * Finds a safe place for a gravestone near where someone died and puts it up there.
 *
 * <p>The stone needs solid ground under it and room to stand in front of it, out of lava and fire. Looking outward
 * ring by ring from the death spot, each column is searched downward from just above it: a fall or a death in mid-air
 * finds the ground below, a death in a lava lake finds its shore, a cave death stays in the cave. Below the world
 * (the void) the whole column is searched from the top, which finds the edge of the island they fell off. The Nether's
 * roof is out of bounds. Dry ground is preferred; failing that the sea floor will do (the stone is waterlogged).
 *
 * <p>If nothing near will do (the End's void far from any island, say) the stone goes up near the End's central
 * island, or failing that at the world spawn: always somewhere the owner can reach.
 */
public final class GravestonePlacement {
	/** How far round the death spot to look, block by block; then out to {@link #FAR_RADIUS} more coarsely. */
	private static final int NEAR_RADIUS = 20;
	private static final int FAR_RADIUS = 96;
	private static final int FAR_STEP = 4;
	/** How far above the death spot a column's search starts. */
	private static final int HEADROOM = 4;

	private GravestonePlacement() {
	}

	/** Puts up a gravestone for the owner near {@code death}, facing {@code facing}; returns where it went. */
	public static GlobalPos place(MinecraftServer server, ServerLevel level, BlockPos death, Direction facing, UUID owner, String ownerName) {
		ServerLevel target = level;
		BlockPos spot = find(level, death);
		if (spot == null && level.dimension() == Level.END) {
			spot = findLoading(level, new BlockPos(0, level.getMaxY(), 0));
		}
		if (spot == null) {
			GlobalPos spawn = server.getRespawnData().globalPos();
			target = server.getLevel(spawn.dimension());
			if (target == null) {
				target = server.overworld();
			}
			target.getChunk(spawn.pos());
			BlockPos surface = target.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.pos());
			spot = findLoading(target, surface);
			if (spot == null) {
				// Nothing at all to stand on even at spawn: a little plinth of stone to put it on.
				spot = surface;
				target.setBlock(spot.below(), Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		boolean wet = target.getFluidState(spot).is(FluidTags.WATER);
		target.setBlock(spot, Isle.GRAVESTONE.defaultBlockState().setValue(GravestoneBlock.FACING, facing)
				.setValue(GravestoneBlock.WATERLOGGED, wet), Block.UPDATE_ALL);
		if (target.getBlockEntity(spot) instanceof GravestoneBlockEntity grave) {
			grave.setOwner(owner, ownerName);
		}
		return GlobalPos.of(target.dimension(), spot);
	}

	/** The nearest safe spot to {@code death}, dry if possible; null if there's none in reach (of loaded chunks). */
	public static @Nullable BlockPos find(ServerLevel level, BlockPos death) {
		return find(level, death, false, FAR_RADIUS);
	}

	/**
	 * For the fallbacks, which are usually far from anyone: the nearest safe spot within {@link #NEAR_RADIUS}, loading
	 * (or generating) the few chunks that takes.
	 */
	private static @Nullable BlockPos findLoading(ServerLevel level, BlockPos at) {
		return find(level, at, true, NEAR_RADIUS);
	}

	private static @Nullable BlockPos find(ServerLevel level, BlockPos death, boolean load, int far) {
		int minY = level.getMinY() + 1;
		int maxY = level.dimensionType().hasCeiling()
				? Math.min(level.getMinY() + level.dimensionType().logicalHeight() - 2, level.getMaxY() - 2)
				: level.getMaxY() - 2;
		boolean belowWorld = death.getY() < minY;
		int top = belowWorld ? maxY : Math.min(maxY, Math.max(minY, death.getY() + HEADROOM));
		// Distances are measured from where they died, or for the void from the bottom of the world above it.
		BlockPos from = belowWorld ? new BlockPos(death.getX(), minY, death.getZ()) : death;
		for (boolean wet : new boolean[] {false, true}) {
			// Ring by ring outward, until no ring further out could hold anything nearer than the best found (a spot
			// straight below can be a cave far down, when the surface a step aside is much nearer).
			BlockPos best = null;
			double bestDistance = Double.MAX_VALUE;
			for (int r = 0; r <= NEAR_RADIUS && (double) r * r < bestDistance; r++) {
				BlockPos found = ring(level, from, r, 1, top, minY, wet, load);
				if (found != null && found.distSqr(from) < bestDistance) {
					best = found;
					bestDistance = found.distSqr(from);
				}
			}
			for (int r = NEAR_RADIUS + FAR_STEP; best == null && r <= far; r += FAR_STEP) {
				best = ring(level, from, r, FAR_STEP, maxY, minY, wet, load);
			}
			if (best != null) {
				return best;
			}
		}
		return null;
	}

	/** The nearest safe spot on the square ring {@code r} out from the centre, sampling every {@code step} blocks. */
	private static @Nullable BlockPos ring(ServerLevel level, BlockPos centre, int r, int step, int top, int bottom, boolean wet,
			boolean load) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int dx = -r; dx <= r; dx += step) {
			for (int dz = -r; dz <= r; dz += step) {
				if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
					continue;
				}
				int x = centre.getX() + dx, z = centre.getZ() + dz;
				if (load) {
					level.getChunk(x >> 4, z >> 4);
				} else if (!level.hasChunkAt(new BlockPos(x, centre.getY(), z))) {
					continue;
				}
				BlockPos found = column(level, x, z, top, bottom, wet);
				if (found != null) {
					double distance = found.distSqr(centre);
					if (distance < bestDistance) {
						best = found;
						bestDistance = distance;
					}
				}
			}
		}
		return best;
	}

	private static @Nullable BlockPos column(ServerLevel level, int x, int z, int top, int bottom, boolean wet) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, top, z);
		BlockState above = level.getBlockState(pos.above());
		for (int y = top; y >= bottom; y--) {
			pos.setY(y);
			BlockState here = level.getBlockState(pos);
			if (fits(level, pos, here, above, wet)) {
				return pos.immutable();
			}
			above = here;
		}
		return null;
	}

	private static boolean fits(ServerLevel level, BlockPos pos, BlockState here, BlockState above, boolean wet) {
		if (!roomFor(here, wet) || !roomFor(above, true) || above.getFluidState().is(FluidTags.LAVA)) {
			return false;
		}
		BlockPos belowPos = pos.below();
		BlockState below = level.getBlockState(belowPos);
		return below.isFaceSturdy(level, belowPos, Direction.UP) && below.getFluidState().isEmpty() && !below.is(Blocks.MAGMA_BLOCK)
				&& !below.is(BlockTags.CAMPFIRES) && !below.is(Blocks.BEDROCK) && !below.is(Isle.GRAVESTONE);
	}

	/** Air, or something a stone can push aside (grass, snow), or water if allowed: never lava or fire. */
	private static boolean roomFor(BlockState state, boolean waterOk) {
		FluidState fluid = state.getFluidState();
		if (fluid.is(FluidTags.LAVA) || state.is(BlockTags.FIRE)) {
			return false;
		}
		if (!fluid.isEmpty()) {
			return waterOk && fluid.is(FluidTags.WATER) && fluid.isSource() && (state.is(Blocks.WATER) || state.canBeReplaced());
		}
		return state.isAir() || (state.canBeReplaced() && !state.is(Blocks.LAVA));
	}
}
