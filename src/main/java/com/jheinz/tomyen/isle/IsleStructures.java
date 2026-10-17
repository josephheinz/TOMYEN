package com.jheinz.tomyen.isle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * What gets built on the Isle as it's needed, rather than by world generation: the arrival shrine at the origin with
 * its Lantern of Return, each Revenant's arena, and the Return Rift that opens when one is beaten.
 *
 * <p>Both shrine and arena are round floors of grave stone bricks levelled at the ground's middle height (and never
 * below the sea, so they're always reachable, if need be by swimming): terrain above the floor is cut away, and gaps
 * below it are filled with ashen stone down to the ground or sea floor, so one built over water becomes an islet. A
 * ring of broken pillars and soul braziers stands round each; the arena's grow more ruined the deeper it lies.
 */
public final class IsleStructures {
	private static final int ARENA_FLOOR = 7;
	private static final int ARENA_RIM = 10;
	private static final int CLEARANCE = 9;
	private static final int SHRINE_RADIUS = 5;
	/** The lantern stands this far north of the shrine's centre. */
	private static final int LANTERN_OFFSET = 4;

	private IsleStructures() {
	}

	// --- The arrival shrine -------------------------------------------------------------------------------------------

	/** Where arrivals stand: the shrine's centre, built first if it isn't there. */
	public static BlockPos arrival(ServerLevel level) {
		BlockPos lantern = findLantern(level);
		if (lantern != null) {
			return lantern.offset(0, 0, LANTERN_OFFSET);
		}
		BlockPos centre = levelOut(level, BlockPos.ZERO, SHRINE_RADIUS + 2, SHRINE_RADIUS, level.getRandom(), IsleDepth.SHORE, false);
		level.setBlock(centre.north(LANTERN_OFFSET), Isle.LANTERN_OF_RETURN.defaultBlockState(), Block.UPDATE_ALL);
		RandomSource random = level.getRandom();
		for (int i = 0; i < 4; i++) {
			double angle = Math.PI / 4 + i * Math.PI / 2;
			BlockPos base = centre.offset((int) Math.round(Math.cos(angle) * 5), 0, (int) Math.round(Math.sin(angle) * 5));
			pillar(level, base, 3, random, IsleDepth.SHORE);
			level.setBlock(base.above(3), Isle.SOUL_BRAZIER.defaultBlockState(), Block.UPDATE_ALL);
		}
		return centre;
	}

	/** The Lantern of Return, if the shrine's been built: looked for straight down its column. */
	public static BlockPos findLantern(ServerLevel level) {
		level.getChunk(0, -1);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(0, level.getMaxY(), -LANTERN_OFFSET);
		for (int y = level.getMaxY(); y >= level.getMinY(); y--) {
			pos.setY(y);
			if (level.getBlockState(pos).is(Isle.LANTERN_OF_RETURN)) {
				return pos.immutable();
			}
		}
		return null;
	}

	// --- Arenas -------------------------------------------------------------------------------------------------------

	/** Builds a Revenant's arena round {@code at} (its height is ignored); returns the centre of its floor. */
	public static BlockPos buildArena(ServerLevel level, BlockPos at, IsleDepth depth) {
		RandomSource random = RandomSource.create(at.asLong() * 31 + depth.index);
		BlockPos centre = levelOut(level, at, ARENA_RIM, ARENA_FLOOR, random, depth, true);
		int pillars = 8;
		for (int i = 0; i < pillars; i++) {
			double angle = i * Math.PI * 2 / pillars + 0.2;
			BlockPos base = centre.offset((int) Math.round(Math.cos(angle) * 9), 0, (int) Math.round(Math.sin(angle) * 9));
			int height = 2 + random.nextInt(4) - depth.index;
			pillar(level, base, Math.max(1, height), random, depth);
		}
		for (int i = 0; i < 4; i++) {
			double angle = Math.PI / 4 + i * Math.PI / 2;
			BlockPos brazier = centre.offset((int) Math.round(Math.cos(angle) * 6), 0, (int) Math.round(Math.sin(angle) * 6));
			level.setBlock(brazier, Isle.SOUL_BRAZIER.defaultBlockState(), Block.UPDATE_ALL);
		}
		int remains = 5 + depth.index * 4;
		for (int i = 0; i < remains; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double r = 3.5 + random.nextDouble() * 4.5;
			BlockPos spot = centre.offset((int) Math.round(Math.cos(angle) * r), 0, (int) Math.round(Math.sin(angle) * r));
			if (!level.getBlockState(spot).isAir()) {
				continue;
			}
			Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(random);
			if (random.nextInt(3) == 0) {
				level.setBlock(spot, Isle.OLD_HEADSTONE.defaultBlockState().setValue(OldHeadstoneBlock.FACING, facing)
						.setValue(OldHeadstoneBlock.LEAN, random.nextInt(3)), Block.UPDATE_ALL);
			} else {
				level.setBlock(spot, Isle.BONE_PILE.defaultBlockState().setValue(BonePileBlock.FACING, facing), Block.UPDATE_ALL);
			}
		}
		return centre;
	}

