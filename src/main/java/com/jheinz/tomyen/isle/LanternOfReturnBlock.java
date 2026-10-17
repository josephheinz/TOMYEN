package com.jheinz.tomyen.isle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Lantern of Return: a tall iron lantern on a stone post at the arrival shrine, burning with a pale soul flame.
 * Using it takes anyone home, to where they were pulled down from, whether or not they've won anything back; their
 * hoard stays safe on the Isle for another try. It's always there, so no one is ever stuck on the Isle.
 */
public class LanternOfReturnBlock extends Block {
	private static final VoxelShape SHAPE = Shapes.or(Block.box(3, 0, 3, 13, 3, 13), Block.box(5, 3, 5, 11, 16, 11), Block.box(4, 16, 4, 12, 26, 12));

	public LanternOfReturnBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer serverPlayer) {
			if (Hoards.get(serverPlayer.level().getServer(), serverPlayer.getUUID()) != null) {
				serverPlayer.sendSystemMessage(Component.translatable("message.tomyen.isle.hoard_waits"));
			}
			IsleDimension.sendHome(serverPlayer, false);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(2) == 0) {
			level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.15, pos.getY() + 1.3,
					pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.15, 0.0, 0.01, 0.0);
		}
		if (random.nextInt(6) == 0) {
			level.addParticle(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 1.7, pos.getZ() + 0.5, (random.nextDouble() - 0.5) * 0.02, 0.03,
					(random.nextDouble() - 0.5) * 0.02);
		}
	}
}
