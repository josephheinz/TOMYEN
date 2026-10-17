package com.jheinz.tomyen.fusion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.fae.Fae;
import com.jheinz.tomyen.fae.Feywild;

/**
 * Telling a fae pond's water from any other. The pond feature leaves no marker of its own, but its banks always carry
 * its flowers ({@link #MARKERS}: Fae Blooms and Fae Mushrooms, a block tag, so more can be added), so:
 * <ul>
 * <li>In the Feywild, water counts if any marker grows within {@value #FEYWILD_REACH} blocks across (and
 * {@value #FEYWILD_DEPTH} up or down). A natural pond is at most about 15 blocks across with three rings of bank
 * round it, so wherever an item lands in one, some bank flower is in reach.
 * <li>Anywhere (the Overworld too), a hand-made pond counts: water with at least {@value #RING_MUSHROOMS} Fae
 * Mushrooms within {@value #RING_REACH} blocks across (from a block below to two above): a small pool ringed with them.
 * </ul>
 * Only checked when items are actually thrown in with Fae Dust, and for the hint shimmer every couple of seconds, so a
 * few thousand block reads at a time is fine.
 */
public final class FaePonds {
	public static final TagKey<Block> MARKERS = TagKey.create(Registries.BLOCK, MyMod.id("fae_pond_markers"));
	private static final int FEYWILD_REACH = 9;
	private static final int FEYWILD_DEPTH = 3;
	private static final int RING_MUSHROOMS = 4;
	private static final int RING_REACH = 3;

	private FaePonds() {
	}

	/** Whether the block at pos is water belonging to a fae pond. */
	public static boolean isPondWater(Level level, BlockPos pos) {
		if (!level.getFluidState(pos).is(FluidTags.WATER)) {
			return false;
		}
		if (Feywild.is(level) && anyMarker(level, pos, FEYWILD_REACH, -FEYWILD_DEPTH, FEYWILD_DEPTH)) {
			return true;
		}
		return countMushrooms(level, pos, RING_REACH) >= RING_MUSHROOMS;
	}

	/** Whether pos (a player's) is within a few blocks of fae pond water: samples the water round it. */
	public static boolean nearPond(Level level, BlockPos pos) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int dy = -3; dy <= 1; dy++) {
			for (int dx = -5; dx <= 5; dx += 2) {
				for (int dz = -5; dz <= 5; dz += 2) {
					at.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
					if (level.isLoaded(at) && level.getFluidState(at).is(FluidTags.WATER)) {
						return isPondWater(level, at.immutable());
					}
				}
			}
		}
		return false;
	}

	private static boolean anyMarker(Level level, BlockPos pos, int reach, int down, int up) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int dy = down; dy <= up; dy++) {
			for (int dx = -reach; dx <= reach; dx++) {
				for (int dz = -reach; dz <= reach; dz++) {
					at.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
					if (level.isLoaded(at) && isMarker(level.getBlockState(at))) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static int countMushrooms(Level level, BlockPos pos, int reach) {
		int count = 0;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int dy = -1; dy <= 2; dy++) {
			for (int dx = -reach; dx <= reach; dx++) {
				for (int dz = -reach; dz <= reach; dz++) {
					at.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
					if (level.isLoaded(at) && level.getBlockState(at).is(Fae.FAE_MUSHROOM)) {
						count++;
					}
				}
			}
		}
		return count;
	}

	private static boolean isMarker(BlockState state) {
		return state.is(MARKERS);
	}
}
