package com.jheinz.tomyen.isle;

import java.util.EnumMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A player's grave, put up where they died with something on them (see {@link DeathCapture}): a carved headstone on a
 * mossy plinth, a little soul lantern at its foot and the owner's name cut into its face. It can't be broken; it goes
 * by itself once it no longer leads anywhere (see {@link GravestoneBlockEntity}). Using it is {@link PayingRespects}.
 *
 * <p>{@code facing} is the way its carved face looks. Waterloggable, for the drowned.
 */
public class GravestoneBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

	/** For a stone facing north (laid out as tools/gen_isle.py builds it): plinth, headstone, and the lantern. */
	private static final VoxelShape NORTH = Shapes.or(Block.box(1, 0, 2, 15, 3, 14), Block.box(3, 3, 7, 13, 14, 11),
			Block.box(5, 14, 7, 11, 16, 11), Block.box(2, 3, 3.5, 4.6, 8.3, 6.5));
	private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

	static {
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			SHAPES.put(direction, rotate(NORTH, direction));
		}
	}

	public GravestoneBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false));
	}

	/** A shape laid out facing north, turned to face the given way. */
	static VoxelShape rotate(VoxelShape north, Direction facing) {
		VoxelShape[] out = {Shapes.empty()};
		north.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
			VoxelShape box = switch (facing) {
				case SOUTH -> Shapes.box(1 - x1, y0, 1 - z1, 1 - x0, y1, 1 - z0);
				case EAST -> Shapes.box(1 - z1, y0, x0, 1 - z0, y1, x1);
				case WEST -> Shapes.box(z0, y0, 1 - x1, z1, y1, 1 - x0);
				default -> Shapes.box(x0, y0, z0, x1, y1, z1);
			};
			out[0] = Shapes.or(out[0], box);
		});
		return out[0].optimize();
	}

	/** It goes quietly: a puff of soul and dust, and the stone is gone. */
	public static void crumble(ServerLevel level, BlockPos pos, BlockState state) {
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 30,
				0.3, 0.4, 0.3, 0.05);
		level.sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 8, 0.2, 0.3, 0.2, 0.02);
		level.playSound(null, pos, SoundEvents.TUFF_BREAK, SoundSource.BLOCKS, 1.0f, 0.7f);
		level.setBlock(pos, state.getValue(WATERLOGGED) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new GravestoneBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level instanceof ServerLevel ? createTickerHelper(type, Isle.GRAVESTONE_ENTITY, GravestoneBlockEntity::serverTick) : null;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour,
			BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (state.getValue(WATERLOGGED)) {
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		}
		return state;
	}

	/** The lantern's flame flickers, and now and then a wisp of soul drifts up off the stone. */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		Direction facing = state.getValue(FACING);
		// The lantern stands at the front left corner of the plinth (x 3.3, z 5 for a north-facing stone).
		double lx = 3.3 / 16.0 - 0.5, lz = 5.0 / 16.0 - 0.5;
		double x = pos.getX() + 0.5, z = pos.getZ() + 0.5;
		switch (facing) {
			case SOUTH -> {
				x -= lx;
				z -= lz;
			}
			case EAST -> {
				x -= lz;
				z += lx;
			}
			case WEST -> {
				x += lz;
				z -= lx;
			}
			default -> {
				x += lx;
				z += lz;
			}
		}
		if (random.nextInt(4) == 0) {
			level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, pos.getY() + 6.5 / 16.0, z, 0.0, 0.004, 0.0);
		}
		if (random.nextInt(12) == 0) {
			level.addParticle(ParticleTypes.SOUL, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 1.0,
					pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0.0, 0.02, 0.0);
		}
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
		builder.add(FACING, WATERLOGGED);
	}
}
