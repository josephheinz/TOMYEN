package com.jheinz.tomyen.meteorite;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Builds meteorite crash sites, for worldgen and for meteors falling now, so both look alike.
 * <p>
 * A site is a bowl dug {@code craterRadius} blocks out from the centre (a little lumpy, never quite round) and about
 * half that deep, its floor scorched to blackstone, basalt and coarse dirt with magma near the middle, ringed by a
 * raised rim of thrown-up soil, and with ejecta (meteorite stone, blackstone, basalt) scattered over the ground beyond.
 * The core sits half-buried in the bowl, layered like a geode (see {@link #core}).
 * <p>
 * The crater's level is the average of the ground around its rim, so it settles into gentle slopes. Water or ice
 * anywhere around it, or ground more than one and a half times {@code craterRadius} out of level, rules the spot out
 * (unless the slope is forced, as a falling meteor must land somewhere). Trees, plants and snow over the crater and its rim are
 * swept away.
 */
public final class MeteoriteGenerator {
	/** A ground search that ran into water (or ice), or found nothing to stand on. */
	private static final int WATER = Integer.MIN_VALUE;
	private static final int NO_GROUND = Integer.MIN_VALUE + 1;
	/** How far down from the top block to look for the real ground, past a tall tree. */
	private static final int SURFACE_SEARCH = 48;
	/** How high above the crater floor and rim things are cleared. */
	private static final int CLEAR_HEIGHT = 32;
	/** Points around the rim (and half way in) checked for water and slope. */
	private static final int SAMPLES = 16;
	/** Ejecta lands up to this far out; with the worst-placed origin that stays inside the chunks a feature may write. */
	private static final int MAX_REACH = 14;

	/** What each block of a core is, while building it. */
	private static final byte NONE = 0, SHELL = 1, ORE = 2, LINING = 3, HOLLOW = 4;

	private MeteoriteGenerator() {
	}

	/**
	 * Digs a crash site centred on this column, with a core of {@code coreRadius} (plus a half) in it.
	 *
	 * @param fresh a meteor that just landed: hotter (more magma, fires burning), any slope will do, no crystals yet
	 * @return false if the spot was no good, and nothing was changed
	 */
	public static boolean place(WorldGenLevel level, RandomSource random, int x, int z, int craterRadius, int coreRadius, boolean fresh, int flags) {
		OptionalInt base = siteLevel(level, x, z, craterRadius, fresh);
		if (base.isEmpty()) {
			return false;
		}
		int h = base.getAsInt();
		int depth = Math.max(2, Math.round(craterRadius * 0.5f));
		List<BlockPos> floor = dig(level, random, x, z, h, craterRadius, depth, fresh, flags);
		core(level, random, new BlockPos(x, h - depth + 1, z), coreRadius, !fresh, !fresh && random.nextBoolean(), true, flags);
		ejecta(level, random, x, z, craterRadius, fresh, flags);
		if (fresh) {
			for (BlockPos pos : floor) {
				BlockPos above = pos.above();
				if (random.nextFloat() < 0.15f && level.getBlockState(above).isAir()) {
					level.setBlock(above, BaseFireBlock.getState(level, above), flags);
				}
			}
		}
		return true;
	}

	/** A core set down on the ground here without digging anything: it only fills air and plants. */
	public static void placeCoreOnly(WorldGenLevel level, RandomSource random, int x, int z, int coreRadius, int flags) {
		int g = ground(level, x, z);
		if (g != WATER && g != NO_GROUND) {
			core(level, random, new BlockPos(x, g + 1, z), coreRadius, false, false, false, flags);
		}
	}

	/** The height to dig the crater from, or empty if this is no place for one. */
	private static OptionalInt siteLevel(WorldGenLevel level, int x, int z, int radius, boolean anySlope) {
		int centre = ground(level, x, z);
		if (centre == WATER || centre == NO_GROUND) {
			return OptionalInt.empty();
		}
		int min = centre, max = centre, sum = centre, count = 1;
		for (int i = 0; i < SAMPLES; i++) {
			double angle = i * Mth.TWO_PI / SAMPLES;
			double r = i % 2 == 0 ? radius + 1 : radius * 0.5;
			int g = ground(level, x + (int) Math.round(Math.cos(angle) * r), z + (int) Math.round(Math.sin(angle) * r));
			if (g == WATER || g == NO_GROUND && !anySlope) {
				return OptionalInt.empty();
			}
			if (g == NO_GROUND) {
				continue;
			}
			min = Math.min(min, g);
			max = Math.max(max, g);
			if (i % 2 == 0) {
				sum += g;
				count++;
			}
		}
		if (!anySlope && max - min > radius * 3 / 2) {
			return OptionalInt.empty();
		}
		return OptionalInt.of(Math.round((float) sum / count));
	}

	/** Shapes the bowl, the rim and the scorched ground round it; returns the floor's top blocks. */
	private static List<BlockPos> dig(WorldGenLevel level, RandomSource random, int x, int z, int h, int radius, int depth, boolean fresh,
			int flags) {
		int rimWidth = Math.max(2, radius / 3);
		int rimHeight = radius >= 6 ? 2 : 1;
		int reach = Math.min(MAX_REACH, radius + rimWidth + 3);
		// Two slow lobes round the edge, so no two craters are the same circle.
		double phase2 = random.nextDouble() * Mth.TWO_PI, phase3 = random.nextDouble() * Mth.TWO_PI;
		double lobe2 = 0.04 + random.nextDouble() * 0.06, lobe3 = random.nextDouble() * 0.05;
		List<BlockPos> floor = new ArrayList<>();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				double dist = Math.sqrt(dx * dx + dz * dz);
				if (dist > reach + 0.5) {
					continue;
				}
				double theta = Math.atan2(dz, dx);
				double edge = radius * (1.0 + lobe2 * Math.sin(2 * theta + phase2) + lobe3 * Math.sin(3 * theta + phase3));
				double d = dist / edge;
				int cx = x + dx, cz = z + dz;
				int g = ground(level, cx, cz);
				if (g == WATER || g == NO_GROUND) {
					continue;
				}
				if (d <= 1.0) {
					// The bowl: a parabola from the base level down to the floor at the centre.
					int floorY = h - (int) Math.round(depth * (1.0 - d * d));
					int top = Math.max(g, level.getHeight(Heightmap.Types.WORLD_SURFACE, cx, cz) - 1);
					for (int y = floorY + 1; y <= Math.min(top, floorY + CLEAR_HEIGHT); y++) {
						pos.set(cx, y, cz);
						if (!level.getBlockState(pos).isAir()) {
							level.setBlock(pos, Blocks.AIR.defaultBlockState(), flags);
						}
					}
					for (int y = g + 1; y < floorY; y++) {
						level.setBlock(pos.set(cx, y, cz), underlayer(random), flags);
					}
					level.setBlock(pos.set(cx, floorY, cz), floorBlock(random, d, fresh), flags);
					level.setBlock(pos.set(cx, floorY - 1, cz), underlayer(random), flags);
					if (random.nextBoolean() && level.getBlockState(pos.set(cx, floorY - 2, cz)).isSolid()) {
						level.setBlock(pos, underlayer(random), flags);
					}
					floor.add(new BlockPos(cx, floorY, cz));
				} else {
					double t = (dist - edge) / rimWidth;
					clearDebris(level, cx, g + 1, cz, flags);
					if (t <= 1.0) {
						// The rim: soil thrown up over the edge, highest right at it.
						int raise = (int) Math.round(rimHeight * (1.0 - t) + random.nextFloat() * 0.6 - 0.3);
						BlockState fill = bared(level.getBlockState(pos.set(cx, g, cz)));
						for (int i = 1; i <= raise; i++) {
							level.setBlock(pos.set(cx, g + i, cz), i == raise ? rimTop(random) : random.nextFloat() < 0.6f ? fill : underlayer(random), flags);
						}
						if (raise <= 0 && random.nextFloat() < 0.6f) {
							level.setBlock(pos.set(cx, g, cz), rimTop(random), flags);
						}
					} else if (random.nextFloat() < 0.45 * (1.0 - (t - 1.0) * rimWidth / 3.0)) {
						// Scorch marks fading out beyond the rim.
						pos.set(cx, g, cz);
						if (isSoil(level.getBlockState(pos))) {
							level.setBlock(pos, random.nextFloat() < 0.7f ? Blocks.COARSE_DIRT.defaultBlockState() : rimTop(random), flags);
						}
					}
				}
			}
		}
		return floor;
	}

	/**
	 * A meteorite core of radius {@code radius + 0.5}, a little squat and lumpy, centred here: an outer shell of
	 * Meteorite Stone (with a fleck of ore), a layer about half Meteoric Iron Ore, a lining of mostly Budding Star
	 * Crystal (some plain star crystal), and a hollow inside. With {@code crystals}, some of the lining has already
	 * grown buds and clusters into the hollow; with {@code crack}, a fissure splits the top open on one side so the
	 * glow shows. Unless {@code carve}, it only fills air and plants.
	 */
	private static void core(WorldGenLevel level, RandomSource random, BlockPos centre, int radius, boolean crystals, boolean crack, boolean carve,
			int flags) {
		double outer = radius + 0.5;
		int n = (int) Math.ceil(outer);
		int size = 2 * n + 1;
		byte[] layers = new byte[size * size * size];
		double p1 = random.nextDouble() * Mth.TWO_PI, p2 = random.nextDouble() * Mth.TWO_PI;
		double crackAngle = random.nextDouble() * Mth.TWO_PI;
		double cos = Math.cos(crackAngle), sin = Math.sin(crackAngle);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -n; dx <= n; dx++) {
			for (int dy = -n; dy <= n; dy++) {
				for (int dz = -n; dz <= n; dz++) {
					double dist = Math.sqrt(dx * dx + dy * 1.15 * dy * 1.15 + dz * dz);
					double e = dist;
					if (dist > 0) {
						e += 0.3 * Math.sin(3.0 * dx / dist + p1) * Math.cos(2.0 * dz / dist + p2) + 0.15 * Math.sin(2.5 * dy / dist + p1);
					}
					if (e > outer) {
						continue;
					}
					byte layer = e > outer - 1 ? SHELL : e > outer - 2 ? ORE : e > outer - 3 ? LINING : HOLLOW;
					if (crack && dy >= 1 && dx * cos + dz * sin > 0 && Math.abs(-dx * sin + dz * cos) < 0.8) {
						layer = HOLLOW;
					}
					pos.setWithOffset(centre, dx, dy, dz);
					if (!carve && !level.getBlockState(pos).canBeReplaced()) {
						continue;
					}
					BlockState state = switch (layer) {
						case SHELL -> (random.nextFloat() < 0.08f ? Meteorites.METEORIC_IRON_ORE : Meteorites.METEORITE_STONE).defaultBlockState();
						case ORE -> (random.nextFloat() < 0.55f ? Meteorites.METEORIC_IRON_ORE : Meteorites.METEORITE_STONE).defaultBlockState();
						case LINING -> (random.nextFloat() < 0.7f ? Meteorites.BUDDING_STAR_CRYSTAL : Meteorites.STAR_CRYSTAL_BLOCK).defaultBlockState();
						default -> Blocks.AIR.defaultBlockState();
					};
					if (layer == LINING && state.is(Meteorites.STAR_CRYSTAL_BLOCK)) {
						layer = SHELL; // grows nothing
					}
					level.setBlock(pos, state, flags);
					layers[((dx + n) * size + dy + n) * size + dz + n] = layer;
				}
			}
		}
		if (!crystals) {
			return;
		}
		Block[] stages = {Meteorites.SMALL_STAR_CRYSTAL_BUD, Meteorites.MEDIUM_STAR_CRYSTAL_BUD, Meteorites.LARGE_STAR_CRYSTAL_BUD,
				Meteorites.STAR_CRYSTAL_CLUSTER};
		BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
		for (int dx = -n; dx <= n; dx++) {
			for (int dy = -n; dy <= n; dy++) {
				for (int dz = -n; dz <= n; dz++) {
					if (layers[((dx + n) * size + dy + n) * size + dz + n] != LINING) {
						continue;
					}
					pos.setWithOffset(centre, dx, dy, dz);
					for (Direction direction : Direction.values()) {
						int nx = dx + direction.getStepX(), ny = dy + direction.getStepY(), nz = dz + direction.getStepZ();
						if (Math.max(Math.abs(nx), Math.max(Math.abs(ny), Math.abs(nz))) > n
								|| layers[((nx + n) * size + ny + n) * size + nz + n] != HOLLOW || random.nextFloat() >= 0.3f) {
							continue;
						}
						next.setWithOffset(pos, direction);
						if (level.getBlockState(next).isAir()) {
							level.setBlock(next, stages[random.nextInt(stages.length)].defaultBlockState()
									.setValue(AmethystClusterBlock.FACING, direction), flags);
						}
					}
				}
			}
		}
	}

	/** Rocks flung out past the rim, alone or in pairs. */
	private static void ejecta(WorldGenLevel level, RandomSource random, int x, int z, int radius, boolean fresh, int flags) {
		int count = radius + random.nextInt(radius + 1);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < count; i++) {
			double angle = random.nextDouble() * Mth.TWO_PI;
			double dist = Math.min(MAX_REACH, radius * (1.15 + random.nextDouble() * 0.75));
			int ex = x + (int) Math.round(Math.cos(angle) * dist), ez = z + (int) Math.round(Math.sin(angle) * dist);
			int g = ground(level, ex, ez);
			if (g == WATER || g == NO_GROUND) {
				continue;
			}
			int pile = random.nextFloat() < 0.3f ? 2 : 1;
			for (int j = 1; j <= pile; j++) {
				pos.set(ex, g + j, ez);
				if (!level.getBlockState(pos).canBeReplaced()) {
					break;
				}
				level.setBlock(pos, debris(random, fresh), flags);
			}
			// Don't leave the top half of a tall plant standing on a rock.
			pos.move(Direction.UP);
			if (level.getBlockState(pos).canBeReplaced() && !level.getBlockState(pos).isAir()) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), flags);
			}
		}
	}

	/**
	 * The top of the ground in this column: the highest solid block that isn't a tree, a plant or snow. WATER if
	 * the search runs into water, lava or ice first.
	 */
	private static int ground(WorldGenLevel level, int x, int z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
		for (int i = 0; i < SURFACE_SEARCH && pos.getY() > level.getMinY(); i++, pos.move(Direction.DOWN)) {
			BlockState state = level.getBlockState(pos);
			if (!state.getFluidState().isEmpty() || state.is(BlockTags.ICE)) {
				return WATER;
			}
			if (state.isSolid() && !isDebris(state)) {
				return pos.getY();
			}
		}
		return NO_GROUND;
	}

	/** Above ground and swept away by the blast: trees, plants, snow. */
	private static boolean isDebris(BlockState state) {
		return state.canBeReplaced() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || state.is(BlockTags.REPLACEABLE_BY_TREES)
				|| state.is(Blocks.CACTUS) || state.is(Blocks.BAMBOO) || state.is(Blocks.PUMPKIN) || state.is(Blocks.MELON)
				|| state.is(Blocks.BEE_NEST) || state.is(Blocks.MUSHROOM_STEM) || state.is(Blocks.RED_MUSHROOM_BLOCK)
				|| state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.SNOW);
	}

	/** Clears trees and plants standing on the ground from here up. */
	private static void clearDebris(WorldGenLevel level, int x, int fromY, int z, int flags) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = fromY; y < fromY + CLEAR_HEIGHT; y++) {
			BlockState state = level.getBlockState(pos.set(x, y, z));
			if (state.isAir()) {
				continue;
			}
			if (!isDebris(state) || !state.getFluidState().isEmpty()) {
				return;
			}
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), flags);
		}
	}

	private static boolean isSoil(BlockState state) {
		return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(BlockTags.BASE_STONE_OVERWORLD)
				|| state.is(BlockTags.TERRACOTTA) || state.is(Blocks.SNOW_BLOCK);
	}

	/** The rim is made of whatever the ground is, bared of its grass. */
	private static BlockState bared(BlockState ground) {
		if (ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.PODZOL) || ground.is(Blocks.MYCELIUM) || ground.is(Blocks.MOSS_BLOCK)
				|| !isSoil(ground)) {
			return Blocks.DIRT.defaultBlockState();
		}
		return ground;
	}

	/** The scorched floor: hottest (magma, blackstone, basalt) near the middle, coarse dirt and gravel to the edge. */
	private static BlockState floorBlock(RandomSource random, double d, boolean fresh) {
		float r = random.nextFloat();
		if (d < 0.75) {
			return (r < (fresh ? 0.3f : 0.18f) ? Blocks.MAGMA_BLOCK : r < 0.5f ? Blocks.BLACKSTONE : r < 0.72f ? Blocks.BASALT
					: r < 0.86f ? Blocks.SMOOTH_BASALT : Blocks.COARSE_DIRT).defaultBlockState();
		}
		return (r < (fresh ? 0.1f : 0.05f) ? Blocks.MAGMA_BLOCK : r < 0.3f ? Blocks.BLACKSTONE : r < 0.42f ? Blocks.BASALT
				: r < 0.55f ? Blocks.GRAVEL : Blocks.COARSE_DIRT).defaultBlockState();
	}

	private static BlockState underlayer(RandomSource random) {
		float r = random.nextFloat();
		return (r < 0.45f ? Blocks.BLACKSTONE : r < 0.8f ? Blocks.COARSE_DIRT : Blocks.TUFF).defaultBlockState();
	}

	private static BlockState rimTop(RandomSource random) {
		float r = random.nextFloat();
		return (r < 0.5f ? Blocks.COARSE_DIRT : r < 0.68f ? Blocks.GRAVEL : r < 0.82f ? Blocks.BLACKSTONE : r < 0.94f ? Blocks.BASALT
				: Meteorites.METEORITE_STONE).defaultBlockState();
	}

	private static BlockState debris(RandomSource random, boolean fresh) {
		float r = random.nextFloat();
		if (fresh && r < 0.12f) {
			return Blocks.MAGMA_BLOCK.defaultBlockState();
		}
		return (r < 0.45f ? Meteorites.METEORITE_STONE : r < 0.7f ? Blocks.BLACKSTONE : r < 0.85f ? Blocks.BASALT : Blocks.COARSE_DIRT)
				.defaultBlockState();
	}
}
