package com.jheinz.tomyen.powerarmor;

import org.jspecify.annotations.Nullable;

/**
 * What a suit looks like, packed into one int so a parked suit can sync it cheaply: per piece, two bits of mark
 * (0 for missing) and three of condition (0 broken, 1 to 7 rounded up from any damage), and one bit for a lit
 * fusion core.
 */
public record SuitLook(int packed) {
	private static final int BITS = 5;
	private static final int CORE_BIT = 1 << (BITS * PowerArmorPiece.COUNT);
	public static final SuitLook NONE = new SuitLook(0);

	public static SuitLook of(PowerArmorSuit suit) {
		int packed = 0;
		for (PowerArmorPiece piece : PowerArmorPiece.values()) {
			PowerArmorTier tier = suit.tier(piece);
			if (tier == null) {
				continue;
			}
			int condition = (int) Math.ceil(suit.condition(piece) * 7.0f);
			packed |= ((tier.ordinal() + 1) | condition << 2) << (BITS * piece.ordinal());
		}
		if (suit.charge() > 0) {
			packed |= CORE_BIT;
		}
		return new SuitLook(packed);
	}

	private int bits(PowerArmorPiece piece) {
		return packed >> (BITS * piece.ordinal()) & ((1 << BITS) - 1);
	}

	public @Nullable PowerArmorTier tier(PowerArmorPiece piece) {
		int tier = bits(piece) & 3;
		return tier == 0 ? null : PowerArmorTier.values()[tier - 1];
	}

	/** Roughly how intact a piece is, 0 to 1. */
	public float condition(PowerArmorPiece piece) {
		return (bits(piece) >> 2) / 7.0f;
	}

	public boolean coreLit() {
		return (packed & CORE_BIT) != 0;
	}

	public boolean isEmpty() {
		return (packed & (CORE_BIT - 1)) == 0;
	}
}
