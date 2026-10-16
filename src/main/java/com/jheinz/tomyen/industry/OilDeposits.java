package com.jheinz.tomyen.industry;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;

/**
 * Where a Pump Jack draws its oil from. The rule: an Oil Seep must be under the jack (directly, or up to
 * {@link #SEEP_DEPTH} blocks down, so it can stand on a pad), and the oil is whatever crude oil source blocks lie
 * beneath that seep, within {@link #RADIUS} blocks of its column, all the way down.
 * <p>
 * Deposits are finite: each canister pumped drains one source block, the highest first, so a pocket's oil level
 * visibly drops. A typical pocket holds 60 to 300 blocks; when none are left the deposit is spent. Seeps carried
 * off with silk touch and set down elsewhere find nothing beneath them.
 */
public final class OilDeposits {
	public static final int SEEP_DEPTH = 4;
	public static final int RADIUS = 4;

	private OilDeposits() {
	}

	/** The Oil Seep this jack stands on, or null. */
	public static @Nullable BlockPos findSeep(Level level, BlockPos jack) {
		for (int depth = 1; depth <= SEEP_DEPTH; depth++) {
			BlockPos pos = jack.below(depth);
			if (level.getBlockState(pos).is(Industry.OIL_SEEP)) {
				return pos;
			}
		}
		return null;
	}

	/** The highest crude oil source block under the seep, or null if the deposit is dry. */
	public static @Nullable BlockPos findOil(Level level, BlockPos seep) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = seep.getY() - 1; y >= level.getMinY(); y--) {
			for (int dx = -RADIUS; dx <= RADIUS; dx++) {
				for (int dz = -RADIUS; dz <= RADIUS; dz++) {
					pos.set(seep.getX() + dx, y, seep.getZ() + dz);
					if (level.isLoaded(pos) && isOilSource(level.getFluidState(pos))) {
						return pos.immutable();
					}
				}
			}
		}
		return null;
	}

	/** Pumps one block of oil out of the ground. */
	public static void drain(Level level, BlockPos oil) {
		level.setBlockAndUpdate(oil, Blocks.CAVE_AIR.defaultBlockState());
	}

	public static boolean isOilSource(FluidState fluid) {
		return fluid.is(Industry.CRUDE_OIL) && fluid.isSource();
	}
}
