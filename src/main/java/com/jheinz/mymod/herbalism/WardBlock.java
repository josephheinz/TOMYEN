package com.jheinz.mymod.herbalism;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A charm hung on a wall, facing out from it: the Garlic Braid (vampires keep their distance, see
 * {@link HerbalWards#GARLIC_RADIUS}) and the Wolfsbane Ward (cursed villagers nearby hold their shape on a full moon,
 * see {@link HerbalWards#WOLFSBANE_RADIUS}). Each is a point of interest, so the wards are found quickly. It needs a
 * sturdy wall behind it and drops if the wall goes.
 */
public class WardBlock extends Block {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

	private final Map<Direction, VoxelShape> shapes;
	private final @Nullable ParticleOptions aura;

	/**
	 * @param width how wide it hangs, in pixels (centred)
	 * @param bottom how low it hangs, in pixels from the block's floor
	 * @param top how high it reaches
	 * @param depth how far it stands out from the wall
	 * @param aura a particle it now and then gives off, or null
	 */
	public WardBlock(Properties properties, double width, double bottom, double top, double depth, @Nullable ParticleOptions aura) {
		super(properties);
		this.aura = aura;
		double half = width / 2.0;
		// Facing north means it hangs on the wall to its south, standing out toward the north.
		VoxelShape north = Block.box(8.0 - half, bottom, 16.0 - depth, 8.0 + half, top, 16.0);
		this.shapes = Shapes.rotateHorizontal(north);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shapes.get(state.getValue(FACING));
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		Direction facing = state.getValue(FACING);
		BlockPos wall = pos.relative(facing.getOpposite());
		return level.getBlockState(wall).isFaceSturdy(level, wall, facing);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		for (Direction direction : context.getNearestLookingDirections()) {
			if (direction.getAxis().isHorizontal()) {
				BlockState state = defaultBlockState().setValue(FACING, direction.getOpposite());
				if (state.canSurvive(context.getLevel(), context.getClickedPos())) {
					return state;
				}
			}
		}
		return null;
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
			BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		return direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
				: super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (aura == null || random.nextInt(12) != 0) {
			return;
		}
		Direction facing = state.getValue(FACING);
		double x = pos.getX() + 0.5 - facing.getStepX() * 0.3 + (random.nextDouble() - 0.5) * 0.4 * Math.abs(facing.getStepZ());
		double z = pos.getZ() + 0.5 - facing.getStepZ() * 0.3 + (random.nextDouble() - 0.5) * 0.4 * Math.abs(facing.getStepX());
		level.addParticle(aura, x, pos.getY() + 0.2 + random.nextDouble() * 0.6, z, 0.0, 0.0, 0.0);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}
}
