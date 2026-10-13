package com.jheinz.mymod.mech;

import net.minecraft.util.Mth;

/**
 * The parts of a {@link Mech} that hits can damage on top of its hull integrity, each with a condition from 0
 * (wrecked) to 1 (sound), packed a byte each into one int so they sync as a single value.
 * <ul>
 * <li>Torso: how efficiently the power plant runs (a battered torso burns more fuel).
 * <li>Legs: walking speed, turning and the jump jets' lift.
 * <li>Each arm: how often its weapon jams (never above {@link MechArm#JAM_FREE_CONDITION}).
 * </ul>
 * None of them stops the walker outright. Iron or steel patches them up along with the hull; docked in a gantry,
 * each part's condition is kept as its item's wear.
 */
public enum MechPart {
	TORSO("torso", MechLoadout.TORSO),
	LEGS("legs", MechLoadout.LEGS),
	LEFT_ARM("left_arm", MechLoadout.LEFT_ARM),
	RIGHT_ARM("right_arm", MechLoadout.RIGHT_ARM);

	/** Every part sound. */
	public static final int ALL_SOUND = 0xFFFFFFFF;

	private final String id;
	private final int slot;

	MechPart(String id, int slot) {
		this.id = id;
		this.slot = slot;
	}

	public String id() {
		return id;
	}

	/** The loadout slot whose item this part is. */
	public int slot() {
		return slot;
	}

	/** "hud.mymod.mech.part.legs" and so on. */
	public String nameKey() {
		return "hud.mymod.mech.part." + id;
	}

	public static MechPart arm(int side) {
		return side == MechArm.LEFT ? LEFT_ARM : RIGHT_ARM;
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

	public static boolean allSound(int packed) {
		return packed == ALL_SOUND;
	}
}