	/**
	 * Levels a round floor of bricks ({@code floor} blocks across the middle, ash or moss out to {@code rim}) at the
	 * middle height of the ground there, cuts away what's above and fills what's below; returns the floor's centre
	 * (the block above it).
	 */
	private static BlockPos levelOut(ServerLevel level, BlockPos at, int rim, int floor, RandomSource random, IsleDepth depth, boolean ragged) {
		for (int dx = -rim - 1; dx <= rim + 1; dx += 16) {
			for (int dz = -rim - 1; dz <= rim + 1; dz += 16) {
				level.getChunk((at.getX() + dx) >> 4, (at.getZ() + dz) >> 4);
			}
		}
		level.getChunk((at.getX() + rim + 1) >> 4, (at.getZ() + rim + 1) >> 4);
		int sea = level.getSeaLevel();
		List<Integer> heights = new ArrayList<>();
		for (int dx = -rim; dx <= rim; dx++) {
			for (int dz = -rim; dz <= rim; dz++) {
				if (dx * dx + dz * dz <= rim * rim) {
					heights.add(groundY(level, at.getX() + dx, at.getZ() + dz));
				}
			}
		}
		Integer[] sorted = heights.toArray(Integer[]::new);
		Arrays.sort(sorted);
		int floorY = Math.max(sea + 1, Math.min(sorted[sorted.length / 2], sea + 40));
		BlockPos centre = new BlockPos(at.getX(), floorY, at.getZ());

		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -rim - 1; dx <= rim + 1; dx++) {
			for (int dz = -rim - 1; dz <= rim + 1; dz++) {
				double r = Math.sqrt(dx * dx + dz * dz);
				boolean edge = r > rim;
				if (r > rim + 1.0 || (edge && (!ragged || random.nextInt(3) != 0))) {
					continue;
				}
				int x = at.getX() + dx, z = at.getZ() + dz;
				// Clear the space above the floor.
				for (int y = floorY; y < floorY + CLEARANCE; y++) {
					pos.set(x, y, z);
					if (!level.getBlockState(pos).isAir()) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
				// The floor, and solid ground under it down to wherever the ground really is.
				pos.set(x, floorY - 1, z);
				level.setBlock(pos, r <= floor ? floorBlock(random, depth, r) : rimBlock(random, depth), Block.UPDATE_CLIENTS);
				for (int y = floorY - 2; y > level.getMinY(); y--) {
					pos.set(x, y, z);
					BlockState state = level.getBlockState(pos);
					if (!state.isAir() && state.getFluidState().isEmpty() && !state.canBeReplaced()) {
						break;
					}
					level.setBlock(pos, y >= floorY - 3 && r > floor ? Isle.ASH_SOIL.defaultBlockState() : Isle.ASHEN_STONE.defaultBlockState(),
							Block.UPDATE_CLIENTS);
				}
			}
		}
		return centre;
	}

