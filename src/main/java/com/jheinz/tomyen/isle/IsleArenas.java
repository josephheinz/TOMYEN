package com.jheinz.tomyen.isle;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Where each player's Revenant waits at each depth. Every player has their own arena per depth, at a spot worked out
 * from their UUID: on a bearing of its own from the arrival shrine, {@link IsleDepth#arenaDistance} out (give or take
 * a little), turned a further two radians or so each depth down so the three don't line up. Two players' arenas
 * landing close together is harmless: each Revenant only rises for its own owner.
 *
 * <p>The arena itself is built the first time its owner comes near ({@link IsleStructures#buildArena}), then
 * remembered in the hoard.
 */
public final class IsleArenas {
	private IsleArenas() {
	}

	/** The centre of the owner's arena at that depth, at y 0 (the height is found when it's built). */
	public static BlockPos centre(UUID owner, IsleDepth depth) {
		long seed = owner.getMostSignificantBits() * 31 + owner.getLeastSignificantBits();
		double bearing = ((seed & 0xFFFF) / 65536.0) * Math.PI * 2.0 + depth.index * 2.1;
		int distance = depth.arenaDistance + (int) ((seed >>> 24) & 31) - 16;
		return new BlockPos((int) Math.round(Math.cos(bearing) * distance), 0, (int) Math.round(Math.sin(bearing) * distance));
	}

	/** Where the hoard's Revenant waits now: its built arena, or where it will be built (at sea level). */
	public static BlockPos target(ServerLevel level, Hoard hoard) {
		return hoard.arena().orElseGet(() -> centre(hoard.owner(), hoard.depth()).atY(level.getSeaLevel()));
	}

	/** The arena for the hoard's current depth, built (and recorded in the hoard) if it isn't yet. */
	public static BlockPos ensureBuilt(ServerLevel level, Hoard hoard) {
		if (hoard.arena().isPresent()) {
			return hoard.arena().get();
		}
		BlockPos built = IsleStructures.buildArena(level, centre(hoard.owner(), hoard.depth()), hoard.depth());
		Hoards.put(level.getServer(), hoard.withArena(built));
		return built;
	}
}
