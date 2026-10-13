package com.jheinz.mymod.fae;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * A Fae Ring: {@code mushrooms} mushrooms spaced evenly round a circle of {@code radius} blocks (each nudged a little
 * off its spot), each a Fae Mushroom with {@code fae_chance}, otherwise red or brown, and the grass inside turned to
 * Fae Grass. Every mushroom stands on the surface of its own column, so a ring follows gentle slopes; spots with no
 * solid ground or something in the way are skipped, and the ring fails if fewer than half its mushrooms found room.
 */
public record FaeRingFeature(IntProvider radius, IntProvider mushrooms, float faeChance) implements Feature {
	public static final MapCodec<FaeRingFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			IntProviders.codec(2, 6).fieldOf("radius").forGetter(FaeRingFeature::radius),
			IntProviders.codec(4, 24).fieldOf("mushrooms").forGetter(FaeRingFeature::mushrooms),
			Codec.floatRange(0.0f, 1.0f).fieldOf("fae_chance").forGetter(FaeRingFeature::faeChance))
			.apply(i, FaeRingFeature::new));

	@Override
	public MapCodec<FaeRingFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
		return placeRing(level, origin, random, radius.sample(random), mushrooms.sample(random), faeChance);
	}

	/** Places a ring round the column at the centre; also used for the Feywild's arrival ring. */
	public static boolean placeRing(WorldGenLevel level, BlockPos centre, RandomSource random, int radius, int count, float faeChance) {
		int placed = 0;
		float start = random.nextFloat() * Mth.TWO_PI;
		for (int i = 0; i < count; i++) {
			float angle = start + i * Mth.TWO_PI / count + (random.nextFloat() - 0.5f) * 0.3f;
			double r = radius + (random.nextDouble() - 0.5) * 0.6;
			int x = centre.getX() + Mth.floor(Mth.cos(angle) * r + 0.5);
			int z = centre.getZ() + Mth.floor(Mth.sin(angle) * r + 0.5);
			BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
			BlockState mushroom = random.nextFloat() < faeChance ? Fae.FAE_MUSHROOM.defaultBlockState()
					: (random.nextBoolean() ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM).defaultBlockState();
			if (level.getBlockState(pos).canBeReplaced() && level.getFluidState(pos).isEmpty()
					&& level.getBlockState(pos.below()).isSolidRender()) {
				level.setBlock(pos, mushroom, 2);
				placed++;
			}
		}
		if (placed * 2 < count) {
			return false;
		}
		// The brighter grass inside.
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (dx * dx + dz * dz > (radius - 0.5) * (radius - 0.5)) {
					continue;
				}
				int x = centre.getX() + dx, z = centre.getZ() + dz;
				BlockPos ground = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
				if (level.getBlockState(ground).is(Blocks.GRASS_BLOCK)) {
					level.setBlock(ground, Fae.FAE_GRASS_BLOCK.defaultBlockState(), 2);
				}
			}
		}
		return true;
	}
}
