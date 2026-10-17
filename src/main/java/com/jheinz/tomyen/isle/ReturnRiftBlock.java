package com.jheinz.tomyen.isle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A Return Rift: a tall tear of pale light that opens where a Revenant falls. Stepping into it sends anyone home (see
 * {@link IsleDimension#sendHome}), the victor and anyone who helped. It stays open.
 */
public class ReturnRiftBlock extends Block {
	private static final VoxelShape SHAPE = Block.box(4, 0, 6, 12, 16, 10);
	private static final VoxelShape INSIDE = Block.box(3, 0, 3, 13, 28, 13);

	public ReturnRiftBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	protected VoxelShape getEntityInsideCollisionShape(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
		return INSIDE;
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
		if (entity instanceof ServerPlayer player && !player.isSpectator() && player.isAlive() && !player.isOnPortalCooldown()) {
			player.setPortalCooldown();
			IsleDimension.sendHome(player, true);
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		for (int i = 0; i < 2; i++) {
			level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.5, pos.getY() + random.nextDouble() * 1.8,
					pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.2, 0.0, 0.02, 0.0);
		}
		if (random.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5, pos.getY() + 0.2 + random.nextDouble() * 1.5, pos.getZ() + 0.5,
					(random.nextDouble() - 0.5) * 0.4, 0.05, (random.nextDouble() - 0.5) * 0.4);
		}
	}
}
