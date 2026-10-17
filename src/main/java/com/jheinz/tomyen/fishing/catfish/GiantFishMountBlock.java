package com.jheinz.tomyen.fishing.catfish;

import java.util.EnumMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.jheinz.tomyen.fishing.FishMountBlock;
import com.jheinz.tomyen.fishing.FishMountBlockEntity;

/**
 * The Giant Fish Mount: a Fish Mount for a river monster, a dark carved board five blocks wide and two tall with a brass
 * plate under the middle, hung on a wall. Only giant trophies ({@link FishMountBlock#GIANT_TROPHIES}) go up on it, and the
 * plaque's too small for anything else; otherwise it works as a Fish Mount does (right-click to hang the fish or, with an
 * empty hand, take it down; a comparator reads it; breaking it drops it and the fish).
 *
 * <p>It's ten blocks, {@link #COLUMN} 0 to 4 from the viewer's left and {@link #ROW} 0 (bottom) or 1. The bottom middle
 * one is the master: it's where it's placed, it holds the fish (a {@link GiantFishMountBlockEntity}, which draws it across
 * the whole board) and it drops the item. Clicks anywhere on it go to the master, and breaking any piece brings the rest
 * down. The models are cut from the whole board by tools/gen_giant_catfish.py.
 */
public class GiantFishMountBlock extends HorizontalDirectionalBlock implements EntityBlock {
	public static final IntegerProperty COLUMN = IntegerProperty.create("column", 0, 4);
	public static final IntegerProperty ROW = IntegerProperty.create("row", 0, 1);
	public static final int MASTER_COLUMN = 2;
	public static final int COLUMNS = 5;
	public static final int ROWS = 2;

	/** Each row's slice of the board (16 deep in north's frame: the plaque against the block's south side). */
	private static final Map<Direction, VoxelShape> BOTTOM = shapes(1.0, 16.0);
	private static final Map<Direction, VoxelShape> TOP = shapes(0.0, 15.0);

