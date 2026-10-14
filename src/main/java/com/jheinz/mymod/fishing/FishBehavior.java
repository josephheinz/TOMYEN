package com.jheinz.mymod.fishing;

import java.util.Locale;

/** How a hooked fish fights, from a species file's {@code "behavior"}; it picks the minigame. */
public enum FishBehavior {
	/** Pulls steadily, in long smooth surges: the tension meter. */
	STEADY(FishingGame.TENSION),
	/** Darts about: timing strikes, the zone jumping after each hit. */
	DARTING(FishingGame.STRIKES),
	/** Dives for the bottom: the mash meter, falling hard. */
	SINKER(FishingGame.MASH),
	/** Bobs up and down unpredictably: the mash meter with a drifting line. */
	FLOATER(FishingGame.MASH),
	/** Thrashes: the tension meter with sudden jerks either way. */
	THRASHER(FishingGame.TENSION);

	private final FishingGame game;

	FishBehavior(FishingGame game) {
		this.game = game;
	}

	public FishingGame game() {
		return game;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The behavior named {@code id}, or null. */
	public static FishBehavior byId(String id) {
		for (FishBehavior behavior : values()) {
			if (behavior.id().equals(id)) {
				return behavior;
			}
		}
		return null;
	}
}
