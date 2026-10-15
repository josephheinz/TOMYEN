package com.jheinz.mymod.client.detector;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;

import com.jheinz.mymod.detector.Detectors;
import com.jheinz.mymod.fae.Fae;
import com.jheinz.mymod.meteorite.Meteor;

/**
 * The long-range trackers, which point at what the server found for them ({@link Detectors#FIX}): the Dowsing Rods
 * at the nearest Fae Ring, the Meteorite Scanner at the nearest crash site. Here they keep time: the rod twitches and
 * the scanner pings more often the closer the target, and each sounds once on arrival. The scanner also catches
 * meteors falling nearby (they're tracked from far off), which aren't crash sites yet.
 */
final class TrackerClient {
	/** Within this many blocks (flat) the rod dips and the scanner says you're there. */
	static final int ARRIVED = 10;
	private static final int METEOR_WATCH = 256;

	private static int rodIn;
	private static int scannerIn;
	private static boolean rodArrived;
	private static boolean scannerArrived;
	private static @Nullable Meteor meteor;

	private TrackerClient() {
	}

	static void tick(Minecraft minecraft, LocalPlayer player) {
		Item rod = rod(player);
		BlockPos ring = target(player, rod);
		if (ring != null) {
			double distance = DetectorClient.flatDistance(player, ring.getX() + 0.5, ring.getZ() + 0.5);
			boolean arrived = distance < ARRIVED;
			if (arrived && !rodArrived) {
				DetectorClient.play(minecraft, player, Fae.RING_CHIME, 0.7f, 1.2f);
			}
			rodArrived = arrived;
			if (--rodIn <= 0) {
				float closeness = closeness(distance, range(rod));
				rodIn = Math.round(Mth.lerp(closeness, 60.0f, 14.0f));
				DetectorClient.play(minecraft, player, Detectors.DOWSING_TWITCH, 0.25f + 0.3f * closeness, 0.9f + 0.6f * closeness);
			}
		} else {
			rodArrived = false;
		}

		boolean scanning = !DetectorClient.held(player, Detectors.METEORITE_SCANNER).isEmpty();
		BlockPos site = target(player, scanning ? Detectors.METEORITE_SCANNER : null);
		if (site != null) {
			double distance = DetectorClient.flatDistance(player, site.getX() + 0.5, site.getZ() + 0.5);
			boolean arrived = distance < ARRIVED;
			if (arrived && !scannerArrived) {
				DetectorClient.play(minecraft, player, Detectors.SCANNER_ARRIVED, 0.7f, 1.0f);
			}
			scannerArrived = arrived;
			if (--scannerIn <= 0) {
				float closeness = closeness(distance, Detectors.METEORITE_SCANNER_RANGE);
				scannerIn = Math.round(Mth.lerp(closeness, 50.0f, 10.0f));
				DetectorClient.play(minecraft, player, Detectors.SCANNER_PING, 0.3f + 0.2f * closeness, 0.8f + 0.8f * closeness);
			}
		} else {
			scannerArrived = false;
		}
		if (!scanning) {
			meteor = null;
		} else if (player.tickCount % 10 == 0 || meteor != null && meteor.isRemoved()) {
			meteor = minecraft.level.getEntitiesOfClass(Meteor.class, new AABB(player.blockPosition()).inflate(METEOR_WATCH)).stream()
					.findFirst().orElse(null);
		}
	}

	private static float closeness(double distance, int range) {
		return Mth.clamp(1.0f - (float) (distance / range), 0.0f, 1.0f);
	}

	/** The dowsing rod held (the fae one first), or null. */
	static @Nullable Item rod(LocalPlayer player) {
		if (!DetectorClient.held(player, Detectors.FAE_DOWSING_ROD).isEmpty()) {
			return Detectors.FAE_DOWSING_ROD;
		}
		return DetectorClient.held(player, Detectors.DOWSING_ROD).isEmpty() ? null : Detectors.DOWSING_ROD;
	}

	static int range(@Nullable Item rod) {
		return rod == Detectors.FAE_DOWSING_ROD ? Detectors.FAE_DOWSING_ROD_RANGE : Detectors.DOWSING_ROD_RANGE;
	}

	/** What this tracker (a rod or the scanner, held) points at, or null. */
	static @Nullable BlockPos target(LocalPlayer player, @Nullable Item tracker) {
		if (tracker == null || DetectorClient.held(player, tracker).isEmpty()) {
			return null;
		}
		Optional<BlockPos> pos = tracker == Detectors.METEORITE_SCANNER ? DetectorClient.fix(player).meteorite()
				: DetectorClient.fix(player).faeRing();
		return pos.orElse(null);
	}

	static @Nullable Meteor meteor() {
		return meteor;
	}
}
