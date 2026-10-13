package com.jheinz.mymod.mech;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

/**
 * What a {@link Mech}'s arm hardpoint carries (see {@link MechArm} for how each one fires).
 * <ul>
 * <li>Autocannon: explosive-tipped rounds, thirty to an Autocannon Drum.
 * <li>Rotary Cannon: belt-fed rifle rounds (5.56 or 7.62) straight from the stores once its barrels spin up.
 * <li>Power Fist: tap to punch (smashing weak blocks), hold to grab a mob, player or dropped item, tap to throw it.
 * <li>Rocket Pod: four PG-7s at a time, reloaded from the stores; homing ones lock onto what's under the sight.
 * <li>Flamer: a napalm stream, a canister to a load.
 * </ul>
 * An empty hardpoint is a bare forearm stub.
 */
public enum MechWeapon {
	NONE("none", 0, 0.0f),
	AUTOCANNON("autocannon", 30, 33.0f),
	ROTARY_CANNON("rotary_cannon", 0, 26.0f),
	POWER_FIST("power_fist", 0, 22.0f),
	ROCKET_POD("rocket_pod", 4, 13.0f),
	FLAMER("flamer", 200, 24.0f);

	private final String id;
	private final int magazine;
	private final float reach;

	MechWeapon(String id, int magazine, float reach) {
		this.id = id;
		this.magazine = magazine;
		this.reach = reach;
	}

	public String id() {
		return id;
	}

	/** "hud.mymod.mech.weapon.autocannon" and so on. */
	public String nameKey() {
		return "hud.mymod.mech.weapon." + id;
	}

	/** Rounds (or, for the flamer, units of gel) a load holds; 0 for one fed straight from the stores, or no ammo at all. */
	public int magazine() {
		return magazine;
	}

	/** How far ahead of the elbow the muzzle (or the fist's knuckles) is, in model pixels. */
	public float reach() {
		return reach;
	}

	public static MechWeapon byOrdinal(int ordinal) {
		MechWeapon[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NONE;
	}

	/** The item a weapon is, or null for none. */
	public @Nullable Item item() {
		return switch (this) {
			case AUTOCANNON -> Mechs.AUTOCANNON;
			case ROTARY_CANNON -> Mechs.ROTARY_CANNON;
			case POWER_FIST -> Mechs.POWER_FIST;
			case ROCKET_POD -> Mechs.ROCKET_POD;
			case FLAMER -> Mechs.FLAMER;
			case NONE -> null;
		};
	}

	/** The weapon an item is, or {@link #NONE}. */
	public static MechWeapon of(ItemStack stack) {
		for (MechWeapon weapon : values()) {
			if (weapon != NONE && stack.is(weapon.item())) {
				return weapon;
			}
		}
		return NONE;
	}
}
