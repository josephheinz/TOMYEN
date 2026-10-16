package com.jheinz.tomyen.fae;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A glowing violet mushroom. It grows on any full block, in any light, and doesn't spread. At night (always, in the
 * Feywild), each random tick has a {@value #CALL_CHANCE} chance of calling up a fairy beside it, unless
 * {@value #CROWD} or more are already within {@value #CROWD_RADIUS} blocks: that's how fairies come to Overworld
 * rings.
 */
public class FaeMushroomBlock extends VegetationBlock {
	private static final VoxelShape SHAPE = Block.column(10.0, 0.0, 10.0);
	private static final float CALL_CHANCE = 0.3f;
	private static final int CROWD = 3;
	private static final double CROWD_RADIUS = 16.0;

	public FaeMushroomBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
		return state.isSolidRender();
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!(level.isDarkOutside() || Feywild.is(level)) || random.nextFloat() >= CALL_CHANCE
				|| level.getEntitiesOfClass(Fairy.class, new AABB(pos).inflate(CROWD_RADIUS)).size() >= CROWD) {
			return;
		}
		Fairy fairy = Fae.FAIRY.create(level, EntitySpawnReason.NATURAL);
		if (fairy != null) {
			fairy.snapTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, random.nextFloat() * 360.0f, 0.0f);
			if (!Feywild.is(level)) {
				fairy.markNightBound();
			}
			level.addFreshEntity(fairy);
			fairy.poof();
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(4) == 0) {
			level.addParticle(Fae.SPARKLE, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.3 + random.nextDouble() * 0.4,
					pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0.0, 0.01, 0.0);
		}
	}
}
