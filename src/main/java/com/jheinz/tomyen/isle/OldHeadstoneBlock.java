package com.jheinz.tomyen.isle;

import java.util.EnumMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * An old headstone with its name worn away, sunk at an angle into the ash: decoration, scattered over the Isle and
 * round its arenas. {@code lean} 0 stands upright, 1 leans back and 2 leans to one side. Placed by hand it picks a lean
 * from where it's set down.
 */
public class OldHeadstoneBlock extends HorizontalDirectionalBlock {
	public static final IntegerProperty LEAN = IntegerProperty.create("lean", 0, 2);
	private static final VoxelShape NORTH = Block.box(3, 0, 6, 13, 13, 11);
	private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

	static {
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			SHAPES.put(direction, GravestoneBlock.rotate(NORTH, direction));
		}
	}

	public OldHeadstoneBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LEAN, 0));
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockPos pos = context.getClickedPos();
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
				.setValue(LEAN, Math.floorMod(pos.getX() * 7 + pos.getZ() * 13, 3));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, LEAN);
	}
}
