package com.jheinz.tomyen.detector;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import com.jheinz.tomyen.fae.Fae;
import com.jheinz.tomyen.meteorite.Meteor;
import com.jheinz.tomyen.meteorite.Meteorites;

/**
 * Where the Overworld's Fae Rings and meteorite crash sites are, for the Dowsing Rods and the Meteorite Scanner.
 * <p>
 * Neither is a structure, so there's nothing to search for; instead each chunk is looked over as it loads (new or
 * from disk, so worlds from before the trackers existed fill in as they're explored). A ring is told by its Fae
 * Grass, which grows nowhere else; a crash site by its Meteoric Iron Ore and Budding Star Crystal. That's cheap: a
 * chunk section's block palette says whether it can hold any of them without reading a block, and few do. Chunks are
 * queued as they load and looked over a few a tick, so a burst of chunk loading never stalls a tick. A meteor that
 * lands mid-game has the chunks round it looked over again once it's gone.
 * <p>
 * Each landmark is kept as one position (the middle of what was found in a chunk; a site that spans chunks merges
 * into one within {@value #MERGE_DISTANCE} blocks) on the level (see {@link Detectors#LANDMARKS}). Reloading a chunk
 * whose ring or crash site has been dug out drops it, and so does standing near one (see {@link #nearest}).
 */
public final class Landmarks {
	public enum Kind {
		/** At least a few Fae Grass blocks: the bright patch inside a ring. */
		FAE_RING(state -> state.is(Fae.FAE_GRASS_BLOCK), 3),
		/** Meteoric iron ore and budding star crystal: what's left of a meteorite's core. */
		METEORITE(state -> state.is(Meteorites.METEORIC_IRON_ORE) || state.is(Meteorites.BUDDING_STAR_CRYSTAL), 4);

		final Predicate<BlockState> marker;
		final int minimum;

		Kind(Predicate<BlockState> marker, int minimum) {
			this.marker = marker;
			this.minimum = minimum;
		}
	}

	private static final int MERGE_DISTANCE = 12;
	private static final int CHUNKS_PER_TICK = 32;
	/** Within this many blocks of a landmark, a tracker looks again to see it's still there. */
	private static final int VERIFY_DISTANCE = 40;

	private static final Queue<Long> PENDING = new ConcurrentLinkedQueue<>();

	private Landmarks() {
	}

	static void initialize() {
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
			if (tracked(level)) {
				PENDING.add(chunk.getPos().pack());
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Meteor && tracked(level)) {
				ChunkPos centre = entity.chunkPosition();
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						PENDING.add(ChunkPos.pack(centre.x() + dx, centre.z() + dz));
					}
				}
			}
		});
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (!tracked(level)) {
				return;
			}
			for (int i = 0; i < CHUNKS_PER_TICK; i++) {
				Long key = PENDING.poll();
				if (key == null) {
					break;
				}
				LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
				if (chunk != null) {
					scan(level, chunk);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING.clear());
	}

	/** Only the Overworld has rings and crash sites (the Feywild is all fae, and its arrival rings take you home). */
	private static boolean tracked(Level level) {
		return level.dimension() == Level.OVERWORLD;
	}

	private static LandmarkData data(ServerLevel level) {
		LandmarkData data = level.getAttached(Detectors.LANDMARKS);
		return data == null ? LandmarkData.EMPTY : data;
	}

	/** Looks this chunk over for rings and crash sites, adding what's new and dropping what's gone. */
	static void scan(ServerLevel level, LevelChunk chunk) {
		for (Kind kind : Kind.values()) {
			update(level, kind, chunk.getPos(), find(level, chunk, kind));
		}
	}

	/** The middle of this kind's markers in the chunk, or null if there aren't enough of them. */
	private static @Nullable BlockPos find(ServerLevel level, LevelChunk chunk, Kind kind) {
		long x = 0, y = 0, z = 0;
		int count = 0;
		LevelChunkSection[] sections = chunk.getSections();
		for (int i = 0; i < sections.length; i++) {
			LevelChunkSection section = sections[i];
			if (section.hasOnlyAir() || !section.maybeHas(kind.marker)) {
				continue;
			}
			int baseY = SectionPos.sectionToBlockCoord(level.getSectionYFromSectionIndex(i));
			for (int dy = 0; dy < 16; dy++) {
				for (int dz = 0; dz < 16; dz++) {
					for (int dx = 0; dx < 16; dx++) {
						if (kind.marker.test(section.getBlockState(dx, dy, dz))) {
							x += dx;
							y += baseY + dy;
							z += dz;
							count++;
						}
					}
				}
			}
		}
		if (count < kind.minimum) {
			return null;
		}
		return new BlockPos(chunk.getPos().getMinBlockX() + (int) (x / count), (int) (y / count), chunk.getPos().getMinBlockZ() + (int) (z / count));
	}

	private static void update(ServerLevel level, Kind kind, ChunkPos chunk, @Nullable BlockPos found) {
		LandmarkData data = data(level);
		List<BlockPos> positions = new ArrayList<>(data.get(kind));
		int merge = MERGE_DISTANCE * MERGE_DISTANCE;
		boolean changed = positions.removeIf(pos -> pos.getX() >> 4 == chunk.x() && pos.getZ() >> 4 == chunk.z()
				&& (found == null || pos.distSqr(found) > merge));
		if (found != null && positions.stream().noneMatch(pos -> pos.distSqr(found) <= merge)) {
			positions.add(found);
			changed = true;
		}
		if (changed) {
			level.setAttached(Detectors.LANDMARKS, data.with(kind, positions));
		}
	}

	/**
	 * The nearest landmark of this kind within {@code range} blocks (measured flat), or null. One the player is
	 * close to is checked again first, in case it's been dug out since its chunk was last looked over.
	 */
	public static @Nullable BlockPos nearest(ServerLevel level, Kind kind, BlockPos from, int range) {
		if (!tracked(level)) {
			return null;
		}
		for (int tries = 0; tries < 3; tries++) {
			BlockPos best = null;
			double bestDistance = (double) range * range;
			for (BlockPos pos : data(level).get(kind)) {
				double dx = pos.getX() - from.getX(), dz = pos.getZ() - from.getZ();
				double d = dx * dx + dz * dz;
				if (d <= bestDistance) {
					best = pos;
					bestDistance = d;
				}
			}
			if (best == null || bestDistance > VERIFY_DISTANCE * VERIFY_DISTANCE) {
				return best;
			}
			LevelChunk chunk = level.getChunkSource().getChunkNow(best.getX() >> 4, best.getZ() >> 4);
			if (chunk == null) {
				return best;
			}
			BlockPos found = find(level, chunk, kind);
			update(level, kind, chunk.getPos(), found);
			if (found != null && found.distSqr(best) <= MERGE_DISTANCE * MERGE_DISTANCE) {
				return best;
			}
		}
		return null;
	}
}
