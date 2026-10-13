package com.jheinz.mymod.fae;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;

/** A caught fairy in its bottle: stands or hangs like a lantern, lights the room, and sparkles now and then. */
public class FairyInABottleBlock extends LanternBlock {
	public FairyInABottleBlock(Properties properties) {
		super(properties);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(3) == 0) {
			double y = pos.getY() + (state.getValue(HANGING) ? 0.35 : 0.25) + random.nextDouble() * 0.2;
			level.addParticle(Fae.SPARKLE, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.3, y,
					pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.3, 0.0, 0.005, 0.0);
		}
	}
}
