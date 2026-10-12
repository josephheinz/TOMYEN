package com.jheinz.mymod.industry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
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
 * A pocket of crude oil trapped in the rock: a lumpy ellipsoid of oil source blocks centred on the origin, only
 * where there was stone to replace. A cap of oil shale lines part of its shell, and wherever the pocket broke into
 * a cave the gap is sealed with shale so the oil stays put. With {@code seep_chance}, the ground straight above
 * gets an Oil Seep (and a couple of stained blocks round it) as the surface marker.
 */
public record CrudeOilPocketFeature(IntProvider horizontalRadius, IntProvider verticalRadius, float seepChance) implements Feature {
	public static final MapCodec<CrudeOilPocketFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			IntProviders.codec(1, 8).fieldOf("horizontal_radius").forGetter(CrudeOilPocketFeature::horizontalRadius),
			IntProviders.codec(1, 6).fieldOf("vertical_radius").forGetter(CrudeOilPocketFeature::verticalRadius),
			Codec.floatRange(0.0f, 1.0f).fieldOf("seep_chance").forGetter(CrudeOilPocketFeature::seepChance))
			.apply(i, CrudeOilPocketFeature::new));
	/** Share of the solid shell turned to oil shale. */
	private static final float CAP_ROCK_CHANCE = 0.35f;
	/** How far down from the top block to look for real ground to stain (past a tree, say). */
	private static final int SURFACE_SEARCH = 16;

	@Override
	public MapCodec<CrudeOilPocketFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
		int rx = horizontalRadius.sample(random), rz = horizontalRadius.sample(random), ry = verticalRadius.sample(random);
		int sx = rx * 2 + 1, sy = ry * 2 + 1, sz = rz * 2 + 1;
		boolean[] oil = new boolean[sx * sy * sz];
		BlockState source = Industry.CRUDE_OIL_BLOCK.defaultBlockState();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int placed = 0;
		for (int dx = -rx; dx <= rx; dx++) {
			for (int dy = -ry; dy <= ry; dy++) {
				for (int dz = -rz; dz <= rz; dz++) {
					double d = sq(dx / (rx + 0.5)) + sq(dy / (ry + 0.5)) + sq(dz / (rz + 0.5));
					if (d > 1.0 - random.nextFloat() * 0.25) {
						continue;
					}
					pos.setWithOffset(origin, dx, dy, dz);
					if (level.getBlockState(pos).is(BlockTags.BASE_STONE_OVERWORLD)) {
						level.setBlock(pos, source, 2);
						oil[((dx + rx) * sy + dy + ry) * sz + dz + rz] = true;
						placed++;
					}
				}
			}
		}
		if (placed == 0) {
			return false;
		}

		// The shell: seal any opening, and cap some of the rest with shale.
		BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
		for (int dx = -rx; dx <= rx; dx++) {
			for (int dy = -ry; dy <= ry; dy++) {
				for (int dz = -rz; dz <= rz; dz++) {
					if (!oil[((dx + rx) * sy + dy + ry) * sz + dz + rz]) {
						continue;
					}
					pos.setWithOffset(origin, dx, dy, dz);
					for (Direction direction : Direction.values()) {
						int nx = dx + direction.getStepX(), ny = dy + direction.getStepY(), nz = dz + direction.getStepZ();
						if (Math.abs(nx) <= rx && Math.abs(ny) <= ry && Math.abs(nz) <= rz && oil[((nx + rx) * sy + ny + ry) * sz + nz + rz]) {
							continue;
						}
						next.setWithOffset(pos, direction);
						BlockState wall = level.getBlockState(next);
						boolean open = wall.isAir() || !wall.getFluidState().isEmpty() && !wall.is(Industry.CRUDE_OIL_BLOCK) || !wall.isSolid();
						if (open || wall.is(BlockTags.BASE_STONE_OVERWORLD) && random.nextFloat() < CAP_ROCK_CHANCE) {
							level.setBlock(next, shale(next.getY()), 2);
						}
					}
				}
			}
		}

		if (random.nextFloat() < seepChance) {
			placeSeep(level, origin.getX(), origin.getZ());
			for (int i = random.nextInt(3); i > 0; i--) {
				placeSeep(level, origin.getX() + random.nextInt(5) - 2, origin.getZ() + random.nextInt(5) - 2);
			}
		}
		return true;
	}

	/** Stains the top of the ground at this column, unless it's under water or there's no natural ground. */
	private static void placeSeep(WorldGenLevel level, int x, int z) {
		int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
		if (level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) > floor) {
			return;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, floor - 1, z);
		for (int i = 0; i < SURFACE_SEARCH && pos.getY() > level.getMinY(); i++, pos.move(Direction.DOWN)) {
			BlockState state = level.getBlockState(pos);
			if (state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.TERRACOTTA)
					|| state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.GRAVEL)) {
				level.setBlock(pos, Industry.OIL_SEEP.defaultBlockState(), 2);
				return;
			}
			if (!state.isAir() && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS) && !state.canBeReplaced()) {
				return;
			}
		}
	}

	private static BlockState shale(int y) {
		return (y < 0 ? Industry.DEEPSLATE_OIL_SHALE : Industry.OIL_SHALE).defaultBlockState();
	}

	private static double sq(double value) {
		return value * value;
	}
}
