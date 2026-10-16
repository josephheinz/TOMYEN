package com.jheinz.mymod.herbalism;

import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A wild herb: a small flower-like plant (so it works in suspicious stew, like any flower) that grows on its own kind
 * of ground and spreads with bone meal, seeding a copy of itself on a free neighbouring spot. Broken, it drops itself
 * to be planted again (wild garlic drops garlic instead: see its loot table).
 */
public class HerbBlock extends FlowerBlock implements BonemealableBlock {
	private final Predicate<BlockState> soil;
	private final @Nullable ParticleOptions glow;

	/**
	 * @param soil what it can grow on
	 * @param glow a particle it now and then gives off at night (the moonpetal's sparkle), or null
	 */
	public HerbBlock(Holder<MobEffect> stewEffect, float stewSeconds, Predicate<BlockState> soil, @Nullable ParticleOptions glow,
			Properties properties) {
		super(stewEffect, stewSeconds, properties);
		this.soil = soil;
		this.glow = glow;
	}

	@Override
	protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
		return soil.test(state);
	}

	@Override
	public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, BonemealSource source) {
		return BonemealableBlock.hasSpreadableNeighbourPos(level, pos, state);
	}

	@Override
	public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
		return true;
	}

	@Override
	public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
		BonemealableBlock.findSpreadableNeighbourPos(level, pos, state).ifPresent(spot -> level.setBlockAndUpdate(spot, defaultBlockState()));
	}

	@Override
	public Type getType() {
		return Type.NEIGHBOR_SPREADER;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (glow != null && level.isDarkOutside() && random.nextInt(6) == 0) {
			level.addParticle(glow, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.5 + random.nextDouble() * 0.4,
					pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0.0, 0.01, 0.0);
		}
	}
}
