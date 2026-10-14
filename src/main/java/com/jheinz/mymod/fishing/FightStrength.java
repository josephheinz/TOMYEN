package com.jheinz.mymod.fishing;

import net.minecraft.util.Mth;

/**
 * How hard and how long one fish fights, from three things, each 0 to 1:
 * <ul>
 * <li>{@code percentile}: where its weight falls in its species' range ({@link FishSize#quantile}): a tiddler is a
 * quick, easy fight, a fish near the record a long, hard one;</li>
 * <li>{@code rarity}: how rare the species is, from its species-file weight (30 or more is common, 2 the rarest) and,
 * a little, its minimum rod tier;</li>
 * <li>{@code size}: its absolute weight on a log scale (0.1 lb is 0, 1,000 lb is 1), so a 400 lb swordfish outlasts a
 * 5 lb trout even at the same percentile.</li>
 * </ul>
 * They combine into {@link #value} (0 to 1), which sets the fight's {@link #length} (a multiplier on the catch bar's
 * fill, the strikes needed and the mash timer: about 0.6 for a small common fish, 2.6 for a record swordfish) and its
 * {@link #power} (how fast and hard the fish pulls). From {@link #STAMINA_PHASES} up a fish tires partway through and
 * makes a last surge near the end (the client's minigames play it).
 */
public record FightStrength(float percentile, float rarity, float size, float value) {
	/** Unweighed catches (the old boot) and the test command without a weight: an average fish of no rarity. */
	public static final FightStrength AVERAGE = of(0.5f, 0.0f, 0.4f);
	/** From here up the fish tires and surges. */
	public static final float STAMINA_PHASES = 0.5f;

	private static final float COMMON_WEIGHT = 30.0f;
	private static final float RAREST_WEIGHT = 2.0f;

	public static FightStrength of(float percentile, float rarity, float size) {
		float p = Mth.clamp(percentile, 0.0f, 1.0f);
		float r = Mth.clamp(rarity, 0.0f, 1.0f);
		float s = Mth.clamp(size, 0.0f, 1.0f);
		return new FightStrength(p, r, s, 0.45f * p + 0.25f * r + 0.30f * s);
	}

	/** The fight a fish of {@code species} weighing {@code weight} lb puts up (an unweighed one: {@link #AVERAGE}'s). */
	public static FightStrength of(FishingSpecies species, float weight) {
		float rarity = rarity(species);
		FishSize size = species.size();
		if (size == null || weight <= 0.0f) {
			return of(0.5f, rarity, AVERAGE.size);
		}
		return of(size.quantile(weight), rarity, absoluteSize(weight));
	}

	/** 0 for a common species (file weight 30 or more) to 1 for the rarest (2 or less); a high rod tier adds a little. */
	public static float rarity(FishingSpecies species) {
		float byWeight = (float) (Math.log(COMMON_WEIGHT / Math.max(1, species.weight())) / Math.log(COMMON_WEIGHT / RAREST_WEIGHT));
		return Mth.clamp(0.7f * Mth.clamp(byWeight, 0.0f, 1.0f) + 0.3f * species.minRodTier() / 4.0f, 0.0f, 1.0f);
	}

	/** Weight on a log scale: 0.1 lb is 0, 1,000 lb is 1. */
	public static float absoluteSize(float weight) {
		return Mth.clamp(((float) Math.log10(Math.max(weight, 0.01f)) + 1.0f) / 4.0f, 0.0f, 1.0f);
	}

	/** The fight's length multiplier: 0.55 for the weakest fish, about 1 for an average common one, 2.75 at most. */
	public float length() {
		return 0.55f + 2.2f * (float) Math.pow(value, 1.4);
	}

	/** How much harder the fish pulls and faster it moves: 0 to 1, the same as {@link #value}. */
	public float power() {
		return value;
	}

	public boolean staminaPhases() {
		return value >= STAMINA_PHASES;
	}
}
