package com.jheinz.mymod.meteorite;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AmethystBlock;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BuddingAmethystBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * Budding Star Crystal grows star crystal the way budding amethyst grows amethyst: on one random tick in
 * {@link BuddingAmethystBlock#GROWTH_CHANCE} it picks a side, and starts a small bud in the air (or water) there or
 * moves the bud already on that side up a stage, small to medium to large to a full cluster. It drops nothing, even
 * with silk touch, and can't be pushed.
 */
public class BuddingStarCrystalBlock extends AmethystBlock {
	private static final Direction[] DIRECTIONS = Direction.values();

	public BuddingStarCrystalBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (random.nextInt(BuddingAmethystBlock.GROWTH_CHANCE) != 0) {
			return;
		}
		Direction direction = DIRECTIONS[random.nextInt(DIRECTIONS.length)];
		BlockPos growPos = pos.relative(direction);
		BlockState there = level.getBlockState(growPos);
		Block next = null;
		if (BuddingAmethystBlock.canClusterGrowAtState(there)) {
			next = Meteorites.SMALL_STAR_CRYSTAL_BUD;
		} else if (there.getBlock() instanceof AmethystClusterBlock && there.getValue(AmethystClusterBlock.FACING) == direction) {
			next = nextStage(there.getBlock());
		}
		if (next != null) {
			level.setBlockAndUpdate(growPos, next.defaultBlockState().setValue(AmethystClusterBlock.FACING, direction)
					.setValue(AmethystClusterBlock.WATERLOGGED, there.getFluidState().is(Fluids.WATER)));
		}
	}

	/** The stage after this one, or null for a full cluster (or something that isn't star crystal). */
	public static Block nextStage(Block stage) {
		if (stage == Meteorites.SMALL_STAR_CRYSTAL_BUD) {
			return Meteorites.MEDIUM_STAR_CRYSTAL_BUD;
		}
		if (stage == Meteorites.MEDIUM_STAR_CRYSTAL_BUD) {
			return Meteorites.LARGE_STAR_CRYSTAL_BUD;
		}
		if (stage == Meteorites.LARGE_STAR_CRYSTAL_BUD) {
			return Meteorites.STAR_CRYSTAL_CLUSTER;
		}
		return null;
	}
}
