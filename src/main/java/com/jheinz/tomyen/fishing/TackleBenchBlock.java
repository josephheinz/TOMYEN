package com.jheinz.tomyen.fishing;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A little angler's bench: opens the rod-customizing menu, the way the Weapon Station opens its own. */
public class TackleBenchBlock extends HorizontalDirectionalBlock {
	private static final Component TITLE = Component.translatable("container.tomyen.tackle_bench");
	/** The top, the four legs and the shelf (the model's tackle on top is left out, like a lectern's book). */
	private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 13, 0, 16, 16, 16),
			Block.box(1, 0, 1, 3, 13, 3), Block.box(13, 0, 1, 15, 13, 3), Block.box(1, 0, 13, 3, 13, 15), Block.box(13, 0, 13, 15, 13, 15),
			Block.box(1.5, 3, 1.5, 14.5, 5, 14.5));

	public TackleBenchBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide()) {
			player.openMenu(state.getMenuProvider(level, pos));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
		return new SimpleMenuProvider((containerId, inventory, player) ->
				new TackleBenchMenu(containerId, inventory, ContainerLevelAccess.create(level, pos)), TITLE);
	}
}
