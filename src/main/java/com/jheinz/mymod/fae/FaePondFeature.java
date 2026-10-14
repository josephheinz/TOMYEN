package com.jheinz.mymod.fae;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * A Feywild pond: a still pool of source water a few blocks deep, sunk into the meadow so it sits level with its
 * banks. The outline is a main circle of {@code radius} blocks with two to four overlapping lobes, so no two ponds are
 * the same shape (most come out 5 to 14 blocks across); the bowl is one block deep at the rim and {@code max_depth} at
 * its heart.
 *
 * <p>The water's surface is set at the lowest point of the ground right round the pond, so every bank is at least
 * level with the water and nothing can spill; higher ground within three blocks of the water is graded down into a
 * gentle slope rather than left as a cliff. Sites that are too steep, run off the island's edge, or touch other water
 * are skipped, as is the island's centre, where everyone arrives.
 *
 * <p>Clay and sand line the bottom, with a little seagrass, glowing sea pickles in the deeper parts and glow lichen on
 * the submerged banks; lily pads float on top. The banks are grass and moss with moss carpet, Fae Blooms, the odd
 * Fae Mushroom and flowering azalea. Nothing is placed over the water, so it stays open to the sky for fishing.
 */
public record FaePondFeature(IntProvider radius, IntProvider maxDepth) implements Feature {
	public static final MapCodec<FaePondFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			IntProviders.codec(2, 6).fieldOf("radius").forGetter(FaePondFeature::radius),
			IntProviders.codec(1, 6).fieldOf("max_depth").forGetter(FaePondFeature::maxDepth))
			.apply(i, FaePondFeature::new));

	/** How many rings of bank round the water are graded and decorated. */
	private static final int SHORE = 3;
	/** The furthest any water can be from the centre: the lobes are kept inside this. */
	private static final double MAX_SPREAD = 7.5;
	/** Half the working grid: the water plus its shore. */
	private static final int REACH = (int) Math.ceil(MAX_SPREAD) + SHORE;
	private static final int SIZE = REACH * 2 + 1;
	/** How far above and below the placement height to look for the ground. */
	private static final int SEARCH = 10;
	/** Ground this much higher than the water anywhere near it makes the site too steep. */
	private static final int MAX_RISE = SHORE + 1;
	/** Keeps ponds away from the arrival ring at the island's centre. */
	private static final int CENTRE_CLEARANCE = 24;

	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final BlockState WATER = Blocks.WATER.defaultBlockState();
	private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
	private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
	private static final BlockState MOSS = Blocks.MOSS_BLOCK.defaultBlockState();

	@Override
	public MapCodec<FaePondFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
		if ((long) origin.getX() * origin.getX() + (long) origin.getZ() * origin.getZ() < CENTRE_CLEARANCE * CENTRE_CLEARANCE) {
			return false;
		}
		int depth = maxDepth.sample(random);

		// --- Outline: how far inside the pond each column is (0 at the rim, 1 at a lobe's centre), and its shore ring.
		float[] inside = shape(random, radius.sample(random));
		int[] ring = rings(inside);

		// --- The ground under every column the pond touches, and the water level it allows.
		int[] ground = new int[SIZE * SIZE];
		int water = Integer.MAX_VALUE;
		int highest = Integer.MIN_VALUE;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < ground.length; i++) {
			if (ring[i] < 0) {
				continue;
			}
			int g = ground(level, pos, origin.getX() + x(i), origin.getY(), origin.getZ() + z(i));
			if (g == Integer.MIN_VALUE) {
				return false;
			}
			ground[i] = g;
			highest = Math.max(highest, g);
			if (ring[i] == 1) {
				water = Math.min(water, g);
			}
		}
		if (highest - water > MAX_RISE || water - depth - 2 <= level.getMinY()) {
			return false;
		}
		// The bed needs solid ground under it, or the water would drain out the island's underside.
		for (int i = 0; i < ground.length; i++) {
			if (ring[i] == 0 && !level.getBlockState(pos.set(origin.getX() + x(i), water - depth - 1, origin.getZ() + z(i))).isSolidRender()) {
				return false;
			}
		}

		// --- Dig the bowl and fill it.
		BlockState bed = random.nextFloat() < 0.6f ? Blocks.CLAY.defaultBlockState() : Blocks.SAND.defaultBlockState();
		BlockState otherBed = bed.is(Blocks.CLAY) ? Blocks.SAND.defaultBlockState() : Blocks.CLAY.defaultBlockState();
		int[] floor = new int[SIZE * SIZE];
		for (int i = 0; i < ground.length; i++) {
			if (ring[i] != 0) {
				continue;
			}
			int x = origin.getX() + x(i), z = origin.getZ() + z(i);
			int d = 1 + Math.round((depth - 1) * (float) Math.sqrt(inside[i]));
			floor[i] = water - d;
			for (int y = Math.max(ground[i], water) + 1; y > water; y--) {
				setIfChanged(level, pos.set(x, y, z), AIR);
			}
			for (int y = water; y > floor[i]; y--) {
				level.setBlock(pos.set(x, y, z), WATER, 2);
			}
			level.setBlock(pos.set(x, floor[i], z), random.nextFloat() < 0.75f ? bed : otherBed, 2);
			// A dip lower than the bed (rare on these gentle slopes) is filled in rather than left hollow.
			for (int y = floor[i] - 1; y > floor[i] - SEARCH && !level.getBlockState(pos.set(x, y, z)).isSolidRender(); y--) {
				level.setBlock(pos, DIRT, 2);
			}
		}

		// --- Grade the banks: each ring of shore at most one block higher than the one inside it.
		int[] top = new int[SIZE * SIZE];
		for (int i = 0; i < ground.length; i++) {
			if (ring[i] <= 0) {
				continue;
			}
			int x = origin.getX() + x(i), z = origin.getZ() + z(i);
			top[i] = Math.min(ground[i], water + ring[i]);
			for (int y = ground[i]; y > top[i]; y--) {
				level.setBlock(pos.set(x, y, z), AIR, 2);
			}
			BlockState surface = random.nextFloat() < switch (ring[i]) {
				case 1 -> 0.35f;
				case 2 -> 0.18f;
				default -> 0.06f;
			} ? MOSS : GRASS;
			level.setBlock(pos.set(x, top[i], z), surface, 2);
		}

		decorateWater(level, random, origin, pos, ring, inside, floor, water);
		decorateBanks(level, random, origin, pos, ring, top);
		return true;
	}

	/** Lily pads on top; seagrass, sea pickles and glow lichen below. */
	private static void decorateWater(WorldGenLevel level, RandomSource random, BlockPos origin, BlockPos.MutableBlockPos pos, int[] ring,
			float[] inside, int[] floor, int water) {
		for (int i = 0; i < ring.length; i++) {
			if (ring[i] != 0) {
				continue;
			}
			int x = origin.getX() + x(i), z = origin.getZ() + z(i);
			boolean nearBank = inside[i] < 0.35f;
			if (random.nextFloat() < (nearBank ? 0.16f : 0.06f)) {
				placeIfSurvives(level, pos.set(x, water + 1, z), Blocks.LILY_PAD.defaultBlockState());
			}

			int deep = water - floor[i];
			boolean planted = false;
			if (deep >= 2) {
				float roll = random.nextFloat();
				if (roll < 0.06f) {
					planted = placeIfSurvives(level, pos.set(x, floor[i] + 1, z), Blocks.SEA_PICKLE.defaultBlockState()
							.setValue(SeaPickleBlock.PICKLES, 1 + random.nextInt(3)).setValue(SeaPickleBlock.WATERLOGGED, true));
				} else if (roll < 0.18f) {
					planted = placeIfSurvives(level, pos.set(x, floor[i] + 1, z), Blocks.SEAGRASS.defaultBlockState());
				}
			}

			// Glow lichen on the submerged face of the bank, here and there.
			for (int y = floor[i] + 1; y < water; y++) {
				if (planted && y == floor[i] + 1) {
					continue;
				}
				BlockState lichen = null;
				for (Direction direction : Direction.Plane.HORIZONTAL) {
					int n = index(x(i) + direction.getStepX(), z(i) + direction.getStepZ());
					if (n >= 0 && ring[n] > 0 && random.nextFloat() < 0.07f) {
						lichen = (lichen == null ? Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.WATERLOGGED, true) : lichen)
								.setValue(MultifaceBlock.getFaceProperty(direction), true);
					}
				}
				if (lichen != null) {
					placeIfSurvives(level, pos.set(x, y, z), lichen);
				}
			}
		}
	}

	/** Moss carpet on the moss; Fae Blooms, Fae Mushrooms and flowering azalea dotted round the water. */
	private static void decorateBanks(WorldGenLevel level, RandomSource random, BlockPos origin, BlockPos.MutableBlockPos pos, int[] ring,
			int[] top) {
		for (int i = 0; i < ring.length; i++) {
			if (ring[i] <= 0) {
				continue;
			}
			int x = origin.getX() + x(i), z = origin.getZ() + z(i);
			pos.set(x, top[i] + 1, z);
			if (!level.getBlockState(pos).isAir()) {
				continue;
			}
			float roll = random.nextFloat();
			BlockState plant = null;
			if (roll < 0.5f) {
				plant = level.getBlockState(pos.below()).is(Blocks.MOSS_BLOCK) ? Blocks.MOSS_CARPET.defaultBlockState() : null;
			} else if (roll < 0.58f) {
				plant = Fae.FAE_BLOOM.defaultBlockState();
			} else if (roll < 0.61f) {
				plant = ring[i] <= 2 ? Fae.FAE_MUSHROOM.defaultBlockState() : null;
			} else if (roll < 0.64f) {
				plant = ring[i] >= 2 ? Blocks.FLOWERING_AZALEA.defaultBlockState() : null;
			}
			if (plant != null) {
				placeIfSurvives(level, pos, plant);
			}
		}
	}

	/**
	 * The outline, as a grid: for each column, how far inside the pond it is, from just above 0 at the rim to 1 at the
	 * centre of a lobe, or 0 if it's dry.
	 */
	private static float[] shape(RandomSource random, int radius) {
		int lobes = 2 + random.nextInt(3);
		double[] cx = new double[lobes + 1], cz = new double[lobes + 1], r = new double[lobes + 1];
		r[0] = Math.min(radius + random.nextDouble() * 0.8, MAX_SPREAD - 1.0);
		float start = random.nextFloat() * Mth.TWO_PI;
		for (int l = 1; l <= lobes; l++) {
			float angle = start + l * Mth.TWO_PI / lobes + (random.nextFloat() - 0.5f) * 1.2f;
			double distance = r[0] * (0.45 + random.nextDouble() * 0.45);
			r[l] = Math.max(1.5, Math.min(r[0] * (0.45 + random.nextDouble() * 0.3), MAX_SPREAD - distance));
			cx[l] = Mth.cos(angle) * distance;
			cz[l] = Mth.sin(angle) * distance;
		}
		float[] inside = new float[SIZE * SIZE];
		for (int i = 0; i < inside.length; i++) {
			double best = 0.0;
			for (int l = 0; l <= lobes; l++) {
				double dx = x(i) - cx[l], dz = z(i) - cz[l];
				best = Math.max(best, 1.0 - (dx * dx + dz * dz) / (r[l] * r[l]));
			}
			inside[i] = (float) best;
		}
		return inside;
	}

	/** For each column: 0 if it's water, 1 to {@link #SHORE} for the rings of bank round it, -1 beyond. */
	private static int[] rings(float[] inside) {
		int[] ring = new int[SIZE * SIZE];
		for (int i = 0; i < ring.length; i++) {
			ring[i] = inside[i] > 0.0f ? 0 : -1;
		}
		for (int k = 1; k <= SHORE; k++) {
			for (int i = 0; i < ring.length; i++) {
				if (ring[i] != -1) {
					continue;
				}
				search:
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						int n = index(x(i) + dx, z(i) + dz);
						if (n >= 0 && ring[n] == k - 1) {
							ring[i] = -2; // marked this pass; set to k below so it doesn't seed this same pass
							break search;
						}
					}
				}
			}
			for (int i = 0; i < ring.length; i++) {
				if (ring[i] == -2) {
					ring[i] = k;
				}
			}
		}
		return ring;
	}

	/**
	 * The top solid block of the column, looking within {@link #SEARCH} blocks of {@code y}; {@link Integer#MIN_VALUE}
	 * if there's none (off the island's edge) or the column holds any fluid already.
	 */
	private static int ground(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z) {
		for (int dy = SEARCH; dy >= -SEARCH; dy--) {
			BlockState state = level.getBlockState(pos.set(x, y + dy, z));
			if (!state.getFluidState().isEmpty()) {
				return Integer.MIN_VALUE;
			}
			if (state.isSolidRender()) {
				return y + dy;
			}
		}
		return Integer.MIN_VALUE;
	}

	private static boolean placeIfSurvives(WorldGenLevel level, BlockPos pos, BlockState state) {
		if (state.canSurvive(level, pos)) {
			level.setBlock(pos, state, 2);
			return true;
		}
		return false;
	}

	private static void setIfChanged(WorldGenLevel level, BlockPos pos, BlockState state) {
		if (level.getBlockState(pos) != state) {
			level.setBlock(pos, state, 2);
		}
	}

	private static int x(int index) {
		return index / SIZE - REACH;
	}

	private static int z(int index) {
		return index % SIZE - REACH;
	}

	/** The grid index of a column relative to the centre, or -1 if it's off the grid. */
	private static int index(int x, int z) {
		if (x < -REACH || x > REACH || z < -REACH || z > REACH) {
			return -1;
		}
		return (x + REACH) * SIZE + (z + REACH);
	}
}
