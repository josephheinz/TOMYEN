package com.jheinz.tomyen.client.uranium;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import com.jheinz.tomyen.uranium.GeigerCounterItem;
import com.jheinz.tomyen.uranium.Radiation;
import com.jheinz.tomyen.uranium.Uranium;

/**
 * Makes a held Geiger Counter click. Like a real one, the clicks come at random: each tick there's a chance of one,
 * {@link #BACKGROUND} with nothing about, rising with the activity around the player. Every ore block within
 * {@link GeigerCounterItem#RANGE} adds 1/d² (d its distance, at least 1), and what the player carries adds a share
 * of the dose it gives them, so one ore block an arm's length away makes about ten clicks a second, and one at the
 * edge of range barely shows over the background. The surroundings are rescanned every {@link #SCAN_TICKS} ticks.
 */
public final class GeigerCounterClient {
	private static final float BACKGROUND = 0.015f;
	private static final float CLICKS_PER_ACTIVITY = 0.5f;
	private static final float MAX_CHANCE = 0.9f;
	/** Activity per tick of dose a second the player's inventory gives them. */
	private static final float CARRIED_ACTIVITY = 0.004f;
	private static final int SCAN_TICKS = 10;

	private static float activity;
	private static int scanIn;

	private GeigerCounterClient() {
	}

	public static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || client.level == null || client.isPaused()
				|| !player.getMainHandItem().is(Uranium.GEIGER_COUNTER) && !player.getOffhandItem().is(Uranium.GEIGER_COUNTER)) {
			scanIn = 0;
			return;
		}
		if (--scanIn <= 0) {
			scanIn = SCAN_TICKS;
			activity = scan(player) + Radiation.carriedDose(player.getInventory()) * CARRIED_ACTIVITY;
		}
		RandomSource random = player.getRandom();
		if (random.nextFloat() < Math.min(MAX_CHANCE, BACKGROUND + activity * CLICKS_PER_ACTIVITY)) {
			client.level.playLocalSound(player.getX(), player.getY() + 1.0, player.getZ(), Uranium.GEIGER_CLICK, SoundSource.PLAYERS,
					0.5f, 0.9f + random.nextFloat() * 0.25f, false);
		}
	}

	private static float scan(LocalPlayer player) {
		int range = GeigerCounterItem.RANGE;
		BlockPos centre = player.blockPosition().above();
		float total = 0.0f;
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-range, -range, -range), centre.offset(range, range, range))) {
			if (player.level().getBlockState(pos).is(Uranium.URANIUM_ORES)) {
				double distance = Math.sqrt(pos.distSqr(centre));
				if (distance <= range) {
					total += 1.0f / (float) Mth.square(Math.max(1.0, distance));
				}
			}
		}
		return total;
	}
}
