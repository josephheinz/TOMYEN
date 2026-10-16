package com.jheinz.mymod.sodajetpack;

/**
 * The jetpack's misbehaviour, worked out from the game time and the wearer's entity id so the client (which flies the
 * player) and the server (which plays the sounds and burns the fuel) agree on it without sending anything: when it
 * sputters, when it spins you out and which way, and how it wobbles.
 */
public final class JetpackChaos {
	/** A sputter lasts this many ticks. */
	public static final int SPUTTER_WINDOW = 4;
	/** A spin-out lasts this many ticks. */
	public static final int SPIN_WINDOW = 16;
	/** Degrees a spin-out turns you each tick. */
	public static final float SPIN_DEGREES = 22.0f;

	private JetpackChaos() {
	}

	/** A number in [0, 1) from two inputs, well mixed (SplitMix64's finaliser). */
	public static float hash(long a, long b) {
		long z = a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL + 0x165667B19E3779F9L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		z ^= z >>> 31;
		return (z >>> 40) / (float) (1L << 24);
	}

	public static boolean sputters(long time, int id, JetpackMode mode) {
		return hash(Math.floorDiv(time, SPUTTER_WINDOW), id) < mode.sputterChance;
	}

	/** Whether this tick's the first of a sputter (for its sound). */
	public static boolean sputterStarts(long time, int id, JetpackMode mode) {
		return Math.floorMod(time, SPUTTER_WINDOW) == 0 && sputters(time, id, mode);
	}

	public static boolean spins(long time, int id, JetpackMode mode) {
		return hash(Math.floorDiv(time, SPIN_WINDOW), id * 31L + 7L) < mode.spinChance;
	}

	public static boolean spinStarts(long time, int id, JetpackMode mode) {
		return Math.floorMod(time, SPIN_WINDOW) == 0 && spins(time, id, mode);
	}

	/** Which way a spin-out turns: -1 or 1. */
	public static int spinDirection(long time, int id) {
		return hash(Math.floorDiv(time, SPIN_WINDOW), id * 17L + 3L) < 0.5f ? -1 : 1;
	}

	/** A wobble in [-1, 1] for one axis this tick. */
	public static float wobble(long time, int id, int axis) {
		return hash(time, id * 7L + axis * 1013L) * 2.0f - 1.0f;
	}
}
