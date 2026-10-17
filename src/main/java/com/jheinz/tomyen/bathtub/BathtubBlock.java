package com.jheinz.tomyen.bathtub;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A clawfoot bathtub: two blocks long like a bed, its faucet end (the head) the way the placer faced. Breaking either
 * half takes the whole tub, and only the head drops it. Purely decorative; its looks are cut from the Bathtub Boat's
 * texture (tools/gen_bathtub_boat.py).
 */
public class BathtubBlock extends HorizontalDirectionalBlock {
	public static final EnumProperty<BedPart> PART = BlockStateProperties.BED_PART;
	/** The tub up to its rim. */
	private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 15.0, 16.0);

	public BathtubBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, BedPart.FOOT));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, PART);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	/** From one half, the way to the other. */
	private static Direction toOtherHalf(BedPart part, Direction facing) {
		return part == BedPart.FOOT ? facing : facing.getOpposite();
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction facing = context.getHorizontalDirection();
		BlockPos head = context.getClickedPos().relative(facing);
		Level level = context.getLevel();
		return level.getBlockState(head).canBeReplaced(context) && level.getWorldBorder().isWithinBounds(head)
				? defaultBlockState().setValue(FACING, facing)
				: null;
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
		super.setPlacedBy(level, pos, state, by, stack);
		level.setBlockAndUpdate(pos.relative(state.getValue(FACING)), state.setValue(PART, BedPart.HEAD));
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour,
			BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (directionToNeighbour == toOtherHalf(state.getValue(PART), state.getValue(FACING))) {
			return neighbourState.is(this) && neighbourState.getValue(PART) != state.getValue(PART) ? state : Blocks.AIR.defaultBlockState();
		}
		return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
	}

	/** In creative, breaking the foot takes the head without dropping it (as a bed does). */
	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide() && player.preventsBlockDrops() && state.getValue(PART) == BedPart.FOOT) {
			BlockPos headPos = pos.relative(state.getValue(FACING));
			BlockState head = level.getBlockState(headPos);
			if (head.is(this) && head.getValue(PART) == BedPart.HEAD) {
				level.setBlock(headPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_NEIGHBORS | Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
				level.levelEvent(player, 2001, headPos, Block.getId(head));
			}
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	protected long getSeed(BlockState state, BlockPos pos) {
		// Both halves take the head's seed.
		BlockPos head = pos.relative(state.getValue(FACING), state.getValue(PART) == BedPart.HEAD ? 0 : 1);
		return Mth.getSeed(head.getX(), pos.getY(), head.getZ());
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return false;
	}
}