	public GiantFishMountBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(COLUMN, MASTER_COLUMN).setValue(ROW, 0));
	}

	private static Map<Direction, VoxelShape> shapes(double y0, double y1) {
		Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
		shapes.put(Direction.NORTH, Block.box(0, y0, 13, 16, y1, 16));
		shapes.put(Direction.SOUTH, Block.box(0, y0, 0, 16, y1, 3));
		shapes.put(Direction.EAST, Block.box(0, y0, 0, 3, y1, 16));
		shapes.put(Direction.WEST, Block.box(13, y0, 0, 16, y1, 16));
		return shapes;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, COLUMN, ROW);
	}

	/** Where the piece at {@code column}, {@code row} goes for a mount whose master is at {@code master}. */
	public static BlockPos piece(BlockPos master, Direction facing, int column, int row) {
		return master.relative(facing.getClockWise(), MASTER_COLUMN - column).above(row);
	}

	/** The master of the mount {@code state} at {@code pos} is part of. */
	public static BlockPos master(BlockPos pos, BlockState state) {
		return pos.relative(state.getValue(FACING).getClockWise(), state.getValue(COLUMN) - MASTER_COLUMN).below(state.getValue(ROW));
	}

	public static boolean isMaster(BlockState state) {
		return state.getValue(COLUMN) == MASTER_COLUMN && state.getValue(ROW) == 0;
	}

	/** Placed where clicked as the bottom middle, facing out from the wall, if the whole board fits there. */
	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		Level level = context.getLevel();
		BlockPos master = context.getClickedPos();
		for (Direction direction : context.getNearestLookingDirections()) {
			if (!direction.getAxis().isHorizontal()) {
				continue;
			}
			Direction facing = direction.getOpposite();
			if (fits(context, level, master, facing)) {
				return defaultBlockState().setValue(FACING, facing);
			}
		}
		return null;
	}

	private boolean fits(BlockPlaceContext context, Level level, BlockPos master, Direction facing) {
		for (int column = 0; column < COLUMNS; column++) {
			for (int row = 0; row < ROWS; row++) {
				BlockPos pos = piece(master, facing, column, row);
				if (!level.isInsideBuildHeight(pos) || !level.getBlockState(pos).canBeReplaced(context) || !wallBehind(level, pos, facing)) {
					return false;
				}
			}
		}
		return true;
	}

	private static boolean wallBehind(LevelReader level, BlockPos pos, Direction facing) {
		BlockPos behind = pos.relative(facing.getOpposite());
		return level.getBlockState(behind).isFaceSturdy(level, behind, facing);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
		if (level.isClientSide()) {
			return;
		}
		Direction facing = state.getValue(FACING);
		for (int column = 0; column < COLUMNS; column++) {
			for (int row = 0; row < ROWS; row++) {
				if (column != MASTER_COLUMN || row != 0) {
					level.setBlock(piece(pos, facing, column, row), state.setValue(COLUMN, column).setValue(ROW, row), Block.UPDATE_ALL);
				}
			}
		}
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return wallBehind(level, pos, state.getValue(FACING));
	}

	/** Comes down if the wall goes, or any neighbouring piece of the board does. */
	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
			BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		Direction facing = state.getValue(FACING);
		int column = state.getValue(COLUMN);
		int row = state.getValue(ROW);
		if (direction == facing.getOpposite() && !state.canSurvive(level, pos)) {
			return Blocks.AIR.defaultBlockState();
		}
		int wantColumn = -1;
		int wantRow = row;
		if (direction == facing.getClockWise() && column > 0) {
			wantColumn = column - 1;
		} else if (direction == facing.getCounterClockWise() && column < COLUMNS - 1) {
			wantColumn = column + 1;
		} else if (direction == Direction.UP && row == 0 || direction == Direction.DOWN && row == 1) {
			wantColumn = column;
			wantRow = 1 - row;
		}
		if (wantColumn >= 0 && !(neighbourState.is(this) && neighbourState.getValue(FACING) == facing
				&& neighbourState.getValue(COLUMN) == wantColumn && neighbourState.getValue(ROW) == wantRow)) {
			return Blocks.AIR.defaultBlockState();
		}
		return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
	}

	/** In creative, breaking any piece takes the master without dropping it (the rest then follow). */
	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide() && player.preventsBlockDrops() && !isMaster(state)) {
			BlockPos master = master(pos, state);
			BlockState masterState = level.getBlockState(master);
			if (masterState.is(this) && isMaster(masterState)) {
				level.setBlock(master, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
				level.levelEvent(player, 2001, master, Block.getId(masterState));
			}
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return (state.getValue(ROW) == 0 ? BOTTOM : TOP).get(state.getValue(FACING));
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return isMaster(state) ? new GiantFishMountBlockEntity(pos, state) : null;
	}

	private static @Nullable FishMountBlockEntity mount(Level level, BlockPos pos, BlockState state) {
		return level.getBlockEntity(master(pos, state)) instanceof GiantFishMountBlockEntity mount ? mount : null;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
		FishMountBlockEntity mount = mount(level, pos, state);
		if (mount == null) {
			return InteractionResult.PASS;
		}
		if (!mount.fish().isEmpty()) {
			if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendOverlayMessage(mount.describe());
			}
			return InteractionResult.SUCCESS;
		}
		if (!stack.is(FishMountBlock.GIANT_TROPHIES)) {
			if (FishMountBlock.mountable(stack)) {
				if (player instanceof ServerPlayer serverPlayer) {
					serverPlayer.sendOverlayMessage(Component.translatable("block.tomyen.giant_fish_mount.too_small"));
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (!level.isClientSide()) {
			mount.setFish(stack.copyWithCount(1));
			stack.consume(1, player);
			level.playSound(null, mount.getBlockPos(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0f, 0.7f);
			if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendOverlayMessage(mount.describe());
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		FishMountBlockEntity mount = mount(level, pos, state);
		if (mount == null || mount.fish().isEmpty()) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			ItemStack fish = mount.fish();
			mount.setFish(ItemStack.EMPTY);
			player.getInventory().placeItemBackInInventory(fish, Prediction.SERVER_ONLY);
			level.playSound(null, mount.getBlockPos(), SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0f, 0.7f);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
		FishMountBlockEntity mount = mount(level, pos, state);
		return mount != null ? mount.signal() : 0;
	}
}