	/**
	 * The height just above the ground (or the sea floor) in a column: the world surface, less any water, plants, logs
	 * and leaves standing on the ground.
	 */
	public static int groundY(ServerLevel level, int x, int z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
		while (pos.getY() > level.getMinY()) {
			BlockState state = level.getBlockState(pos);
			if (!state.isAir() && state.getFluidState().isEmpty() && !state.canBeReplaced() && !state.is(BlockTags.LOGS)
					&& !state.is(BlockTags.LEAVES)) {
				break;
			}
			pos.move(Direction.DOWN);
		}
		return pos.getY() + 1;
	}

	private static BlockState floorBlock(RandomSource random, IsleDepth depth, double r) {
		if (r < 1.5) {
			return Isle.MOSSY_GRAVE_STONE_BRICKS.defaultBlockState();
		}
		int roll = random.nextInt(10);
		if (roll < 1 + depth.index * 2) {
			return Isle.CRACKED_GRAVE_STONE_BRICKS.defaultBlockState();
		}
		if (roll < 3 + depth.index * 2) {
			return Isle.MOSSY_GRAVE_STONE_BRICKS.defaultBlockState();
		}
		return Isle.GRAVE_STONE_BRICKS.defaultBlockState();
	}

	private static BlockState rimBlock(RandomSource random, IsleDepth depth) {
		return switch (depth) {
			case HOLLOWS -> random.nextInt(3) == 0 ? Isle.ASH_SOIL.defaultBlockState() : Isle.SOUL_MOSS.defaultBlockState();
			case ABYSS -> random.nextInt(3) == 0 ? Blocks.SOUL_SOIL.defaultBlockState() : Isle.ASHEN_STONE.defaultBlockState();
			default -> Isle.ASH_SOIL.defaultBlockState();
		};
	}

	/** A broken pillar of bricks, {@code height} tall, capped with a wall stub or slab where it snapped. */
	private static void pillar(ServerLevel level, BlockPos base, int height, RandomSource random, IsleDepth depth) {
		for (int y = 0; y < height; y++) {
			BlockState state = random.nextInt(4) < 1 + depth.index ? Isle.CRACKED_GRAVE_STONE_BRICKS.defaultBlockState()
					: random.nextInt(3) == 0 ? Isle.MOSSY_GRAVE_STONE_BRICKS.defaultBlockState() : Isle.GRAVE_STONE_BRICKS.defaultBlockState();
			level.setBlock(base.above(y), state, Block.UPDATE_CLIENTS);
		}
		BlockPos top = base.above(height);
		if (random.nextBoolean()) {
			level.setBlock(top, Isle.GRAVE_STONE_BRICK_WALL.defaultBlockState(), Block.UPDATE_ALL);
		} else {
			level.setBlock(top, Isle.GRAVE_STONE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), Block.UPDATE_ALL);
		}
	}

	// --- After a Revenant falls ---------------------------------------------------------------------------------------

	/** Opens a Return Rift three blocks north of the arena's centre (or wherever's clear nearby). */
	public static BlockPos openRift(ServerLevel level, BlockPos centre) {
		BlockPos[] tries = {centre.north(3), centre.south(3), centre.east(3), centre.west(3), centre.north(2), centre.above()};
		for (BlockPos pos : tries) {
			if (level.getBlockState(pos).canBeReplaced() && level.getBlockState(pos.above()).canBeReplaced()) {
				level.setBlock(pos, Isle.RETURN_RIFT.defaultBlockState(), Block.UPDATE_ALL);
				return pos;
			}
		}
		level.setBlock(centre.north(3), Isle.RETURN_RIFT.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(centre.north(3).above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		return centre.north(3);
	}

	/** Spots for {@code count} Soul Caches round the arena's centre, the centre first. */
	public static List<BlockPos> cacheSpots(BlockPos centre, int count) {
		List<BlockPos> spots = new ArrayList<>();
		BlockPos[] order = {centre, centre.east(), centre.west(), centre.south(), centre.offset(1, 0, 1), centre.offset(-1, 0, 1),
				centre.offset(2, 0, 0), centre.offset(-2, 0, 0)};
		for (int i = 0; i < count; i++) {
			spots.add(i < order.length ? order[i] : centre.offset(-3 + (i - order.length) % 7, 0, 2 + (i - order.length) / 7));
		}
		return spots;
	}
}
