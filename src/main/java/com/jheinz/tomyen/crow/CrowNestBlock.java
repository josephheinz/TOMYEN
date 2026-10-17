package com.jheinz.tomyen.crow;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.core.Direction;

/**
 * A crow's nest: a bowl of woven twigs lined with black feathers, built by a murder in a treetop to hoard what it
 * steals. Open it to take the loot back (two rows, like a small chest); the murder doesn't like that, and a few of the
 * bolder crows come down to peck at whoever's robbing them (see {@link CrowNests#alarm}). Breaking it spills everything.
 * {@link #LOOT} shows a glint of treasure in the bowl, and the odd sparkle, while it holds anything.
 */
public class CrowNestBlock extends Block implements EntityBlock {
	public static final BooleanProperty LOOT = BooleanProperty.create("loot");
	/** Where a crow stands on it: the rim of the bowl, in pixels. */
	public static final double STAND_HEIGHT = 6.0;
	private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 6.0, 15.0);

	public CrowNestBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(LOOT, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LOOT);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return false;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new CrowNestBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof CrowNestBlockEntity nest) {
			player.openMenu(nest);
			if (!player.isSpectator()) {
				CrowNests.alarm(serverLevel, pos, player, false);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (level instanceof ServerLevel serverLevel && !player.isCreative()) {
			CrowNests.alarm(serverLevel, pos, player, true);
		}
		return super.playerWillDestroy(level, pos, state, player);
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

	/** A glint now and then off whatever's in it. */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (state.getValue(LOOT) && random.nextInt(6) == 0) {
			level.addParticle(ParticleTypes.WAX_OFF, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.3,
					pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0.0, 0.02, 0.0);
		}
	}
}
