package com.jheinz.mymod.starforge;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Star Forge. {@code starlit} while it stands under an open night sky (its core glows and the window shows the
 * stars); dark and dormant by day, under a roof, or where there's no sky. {@code forging} while it's infusing.
 */
public class StarForgeBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	public static final BooleanProperty STARLIT = BooleanProperty.create("starlit");
	public static final BooleanProperty FORGING = BooleanProperty.create("forging");
	/** The base, the body and the ring on top; the spires and the core poke above it. */
	private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 2, 16), Block.box(1, 2, 1, 15, 10, 15),
			Block.box(4, 10, 4, 12, 15, 12), Block.box(0, 2, 0, 3, 16, 3), Block.box(13, 2, 0, 16, 16, 3), Block.box(0, 2, 13, 3, 16, 16),
			Block.box(13, 2, 13, 16, 16, 16));
	private static final DustParticleOptions STARLIGHT = new DustParticleOptions(0xCFE2FF, 0.7f);

	public StarForgeBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STARLIT, false).setValue(FORGING, false));
	}

	static int lightLevel(BlockState state) {
		return state.getValue(FORGING) ? 13 : state.getValue(STARLIT) ? 9 : 2;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new StarForgeBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level instanceof ServerLevel ? createTickerHelper(type, StarForge.STAR_FORGE_ENTITY, StarForgeBlockEntity::serverTick) : null;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof StarForgeBlockEntity forge) {
			player.openMenu(forge);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		Containers.updateNeighboursAfterDestroy(state, level, pos);
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
		return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
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
		builder.add(FACING, STARLIT, FORGING);
	}

	/**
	 * Under the night sky, motes of starlight drift down into the core now and then; while it forges they pour in, the
	 * spires spark and the crystal chimes.
	 */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!state.getValue(STARLIT)) {
			return;
		}
		double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;
		boolean forging = state.getValue(FORGING);
		if (random.nextFloat() < (forging ? 0.9f : 0.25f)) {
			double fall = 1.2 + random.nextDouble() * 1.5;
			level.addParticle(STARLIGHT, x + random.nextGaussian() * 0.15, y + 0.9 + fall, z + random.nextGaussian() * 0.15, 0.0, -0.12, 0.0);
		}
		if (!forging) {
			return;
		}
		// A spark off one of the four crystal spires.
		double sx = random.nextBoolean() ? 0.1 : 0.9, sz = random.nextBoolean() ? 0.1 : 0.9;
		level.addParticle(ParticleTypes.END_ROD, pos.getX() + sx, y + 1.02, pos.getZ() + sz, (0.5 - sx) * 0.04, 0.01, (0.5 - sz) * 0.04);
		if (random.nextFloat() < 0.5f) {
			level.addParticle(ParticleTypes.GLOW, x + random.nextGaussian() * 0.12, y + 0.85, z + random.nextGaussian() * 0.12, 0.0, 0.02, 0.0);
		}
		if (random.nextFloat() < 0.08f) {
			level.playLocalSound(x, y + 0.8, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8f, 0.6f + random.nextFloat() * 0.6f,
					false);
		}
	}
}
