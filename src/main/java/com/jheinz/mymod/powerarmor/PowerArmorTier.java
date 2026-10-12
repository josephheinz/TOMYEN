package com.jheinz.mymod.powerarmor;

import net.minecraft.network.chat.Component;

/**
 * A power armor piece's mark. Every piece comes in all three, and a suit can mix them freely: each piece's mark
 * sets its own durability and protection, and what it governs ({@link PowerArmorStats}) improves with it.
 * <ul>
 * <li>Mk I: battered grey T-51b plate, rust-streaked, with mismatched patch plates bolted on.
 * <li>Mk II: clean factory-grey T-51b with hazard-stripe stencils.
 * <li>Mk III: dark polished gunmetal T-51b with extra reinforcing plates.
 * </ul>
 */
public enum PowerArmorTier {
	MK1("mk1", "I", 1.0f, 0.75f, 0xB07A50),
	MK2("mk2", "II", 1.75f, 0.84f, 0xE0C040),
	MK3("mk3", "III", 2.8f, 0.92f, 0x9CB4C8);

	private final String id;
	private final String numeral;
	private final float durabilityScale;
	private final float protection;
	private final int color;

	PowerArmorTier(String id, String numeral, float durabilityScale, float protection, int color) {
		this.id = id;
		this.numeral = numeral;
		this.durabilityScale = durabilityScale;
		this.protection = protection;
		this.color = color;
	}

	public String id() {
		return id;
	}

	/** "I", "II" or "III", for compact readouts. */
	public String numeral() {
		return numeral;
	}

	/** The piece's durability relative to a Mk I. */
	public float durabilityScale() {
		return durabilityScale;
	}

	/** The share of a hit a working piece of this mark absorbs. */
	public float protection() {
		return protection;
	}

	/** The mark's accent colour (RGB) in tooltips, the station and the HUD. */
	public int color() {
		return color;
	}

	/** 0, 1 or 2: an index into per-mark stat tables. */
	public int level() {
		return ordinal();
	}

	public Component displayName() {
		return Component.translatable("power_armor_tier.mymod." + id);
	}
}
