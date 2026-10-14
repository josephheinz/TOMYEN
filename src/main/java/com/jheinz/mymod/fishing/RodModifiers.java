package com.jheinz.mymod.fishing;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * What a rod, or one part fitted to it, changes about fishing with it. A rod's totals ({@link RodStats}) are its own
 * modifiers plus its parts'. Every field is a change from the baseline, so {@link #NONE} is "no change":
 * <ul>
 * <li>{@code biteTicks}: ticks off the wait for a bite (like Lure), {@code nightBiteTicks} more at night;</li>
 * <li>{@code zone}: the minigame's green zone (and strike zone), +0.1 = 10% wider;</li>
 * <li>{@code speed}: how fast the fish (needle, marker, falling meter) moves, -0.1 = 10% slower;</li>
 * <li>{@code escape}: hold on the fish, 0 to 1: longer grace in the slack zone, extra misses allowed;</li>
 * <li>{@code line}: line strength, 0 to 1: longer grace in the snap zone;</li>
 * <li>{@code reel}: reel speed, +0.1 = catch progress fills 10% faster (and harder taps);</li>
 * <li>{@code assist}: a servo reel's help, 0 to 1: it nudges the needle back to centre;</li>
 * <li>{@code rarity}: bias toward rarer (lower weight) species, {@code nightRarity} more at night;</li>
 * <li>{@code sonar}: the species shows before it bites; {@code cursedBonus}: cursed catches are one step easier.</li>
 * </ul>
 */
public record RodModifiers(int biteTicks, int nightBiteTicks, float zone, float speed, float escape, float line,
		float reel, float assist, float rarity, float nightRarity, boolean sonar, boolean cursedBonus) {
	public static final RodModifiers NONE = builder().build();

	public static Builder builder() {
		return new Builder();
	}

	public RodModifiers plus(RodModifiers other) {
		return new RodModifiers(biteTicks + other.biteTicks, nightBiteTicks + other.nightBiteTicks, zone + other.zone,
				speed + other.speed, escape + other.escape, line + other.line, reel + other.reel, assist + other.assist,
				rarity + other.rarity, nightRarity + other.nightRarity, sonar || other.sonar, cursedBonus || other.cursedBonus);
	}

	/** One tooltip line per thing this changes, good changes blue and bad ones red, like attribute modifiers. */
	public List<Component> describe() {
		List<Component> lines = new ArrayList<>();
		if (biteTicks != 0) {
			lines.add(line(biteTicks > 0, biteTicks > 0 ? "bite_sooner" : "bite_later", seconds(Math.abs(biteTicks))));
		}
		if (nightBiteTicks != 0) {
			lines.add(line(nightBiteTicks > 0, "night_bite", seconds(nightBiteTicks)));
		}
		percent(lines, zone, true, "zone");
		if (speed != 0) {
			lines.add(line(speed < 0, speed < 0 ? "fish_slower" : "fish_faster", percent(Math.abs(speed))));
		}
		percent(lines, escape, true, "escape");
		percent(lines, line, true, "line");
		percent(lines, reel, true, "reel");
		percent(lines, assist, true, "assist");
		percent(lines, rarity, true, "rarity");
		percent(lines, nightRarity, true, "night_rarity");
		if (sonar) {
			lines.add(line(true, "sonar"));
		}
		if (cursedBonus) {
			lines.add(line(true, "cursed"));
		}
		return lines;
	}

	private static void percent(List<Component> lines, float value, boolean higherIsBetter, String key) {
		if (value != 0) {
			lines.add(line(value > 0 == higherIsBetter, key, (value > 0 ? "+" : "-") + percent(Math.abs(value))));
		}
	}

	private static String percent(float value) {
		return Math.round(value * 100.0f) + "%";
	}

	private static String seconds(int ticks) {
		return String.format(Locale.ROOT, "%.1f", ticks / 20.0f);
	}

	private static Component line(boolean good, String key, Object... args) {
		return Component.literal(" ").append(Component.translatable("tooltip.mymod.rod_effect." + key, args))
				.withStyle(good ? ChatFormatting.BLUE : ChatFormatting.RED);
	}

	public static final class Builder {
		private int biteTicks;
		private int nightBiteTicks;
		private float zone;
		private float speed;
		private float escape;
		private float line;
		private float reel;
		private float assist;
		private float rarity;
		private float nightRarity;
		private boolean sonar;
		private boolean cursedBonus;

		private Builder() {
		}

		public Builder biteTicks(int ticks) {
			biteTicks = ticks;
			return this;
		}

		public Builder nightBiteTicks(int ticks) {
			nightBiteTicks = ticks;
			return this;
		}

		public Builder zone(float value) {
			zone = value;
			return this;
		}

		public Builder speed(float value) {
			speed = value;
			return this;
		}

		public Builder escape(float value) {
			escape = value;
			return this;
		}

		public Builder line(float value) {
			line = value;
			return this;
		}

		public Builder reel(float value) {
			reel = value;
			return this;
		}

		public Builder assist(float value) {
			assist = value;
			return this;
		}

		public Builder rarity(float value) {
			rarity = value;
			return this;
		}

		public Builder nightRarity(float value) {
			nightRarity = value;
			return this;
		}

		public Builder sonar() {
			sonar = true;
			return this;
		}

		public Builder cursedBonus() {
			cursedBonus = true;
			return this;
		}

		public RodModifiers build() {
			return new RodModifiers(biteTicks, nightBiteTicks, zone, speed, escape, line, reel, assist, rarity, nightRarity,
					sonar, cursedBonus);
		}
	}
}
