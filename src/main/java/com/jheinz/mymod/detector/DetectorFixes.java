package com.jheinz.mymod.detector;

import java.util.Optional;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;

import com.jheinz.mymod.werewolf.WerewolfCurse;

/**
 * Once a second, for each player holding a tracker that needs the server: the nearest ring or crash site from
 * {@link Landmarks}, and for the Lunar Almanac whether a cursed villager is about (which only the server knows). The
 * answer goes on the player as {@link Detectors#FIX}, set only when it changes, so it costs nothing on the wire while
 * nothing moves. Players holding none of them have it cleared.
 */
public final class DetectorFixes {
	private static final int INTERVAL = 20;

	private DetectorFixes() {
	}

	static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(DetectorFixes::tick);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if ((server.getTickCount() + player.getId()) % INTERVAL == 0) {
				update(player);
			}
		}
	}

	private static void update(ServerPlayer player) {
		ServerLevel level = player.level();
		int rodRange = Detectors.holds(player, Detectors.FAE_DOWSING_ROD) ? Detectors.FAE_DOWSING_ROD_RANGE
				: Detectors.holds(player, Detectors.DOWSING_ROD) ? Detectors.DOWSING_ROD_RANGE : 0;
		boolean scanner = Detectors.holds(player, Detectors.METEORITE_SCANNER);
		boolean almanac = Detectors.holds(player, Detectors.LUNAR_ALMANAC);
		DetectorFix fix = DetectorFix.NONE;
		if (rodRange > 0 || scanner || almanac) {
			fix = new DetectorFix(
					rodRange > 0 ? Optional.ofNullable(Landmarks.nearest(level, Landmarks.Kind.FAE_RING, player.blockPosition(), rodRange))
							: Optional.empty(),
					scanner ? Optional.ofNullable(Landmarks.nearest(level, Landmarks.Kind.METEORITE, player.blockPosition(),
							Detectors.METEORITE_SCANNER_RANGE)) : Optional.empty(),
					almanac && !level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(Detectors.OMEN_RANGE),
							WerewolfCurse::isCursed).isEmpty());
		}
		DetectorFix old = player.getAttached(Detectors.FIX);
		if (fix.equals(DetectorFix.NONE)) {
			if (old != null) {
				player.removeAttached(Detectors.FIX);
			}
		} else if (!fix.equals(old)) {
			player.setAttached(Detectors.FIX, fix);
		}
	}
}
