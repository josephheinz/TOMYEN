package com.jheinz.tomyen.fishing;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jspecify.annotations.Nullable;

/**
 * A Fish Mount: a carved plaque with a brass name plate that hangs on a wall like an item frame. Right-click it with a
 * fish (any fish: mod or vanilla, trophy or not) to mount it; right-click with an empty hand to take it back down. The
 * fish is drawn in 3D, sized by its weight (FishMountRenderer, client side), and the plate reads its name and weight.
 * A comparator reads how heavy the fish is for its kind. Breaking the plaque drops it and the fish, components and all.
 * {@code FACING} is the way the plaque faces, out from the wall it hangs on.
 */
public class FishMountBlock extends HorizontalDirectionalBlock implements EntityBlock {
	/** The plaque against the wall behind it: 16 wide, 14 tall, 2.5 deep (modelled by tools/gen_fish_mount.py). */
	private static final Map<Direction, VoxelShape> SHAPES = Map.of(
			Direction.NORTH, Block.box(0, 1, 13.5, 16, 15, 16),
			Direction.SOUTH, Block.box(0, 1, 0, 16, 15, 2.5),
			Direction.WEST, Block.box(13.5, 1, 0, 16, 15, 16),
			Direction.EAST, Block.box(0, 1, 0, 2.5, 15, 16));

	public FishMountBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	/** Whether a stack can go on a mount: any fish, plus anything a species file catches (an old boot, say). */
	public static boolean mountable(ItemStack stack) {
		return !stack.isEmpty() && (stack.is(ItemTags.FISHES) || stack.has(Fishing.TROPHY) || FishingSpecies.forItem(stack.getItem()) != null);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = defaultBlockState();
		for (Direction direction : context.getNearestLookingDirections()) {
			if (direction.getAxis().isHorizontal()) {
				state = state.setValue(FACING, direction.getOpposite());
				if (state.canSurvive(context.getLevel(), context.getClickedPos())) {
					return state;
				}
			}
		}
		return null;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		Direction facing = state.getValue(FACING);
		BlockPos behind = pos.relative(facing.getOpposite());
		return level.getBlockState(behind).isFaceSturdy(level, behind, facing);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
			BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		return direction.getOpposite() == state.getValue(FACING) && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
				: super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FishMountBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof FishMountBlockEntity mount)) {
			return InteractionResult.PASS;
		}
		if (!mount.fish().isEmpty()) {
			// Something's up already: tell them what (an empty hand takes it down, in useWithoutItem).
			if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendOverlayMessage(mount.describe());
			}
			return InteractionResult.SUCCESS;
		}
		if (!mountable(stack)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (!level.isClientSide()) {
			mount.setFish(stack.copyWithCount(1));
			stack.consume(1, player);
			level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0f, 0.9f);
			if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendOverlayMessage(mount.describe());
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof FishMountBlockEntity mount) || mount.fish().isEmpty()) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			ItemStack fish = mount.fish();
			mount.setFish(ItemStack.EMPTY);
			player.getInventory().placeItemBackInInventory(fish, Prediction.SERVER_ONLY);
			level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0f, 0.9f);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
		return level.getBlockEntity(pos) instanceof FishMountBlockEntity mount ? mount.signal() : 0;
	}
}
