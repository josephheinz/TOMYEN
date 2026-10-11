package com.jheinz.mymod;

import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;

/**
 * Client-side recoil: each shot kicks the view up (rotation set on the client reaches the server with the
 * next movement packet), kicks the held gun back in an animation that settles, and adds bloom to the
 * crosshair. A burst queues one kick per round so they land on the same ticks as the shots.
 */
public final class GunRecoil {
	/** How much of the gun's recoil goes into turning the view; the rest shows in the gun animation. */
	private static final float VIEW_KICK = 0.7f;
	/** How quickly the kick settles (milliseconds to fall to about a third). */
	private static final float SETTLE_MS = 110.0f;
	/** Kicks stack during rapid fire, up to this much. */
	private static final float MAX_KICK = 3.5f;

	private static int pendingKicks;
	private static GunStats pendingStats;
	private static float kickAtLastShot;
	private static long lastShotMillis;
	private static float roll;

	private GunRecoil() {
	}

	/** Kicks now, then once per tick for the remaining {@code shots - 1}. */
	public static void queue(Player player, int shots, GunStats stats) {
		kick(player, stats);
		pendingKicks = shots - 1;
		pendingStats = stats;
	}

	/** Called every client tick. */
	public static void tick(Player player) {
		if (pendingKicks > 0 && player != null) {
			kick(player, pendingStats);
			pendingKicks--;
		}
	}

	private static void kick(Player player, GunStats stats) {
		float degrees = stats.recoilDegrees();
		player.setXRot(player.getXRot() - degrees * VIEW_KICK);
		player.setYRot(player.getYRot() + (player.getRandom().nextFloat() - 0.5f) * degrees * VIEW_KICK * 0.5f);
		GunSpread.clientShot(stats);
		kickAtLastShot = Math.min(MAX_KICK, kick() + degrees / 2.5f);
		lastShotMillis = Util.getMillis();
		roll = player.getRandom().nextFloat() * 2.0f - 1.0f;
	}

	/** How far the gun is kicked right now: about 1 per 2.5 degrees of the gun's recoil, settling to 0. */
	public static float kick() {
		return kickAtLastShot * (float) Math.exp(-(Util.getMillis() - lastShotMillis) / SETTLE_MS);
	}

	/** Which way (and how much, -1 to 1) the last shot twisted the gun. */
	public static float roll() {
		return roll;
	}
}
