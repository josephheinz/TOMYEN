package com.jheinz.tomyen.detector;

import net.minecraft.network.chat.Component;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.werewolf.WerewolfCurse;

/**
 * The moon as the Lunar Almanac reads it, the same on both sides. The phase turns over at sunrise (each in-game
 * day's clock starts at 6:00), so the phase now is the phase of tonight's moon; werewolves change on the night of
 * the full moon (see {@link WerewolfCurse}).
 */
public final class Moon {
	private Moon() {
	}

	public static MoonPhase phase(Level level, Vec3 pos) {
		return level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, pos);
	}

	/** Nights from tonight to the next full moon: 0 when tonight is one. */
	public static int nightsToFull(MoonPhase phase) {
		return (MoonPhase.COUNT - phase.index()) % MoonPhase.COUNT;
	}

	/** Whether it's night by this level's clock, the way werewolves count it. */
	public static boolean isNight(Level level) {
		if (level.dimensionType().hasFixedTime()) {
			return false;
		}
		long time = level.getDefaultClockTime() % 24000L;
		return time >= WerewolfCurse.DUSK && time < WerewolfCurse.SUNRISE;
	}

	/** Whether the moon means anything here: the dimension has a day and night. */
	public static boolean visible(Level level) {
		return !level.dimensionType().hasFixedTime();
	}

	public static Component phaseName(MoonPhase phase) {
		return Component.translatable("detector.tomyen.moon." + phase.getSerializedName());
	}

	/** "Full moon tonight", "Full moon in 3 nights" and the like. */
	public static Component countdown(Level level, MoonPhase phase) {
		int nights = nightsToFull(phase);
		if (nights == 0) {
			return Component.translatable(isNight(level) ? "detector.tomyen.moon.full_now" : "detector.tomyen.moon.full_tonight");
		}
		return Component.translatable(nights == 1 ? "detector.tomyen.moon.full_in_one" : "detector.tomyen.moon.full_in", nights);
	}
}
