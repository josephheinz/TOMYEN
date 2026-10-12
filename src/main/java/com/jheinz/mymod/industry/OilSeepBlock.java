package com.jheinz.mymod.industry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Oil-stained ground where a crude oil pocket leaks to the surface: the prospector's marker. Black drips well up
 * out of it and it gives off the odd wisp of fume. A Pump Jack stood on (or just above) one draws from the pocket
 * below; see {@link OilDeposits}.
 */
public class OilSeepBlock extends Block {
	private static final DustParticleOptions OIL = new DustParticleOptions(0x121010, 1.2f);

	public OilSeepBlock(Properties properties) {
		super(properties);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!level.getBlockState(pos.above()).isAir()) {
			return;
		}
		double x = pos.getX() + 0.2 + random.nextDouble() * 0.6, y = pos.getY() + 1.02, z = pos.getZ() + 0.2 + random.nextDouble() * 0.6;
		if (random.nextInt(3) == 0) {
			level.addParticle(OIL, x, y, z, 0.0, 0.0, 0.0);
		}
		if (random.nextInt(12) == 0) {
			level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0, 0.02, 0.0);
		}
		if (random.nextInt(200) == 0) {
			level.playLocalSound(x, y, z, SoundEvents.MUD_STEP, SoundSource.BLOCKS, 0.4f, 0.5f, false);
		}
	}
}
