package com.jheinz.mymod.meteorite;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * An old meteorite crash site on the surface (see {@link MeteoriteGenerator}): a crater {@code crater_radius} blocks
 * out from the origin's column, wherever the ground is there (the origin's height doesn't matter), with a core of
 * {@code core_radius} in it and its star crystal already growing. The crater is always at least two blocks wider
 * than the core. Places nothing over water, ice or steep ground.
 */
public record MeteoriteCrashSiteFeature(IntProvider craterRadius, IntProvider coreRadius) implements Feature {
	public static final MapCodec<MeteoriteCrashSiteFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			IntProviders.codec(3, 8).fieldOf("crater_radius").forGetter(MeteoriteCrashSiteFeature::craterRadius),
			IntProviders.codec(2, 5).fieldOf("core_radius").forGetter(MeteoriteCrashSiteFeature::coreRadius))
			.apply(i, MeteoriteCrashSiteFeature::new));

	@Override
	public MapCodec<MeteoriteCrashSiteFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
		int core = coreRadius.sample(random);
		int crater = Math.max(craterRadius.sample(random), core + 2);
		return MeteoriteGenerator.place(level, random, origin.getX(), origin.getZ(), crater, core, false, Block.UPDATE_CLIENTS);
	}
}
