package com.jheinz.mymod.industry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

import org.jspecify.annotations.Nullable;

/**
 * The Metal Press: a frame with a heavy ram over an anvil bed. Use ingots on it to stack them on the bed, then give
 * it a redstone pulse: the ram slams down and stamps one ingot into a plate or sheet (see
 * {@link MetalPressBlockEntity}). Use it with an empty hand to take the plates (or, with none, the ingots) back.
 */
public class MetalPressBlock extends BaseEntityBlock {
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

	public MetalPressBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(POWERED, false));
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MetalPressBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return createTickerHelper(type, Industry.METAL_PRESS_ENTITY, MetalPressBlockEntity::tick);
	}

	/** Each rising edge of a redstone signal is one stroke of the ram. */
	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation,
			boolean movedByPiston) {
		boolean powered = level.hasNeighborSignal(pos);
		if (powered == state.getValue(POWERED)) {
			return;
		}
		if (powered && level.getBlockEntity(pos) instanceof MetalPressBlockEntity press) {
			press.startStroke();
		}
		level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
		if (!MetalPressBlockEntity.isPressable(stack) || !(level.getBlockEntity(pos) instanceof MetalPressBlockEntity press)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (!level.isClientSide() && !press.load(stack)) {
			return InteractionResult.FAIL;
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof MetalPressBlockEntity press) || press.isEmpty()) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			press.unload(player);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int b0, int b1) {
		super.triggerEvent(state, level, pos, b0, b1);
		BlockEntity entity = level.getBlockEntity(pos);
		return entity != null && entity.triggerEvent(b0, b1);
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
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(POWERED);
	}
}
