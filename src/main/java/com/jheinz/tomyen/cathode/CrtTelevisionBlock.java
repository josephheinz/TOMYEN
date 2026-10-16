package com.jheinz.tomyen.cathode;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A CRT Television, made with a Cathode Tube: a wood-veneer set that shows static and glows (light {@value #LIGHT}).
 * Placed switched on; right-click to click it off and on.
 */
public class CrtTelevisionBlock extends Block {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	public static final BooleanProperty LIT = BlockStateProperties.LIT;
	public static final int LIGHT = 9;

	public CrtTelevisionBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, true));
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide()) {
			BlockState toggled = state.cycle(LIT);
			level.setBlock(pos, toggled, Block.UPDATE_ALL);
			level.playSound(null, pos, CathodeWalkers.TELEVISION_TOGGLE, SoundSource.BLOCKS, 0.6f, toggled.getValue(LIT) ? 1.1f : 0.8f);
		}
		return InteractionResult.SUCCESS;
	}

	/** Switched on, it hisses now and then and a spark or two jumps off the screen. */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!state.getValue(LIT) || random.nextInt(40) != 0) {
			return;
		}
		Direction facing = state.getValue(FACING);
		double x = pos.getX() + 0.5 + facing.getStepX() * 0.52 + (random.nextDouble() - 0.5) * 0.5 * Math.abs(facing.getStepZ());
		double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.52 + (random.nextDouble() - 0.5) * 0.5 * Math.abs(facing.getStepX());
		level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, pos.getY() + 0.4 + random.nextDouble() * 0.4, z, 0.0, 0.0, 0.0);
		level.playLocalSound(pos, CathodeWalkers.AMBIENT, SoundSource.BLOCKS, 0.15f, 1.0f + random.nextFloat() * 0.2f, false);
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
		builder.add(FACING, LIT);
	}
}
