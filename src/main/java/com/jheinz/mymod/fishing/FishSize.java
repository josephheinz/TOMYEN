package com.jheinz.mymod.fishing;

import java.util.Locale;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * A species' weights, in lb, from the {@code "size"} of its species file: the smallest catchable, the typical catch
 * and the record. Every fish landed on a mod rod rolls a weight from it ({@link #roll}).
 *
 * <p>The roll is right-skewed. Its quantile function puts the median at {@code avg}: the lower half eases in from
 * {@code min} (most small fish are still near the average), and the upper half climbs to {@code max} along
 * {@code t^4}, a long thin tail, so near-record fish are rare. A bonus (rod tier, bait, Luck of the Sea, luck) pushes
 * the uniform draw toward the top before it's mapped: {@code u' = 1 - (1 - u)^(1 + bonus)}.
 *
 * <p>A trophy is a catch at or above {@link #trophy}: by default the weight 85% of unbiased catches fall below, so
 * about one plain catch in seven is a trophy, more with a good rod and bait. A file may set its own {@code trophy_lb}.
 */
public record FishSize(float min, float avg, float max, float trophy) {
	public static final double TROPHY_QUANTILE = 0.85;
	/** The upper tail's steepness. */
	private static final double TAIL = 4.0;
	/** The most a bonus can push the roll. */
	public static final float MAX_BONUS = 1.0f;

	/** A size with the default trophy line, or {@code trophy} when it's positive. */
	public static FishSize of(float min, float avg, float max, float trophy) {
		FishSize size = new FishSize(min, avg, max, 0.0f);
		return new FishSize(min, avg, max, trophy > 0.0f ? trophy : size.at(TROPHY_QUANTILE));
	}

	/** The weight at quantile {@code u} (0 to 1) of the unbiased roll. */
	public float at(double u) {
		u = Mth.clamp(u, 0.0, 1.0);
		if (u < 0.5) {
			double t = 1.0 - u / 0.5;
			return (float) (avg - (avg - min) * t * t);
		}
		double t = (u - 0.5) / 0.5;
		return (float) (avg + (max - avg) * Math.pow(t, TAIL));
	}

	/** The inverse of {@link #at}: where {@code weight} falls among unbiased catches, 0 (smallest) to 1 (the record). */
	public float quantile(float weight) {
		if (weight <= min) {
			return 0.0f;
		}
		if (weight <= avg) {
			double t = Math.sqrt((avg - weight) / (double) (avg - min));
			return (float) (0.5 * (1.0 - t));
		}
		if (weight >= max) {
			return 1.0f;
		}
		return (float) (0.5 + 0.5 * Math.pow((weight - avg) / (double) (max - avg), 1.0 / TAIL));
	}

	/** A weight for a fish just hooked, leaning heavier with {@code bonus} (0 to {@link #MAX_BONUS}). */
	public float roll(RandomSource random, float bonus) {
		double u = random.nextDouble();
		u = 1.0 - Math.pow(1.0 - u, 1.0 + Mth.clamp(bonus, 0.0f, MAX_BONUS));
		return round(at(u));
	}

	public boolean isTrophy(float weight) {
		return weight >= trophy;
	}

	public float clamp(float weight) {
		return Mth.clamp(weight, min, max);
	}

	/** Weights are kept to three significant figures (a tetra's to the hundredth of an ounce). */
	public static float round(float weight) {
		if (weight >= 100.0f) {
			return Math.round(weight);
		}
		if (weight >= 1.0f) {
			return Math.round(weight * 10.0f) / 10.0f;
		}
		// Under a pound it's shown in ounces: keep two decimals of an ounce.
		return Math.round(weight * 1600.0f) / 1600.0f;
	}

	/** "31.7 lb", "412 lb", or under a pound "3.5 oz" / "0.12 oz". */
	public static String format(float weight) {
		if (weight >= 100.0f) {
			return String.format(Locale.ROOT, "%.0f lb", weight);
		}
		if (weight >= 1.0f) {
			return String.format(Locale.ROOT, "%.1f lb", weight);
		}
		float ounces = weight * 16.0f;
		return String.format(Locale.ROOT, ounces >= 1.0f ? "%.1f oz" : "%.2f oz", ounces);
	}
}
