package com.jheinz.tomyen.fishing;

/** The three quick-time minigames. The client plays them (client/fishing); the server only times and checks them. */
public enum FishingGame {
	/** Keep the needle in the green against the fish's pull until the catch bar fills. */
	TENSION,
	/** Press while the sweeping marker is inside the zone: N hits before M misses. */
	STRIKES,
	/** Tap to keep a falling meter above the line until the timer runs out. */
	MASH
}
