package com.jheinz.tomyen.client.detector;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongConsumer;
import java.util.function.Predicate;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Finds the blocks matching a test round a moving point, a few chunk sections a tick (as the Neuralink's OreScanner
 * does), so a held detector never scans a whole volume in one tick. Sections whose block palette can't hold a match
 * are skipped without reading a block. The sections in range are cycled nearest first, so the whole area is fresh
 * about every {@code queue / perTick} ticks; the found blocks are kept per section between visits.
 */
final class SectionScanner {
	/** Half a section's diagonal: a section can hold blocks in range when its centre is this much further out. */
	private static final double SECTION_REACH = 14.0;

	private final double range;
	private final int perTick;
	private final Long2ObjectMap<long[]> found = new Long2ObjectOpenHashMap<>();
	private final Map<BlockState, Boolean> matches = new IdentityHashMap<>();
	private Predicate<BlockState> test = state -> false;
	private @Nullable Object key;
	private long[] queue = new long[0];
	private int cursor;
	private long queueCentre = Long.MIN_VALUE;
	private @Nullable ClientLevel level;

	SectionScanner(double range, int perTick) {
		this.range = range;
		this.perTick = perTick;
	}

	/** Scans the next few sections round this point for blocks passing the test, which is told apart by its key. */
	void tick(ClientLevel level, BlockPos centre, Object key, Predicate<BlockState> test) {
		if (level != this.level || !Objects.equals(key, this.key)) {
			reset();
			this.level = level;
			this.key = key;
			this.test = test;
		}
		long section = SectionPos.asLong(centre);
		if (section != queueCentre) {
			queueCentre = section;
			rebuildQueue(level, SectionPos.of(centre));
		}
		for (int i = 0; i < perTick && i < queue.length; i++) {
			scan(level, queue[cursor]);
			cursor = (cursor + 1) % queue.length;
		}
	}

	void reset() {
		found.clear();
		matches.clear();
		queue = new long[0];
		cursor = 0;
		queueCentre = Long.MIN_VALUE;
		level = null;
		key = null;
	}

	/** Every matching block found so far, as {@link BlockPos#asLong} positions (some may be a little out of range). */
	void forEach(LongConsumer action) {
		for (long[] positions : found.values()) {
			for (long pos : positions) {
				action.accept(pos);
			}
		}
	}

	private boolean matches(BlockState state) {
		return matches.computeIfAbsent(state, test::test);
	}

	private void rebuildQueue(ClientLevel level, SectionPos centre) {
		int radius = (int) Math.ceil((range + SECTION_REACH) / 16.0);
		double reach = (range + SECTION_REACH) * (range + SECTION_REACH);
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
		found.keySet().removeIf(key -> !keep.contains(key));
	}

	private static int distance(SectionPos centre, long section) {
		int dx = SectionPos.x(section) - centre.x(), dy = SectionPos.y(section) - centre.y(), dz = SectionPos.z(section) - centre.z();
		return dx * dx + dy * dy + dz * dz;
	}

	private void scan(ClientLevel level, long key) {
		int sx = SectionPos.x(key), sy = SectionPos.y(key), sz = SectionPos.z(key);
		LevelChunk chunk = level.getChunkSource().getChunk(sx, sz, ChunkStatus.FULL, false);
		LongArrayList hits = new LongArrayList();
		if (chunk != null) {
			LevelChunkSection section = chunk.getSection(level.getSectionIndexFromSectionY(sy));
			if (!section.hasOnlyAir() && section.maybeHas(this::matches)) {
				int x0 = SectionPos.sectionToBlockCoord(sx), y0 = SectionPos.sectionToBlockCoord(sy), z0 = SectionPos.sectionToBlockCoord(sz);
				for (int y = 0; y < 16; y++) {
					for (int z = 0; z < 16; z++) {
						for (int x = 0; x < 16; x++) {
							if (matches(section.getBlockState(x, y, z))) {
								hits.add(BlockPos.asLong(x0 + x, y0 + y, z0 + z));
							}
						}
					}
				}
			}
		}
		if (hits.isEmpty()) {
			found.remove(key);
		} else {
			found.put(key, hits.toLongArray());
		}
	}
}
