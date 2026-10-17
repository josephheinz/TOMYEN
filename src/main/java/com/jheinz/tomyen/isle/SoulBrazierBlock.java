package com.jheinz.tomyen.isle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A Soul Brazier: an iron bowl on three legs, heaped with embers burning soul-blue. The Isle's only warm light, at its
 * shrine, round its arenas and among its ruins.
 */
public class SoulBrazierBlock extends Block {
	private static final VoxelShape SHAPE = Shapes.or(Block.box(2, 0, 2, 14, 6, 14), Block.box(1, 6, 1, 15, 12, 15));

	public SoulBrazierBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		for (int i = 0; i < 2; i++) {
			level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.8,
					pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0.0, 0.03 + random.nextDouble() * 0.02, 0.0);
		}
		if (random.nextInt(5) == 0) {
			level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 0.0, 0.04, 0.0);
		}
		if (random.nextInt(30) == 0) {
			level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.6f,
					0.7f + random.nextFloat() * 0.2f, false);
		}
	}
}
