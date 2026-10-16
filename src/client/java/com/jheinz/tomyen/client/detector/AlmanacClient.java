package com.jheinz.tomyen.client.detector;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;

import com.jheinz.tomyen.detector.Detectors;
import com.jheinz.tomyen.werewolf.Werewolf;

/**
 * A held Lunar Almanac's two feelings: the server's word that a cursed villager is near ({@code omen}), which sighs
 * once as it comes on, and a werewolf on the prowl, which the client can see for itself (checked twice a second).
 */
final class AlmanacClient {
	private static final int HUNTER_RANGE = 48;

	private static boolean omen;
	private static boolean hunted;

	private AlmanacClient() {
	}

	static void tick(Minecraft minecraft, LocalPlayer player) {
		if (DetectorClient.held(player, Detectors.LUNAR_ALMANAC).isEmpty()) {
			omen = false;
			hunted = false;
			return;
		}
		boolean now = DetectorClient.fix(player).omen();
		if (now && !omen) {
			DetectorClient.play(minecraft, player, Detectors.ALMANAC_OMEN, 0.5f, 0.8f);
		}
		omen = now;
		if (player.tickCount % 10 == 0) {
			hunted = !minecraft.level.getEntitiesOfClass(Werewolf.class, new AABB(player.blockPosition()).inflate(HUNTER_RANGE)).isEmpty();
		}
	}

	static boolean omen() {
		return omen;
	}

	static boolean hunted() {
		return hunted;
	}
}
