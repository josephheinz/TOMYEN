package com.jheinz.mymod.bank;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * An ATM: the banker's job site, so an unemployed villager next to one becomes a banker. Players can use
 * it too:
 * <ul>
 * <li>right-click to open your bank vault (see {@link Vaults}), the same one from every ATM, with your
 * statement on top and a button to pay your debt from the emeralds inside;</li>
 * <li>right-click with a debit card nobody has linked yet to link it to your vault;</li>
 * <li>sneak-right-click with emeralds (or emerald blocks, worth nine) to pay down your debt from your hand
 * (see {@link Banking#initialize}: sneaking with an item in hand never reaches the block itself).</li>
 * </ul>
 */
public class AtmBlock extends Block {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	private static final Component TITLE = Component.translatable("container.mymod.bank_vault");

	public AtmBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
		if (!(stack.getItem() instanceof DebitCardItem) || DebitCardItem.holder(stack) != null) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			DebitCardItem.link(stack, serverPlayer);
			level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4f, 1.6f);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer serverPlayer) {
			VaultContainer vault = Vaults.of(serverPlayer);
			serverPlayer.openMenu(new SimpleMenuProvider((containerId, inventory, user) ->
					new VaultMenu(containerId, inventory, vault, ContainerLevelAccess.create(level, pos)), TITLE));
			level.playSound(null, pos, SoundEvents.VAULT_OPEN_SHUTTER, SoundSource.BLOCKS, 0.6f, 1.2f);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
