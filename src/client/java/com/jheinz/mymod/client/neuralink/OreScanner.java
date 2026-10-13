package com.jheinz.mymod.client.neuralink;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import com.jheinz.mymod.neuralink.NeuralinkItem;
import com.jheinz.mymod.neuralink.NeuralinkModule;
import com.jheinz.mymod.tungsten.Tungsten;
import com.jheinz.mymod.uranium.Uranium;

/**
 * Finds the ores around a player wearing an x-ray module. It cycles through the chunk sections in range a
 * few per tick, nearest first, so the whole area is refreshed about once a second without a hitch, and skips
 * sections whose block palette has no ore at all.
 */
public final class OreScanner {
	/** An ore block, its highlight colour (RGB), and which faces (bit per {@link Direction}) don't touch the same ore. */
	public record Ore(BlockPos pos, int color, int openFaces) {
	}

	private static final int SECTIONS_PER_TICK = 20;
	private static final double RANGE = NeuralinkModule.XRAY_RANGE;
	/** Half a section's diagonal: a section can hold blocks in range when its centre is this much further out. */
	private static final double SECTION_REACH = 14.0;

	private static final Long2ObjectMap<List<Ore>> FOUND = new Long2ObjectOpenHashMap<>();
	/** Highlight colour per block state, 0 for anything that isn't an ore. */
	private static final Map<BlockState, Integer> COLORS = new IdentityHashMap<>();
	private static long[] queue = new long[0];
	private static int cursor;
	private static long queueCentre = Long.MIN_VALUE;
	private static ClientLevel scannedLevel;
	private static List<Ore> snapshot = List.of();
	private static boolean changed;

	private OreScanner() {
	}

	public static void tick(Minecraft minecraft) {
		Player player = minecraft.player;
		ClientLevel level = minecraft.level;
		if (player == null || level == null || !NeuralinkItem.isActive(player, NeuralinkModule.XRAY)) {
			reset();
			return;
		}
		if (level != scannedLevel) {
			reset();
			scannedLevel = level;
		}
		long centre = SectionPos.asLong(player.blockPosition());
		if (centre != queueCentre) {
			queueCentre = centre;
			rebuildQueue(level, player);
		}
		for (int i = 0; i < SECTIONS_PER_TICK && i < queue.length; i++) {
			scan(level, queue[cursor]);
			cursor = (cursor + 1) % queue.length;
		}
	}

	/** Every ore found so far, within range of where the player was when its section was last scanned. */
	public static List<Ore> ores() {
		if (changed) {
			List<Ore> all = new ArrayList<>();
			FOUND.values().forEach(all::addAll);
			snapshot = all;
			changed = false;
		}
		return snapshot;
	}

	private static void reset() {
		if (scannedLevel != null || !FOUND.isEmpty()) {
			FOUND.clear();
			// Tags can change between worlds and servers.
			COLORS.clear();
			queue = new long[0];
			cursor = 0;
			queueCentre = Long.MIN_VALUE;
			scannedLevel = null;
			snapshot = List.of();
			changed = false;
		}
	}

	private static void rebuildQueue(ClientLevel level, Player player) {
		SectionPos centre = SectionPos.of(player.blockPosition());
		int radius = (int) Math.ceil((RANGE + SECTION_REACH) / 16.0);
		double reach = (RANGE + SECTION_REACH) * (RANGE + SECTION_REACH);
		LongArrayList sections = new LongArrayList();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				int sy = centre.y() + dy;
				if (sy < level.getMinSectionY() || sy > level.getMaxSectionY()) {
					continue;
				}
				for (int dz = -radius; dz <= radius; dz++) {
					if ((dx * dx + dy * dy + dz * dz) * 256.0 <= reach) {
						sections.add(SectionPos.asLong(centre.x() + dx, sy, centre.z() + dz));
					}
				}
			}
		}
		sections.sort((a, b) -> Integer.compare(distance(centre, a), distance(centre, b)));
		queue = sections.toLongArray();
		cursor = 0;
		LongOpenHashSet keep = new LongOpenHashSet(queue);
		if (FOUND.keySet().removeIf(key -> !keep.contains(key))) {
			changed = true;
		}
	}

	private static int distance(SectionPos centre, long section) {
		int dx = SectionPos.x(section) - centre.x(), dy = SectionPos.y(section) - centre.y(), dz = SectionPos.z(section) - centre.z();
		return dx * dx + dy * dy + dz * dz;
	}

	private static void scan(ClientLevel level, long key) {
		int sx = SectionPos.x(key), sy = SectionPos.y(key), sz = SectionPos.z(key);
		LevelChunk chunk = level.getChunkSource().getChunk(sx, sz, ChunkStatus.FULL, false);
		List<Ore> ores = new ArrayList<>();
		if (chunk != null) {
			LevelChunkSection section = chunk.getSection(level.getSectionIndexFromSectionY(sy));
			if (!section.hasOnlyAir() && section.maybeHas(state -> color(state) != 0)) {
				BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();
				for (int y = 0; y < 16; y++) {
					for (int z = 0; z < 16; z++) {
						for (int x = 0; x < 16; x++) {
							int color = color(section.getBlockState(x, y, z));
							if (color == 0) {
								continue;
							}
							BlockPos pos = new BlockPos(SectionPos.sectionToBlockCoord(sx) + x, SectionPos.sectionToBlockCoord(sy) + y,
									SectionPos.sectionToBlockCoord(sz) + z);
							int open = 0;
							for (Direction direction : Direction.values()) {
								if (color(level.getBlockState(neighbour.setWithOffset(pos, direction))) != color) {
									open |= 1 << direction.ordinal();
								}
							}
							ores.add(new Ore(pos, color, open));
						}
					}
				}
			}
		}
		List<Ore> previous = ores.isEmpty() ? FOUND.remove(key) : FOUND.put(key, ores);
		if (previous != null || !ores.isEmpty()) {
			changed = true;
		}
	}

	private static int color(BlockState state) {
		return COLORS.computeIfAbsent(state, OreScanner::classify);
	}

	private static int classify(BlockState state) {
		if (state.is(BlockItemTags.DIAMOND_ORES.block())) return 0x4DF5FF;
		if (state.is(BlockItemTags.EMERALD_ORES.block())) return 0x2DFF6A;
		if (state.is(BlockTags.GOLD_ORES)) return 0xFFD83D;
		if (state.is(BlockItemTags.REDSTONE_ORES.block())) return 0xFF2A2A;
		if (state.is(BlockItemTags.LAPIS_ORES.block())) return 0x3A66FF;
		if (state.is(BlockTags.IRON_ORES)) return 0xE8B898;
		if (state.is(BlockTags.COPPER_ORES)) return 0xFF7F3F;
		if (state.is(BlockItemTags.COAL_ORES.block())) return 0x8C8C8C;
		if (state.is(Blocks.ANCIENT_DEBRIS)) return 0xB0603A;
		if (state.is(Blocks.NETHER_QUARTZ_ORE)) return 0xF2EDE4;
		if (state.is(Uranium.URANIUM_ORES)) return 0x9CFF3A;
		if (state.is(Tungsten.TUNGSTEN_ORE)) return 0x8C96A8;
		return state.is(BlockTags.ORES) ? 0xFFFFFF : 0;
	}
}
