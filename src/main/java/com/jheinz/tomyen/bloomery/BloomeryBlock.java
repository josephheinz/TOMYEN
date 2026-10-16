package com.jheinz.tomyen.bloomery;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Bloomery: its front (the tapping arch) faces the player who placed it, the bellows sits on its right as you face
 * it, and it glows while its charge burns. Right-clicking the bellows works it; right-clicking anywhere else opens it.
 * <p>
 * Model space (tools/gen_bloomery.py) faces north: the arch looks north, and the bellows fills the west strip,
 * x < {@link #BELLOWS_EDGE} pixels.
 */
public class BloomeryBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	public static final BooleanProperty LIT = BlockStateProperties.LIT;
	/** The bellows' east edge in model pixels, facing north. */
	public static final double BELLOWS_EDGE = 4.5;
	/** The bellows' top in model pixels: a click above it is on the furnace. */
	public static final double BELLOWS_TOP = 8.0;

	private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Shapes.or(
			Block.box(0, 0, 0, 16, 2, 16),       // the stone footing
			Block.box(5, 2, 2, 15, 8, 12),       // the brick hearth
			Block.box(6, 8, 3, 14, 12, 11),      // the slitted shaft
			Block.box(7, 12, 4, 13, 16, 10),     // the throat
			Block.box(0, 2, 4, 5, 7, 16)));      // the bellows

	public BloomeryBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new BloomeryBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level instanceof ServerLevel ? createTickerHelper(type, Bloomery.BLOOMERY_ENTITY, BloomeryBlockEntity::serverTick) : null;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	/**
	 * Where a point in the block (0 to 1 on each axis, world-aligned) lies in model space facing north, in pixels: the
	 * model is turned clockwise (seen from above) a quarter turn for each step from north to the block's facing.
	 */
	public static Vec3 toModel(Direction facing, Vec3 local) {
		double dx = local.x - 0.5, dz = local.z - 0.5;
		double mx, mz;
		switch (facing) {
			case EAST -> {
				mx = dz;
				mz = -dx;
			}
			case SOUTH -> {
				mx = -dx;
				mz = -dz;
			}
			case WEST -> {
				mx = -dz;
				mz = dx;
			}
			default -> {
				mx = dx;
				mz = dz;
			}
		}
		return new Vec3((mx + 0.5) * 16.0, local.y * 16.0, (mz + 0.5) * 16.0);
	}

	/** The reverse of {@link #toModel}: a model-space point (pixels) as a world position in the block at {@code pos}. */
	public static Vec3 toWorld(Direction facing, BlockPos pos, double px, double py, double pz) {
		double mx = px / 16.0 - 0.5, mz = pz / 16.0 - 0.5;
		double dx, dz;
		switch (facing) {
			case EAST -> {
				dx = -mz;
				dz = mx;
			}
			case SOUTH -> {
				dx = -mx;
				dz = -mz;
			}
			case WEST -> {
				dx = mz;
				dz = -mx;
			}
			default -> {
				dx = mx;
				dz = mz;
			}
		}
		return new Vec3(pos.getX() + 0.5 + dx, pos.getY() + py / 16.0, pos.getZ() + 0.5 + dz);
	}

	private static boolean onBellows(BlockState state, BlockPos pos, BlockHitResult hit) {
		Vec3 local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
		Vec3 model = toModel(state.getValue(FACING), local);
		return model.x < BELLOWS_EDGE + 0.25 && model.y < BELLOWS_TOP;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
		if (onBellows(state, pos, hit)) {
			return pump(level, pos, player);
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (onBellows(state, pos, hit)) {
			return pump(level, pos, player);
		}
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof BloomeryBlockEntity bloomery) {
			player.openMenu((MenuProvider) bloomery);
		}
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult pump(Level level, BlockPos pos, Player player) {
		if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof BloomeryBlockEntity bloomery) {
			bloomery.pump(server);
		}
		return InteractionResult.SUCCESS;
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
		builder.add(FACING, LIT);
	}

	/** Smoke and the odd spark from the throat, a glow of embers at the arch, and the crackle of the charge. */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!state.getValue(LIT)) {
			return;
		}
		Direction facing = state.getValue(FACING);
		if (random.nextDouble() < 0.15) {
			level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.7f,
					0.8f + random.nextFloat() * 0.3f, false);
		}
		Vec3 throat = toWorld(facing, pos, 8.0 + random.nextDouble() * 4.0, 16.0, 5.0 + random.nextDouble() * 4.0);
		level.addParticle(ParticleTypes.SMOKE, throat.x, throat.y, throat.z, 0.0, 0.04, 0.0);
		if (random.nextDouble() < 0.08) {
			level.addParticle(ParticleTypes.LAVA, throat.x, throat.y, throat.z, 0.0, 0.0, 0.0);
		}
		if (random.nextDouble() < 0.3) {
			Vec3 arch = toWorld(facing, pos, 8.5 + random.nextDouble() * 3.0, 2.6, 2.6);
			level.addParticle(ParticleTypes.SMALL_FLAME, arch.x, arch.y, arch.z, 0.0, 0.005, 0.0);
		}
	}
}
