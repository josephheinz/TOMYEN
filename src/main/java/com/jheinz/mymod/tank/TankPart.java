package com.jheinz.mymod.tank;

import net.minecraft.util.Mth;

/**
 * The parts of a {@link Tank} that hits can damage on top of its hull integrity, each with a condition from 0
 * (wrecked) to 1 (sound). All four are packed a byte each into one int so they sync as a single value.
 * <ul>
 * <li>Tracks: top speed and turning.
 * <li>Engine: top speed and acceleration.
 * <li>Turret ring: how fast the turret traverses and the gun elevates.
 * <li>Gun: reload time and accuracy.
 * </ul>
 * None of them ever stops the tank outright: a wrecked part only slows it down to its floor (see the getters
 * on {@link Tank}). Iron patches them up along with the hull.
 */
public enum TankPart {
	TRACKS("tracks"),
	ENGINE("engine"),
	TURRET_RING("turret_ring"),
	GUN("gun");

	/** Every part sound. */
	public static final int ALL_SOUND = 0xFFFFFFFF;

	private final String id;

	TankPart(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	/** "hud.mymod.tank.part.tracks" and so on. */
	public String nameKey() {
		return "hud.mymod.tank.part." + id;
	}

	/** This part's condition in a packed set, 0 to 1. */
	public float condition(int packed) {
		return ((packed >>> (ordinal() * 8)) & 0xFF) / 255.0f;
	}

	/** The packed set with this part's condition changed. */
	public int with(int packed, float condition) {
		int shift = ordinal() * 8;
		int value = Math.round(Mth.clamp(condition, 0.0f, 1.0f) * 255.0f);
		return packed & ~(0xFF << shift) | value << shift;
	}

	/** Whether every part in a packed set is sound. */
	public static boolean allSound(int packed) {
		return packed == ALL_SOUND;
	}
}
