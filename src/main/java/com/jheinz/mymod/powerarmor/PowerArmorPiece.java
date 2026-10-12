package com.jheinz.mymod.powerarmor;

import net.minecraft.network.chat.Component;

/**
 * The six pieces a suit of power armor is built from, in station-slot order. Each has a Mk I durability (scaled
 * up by its mark, see {@link PowerArmorTier#durabilityScale()}) and a weight: how often a hit with no known
 * direction (fire, a cactus, poison gas) lands on it.
 */
public enum PowerArmorPiece {
	HELMET("helmet", 320, 14),
	TORSO("torso", 520, 30),
	LEFT_ARM("left_arm", 340, 14),
	RIGHT_ARM("right_arm", 340, 14),
	LEFT_LEG("left_leg", 380, 14),
	RIGHT_LEG("right_leg", 380, 14);

	public static final int COUNT = values().length;

	private final String id;
	private final int baseDurability;
	private final int hitWeight;

	PowerArmorPiece(String id, int baseDurability, int hitWeight) {
		this.id = id;
		this.baseDurability = baseDurability;
		this.hitWeight = hitWeight;
	}

	public String id() {
		return id;
	}

	/** The slot this piece takes in a suit (and in the station). */
	public int slot() {
		return ordinal();
	}

	public int durability(PowerArmorTier tier) {
		return Math.round(baseDurability * tier.durabilityScale());
	}

	public int hitWeight() {
		return hitWeight;
	}

	public boolean isArm() {
		return this == LEFT_ARM || this == RIGHT_ARM;
	}

	public boolean isLeg() {
		return this == LEFT_LEG || this == RIGHT_LEG;
	}

	/** The piece's name in readouts: "Helmet", "Left Arm" and so on. */
	public Component displayName() {
		return Component.translatable("power_armor_piece.mymod." + id);
	}

	/** A three-letter tag for tight readouts (the HUD's silhouette panel). */
	public Component shortName() {
		return Component.translatable("power_armor_piece.mymod." + id + ".short");
	}
}
