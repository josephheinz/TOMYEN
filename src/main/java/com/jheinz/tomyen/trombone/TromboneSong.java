package com.jheinz.tomyen.trombone;

import java.util.List;

/**
 * The phrases the Trombone knows, each four notes, matched against the last four the player played (forgivingly: any
 * key, steps within a range). In scale steps (one look-notch each):
 * <ul>
 * <li>Sad Trombone: wah-wah-wah-waaah, four notes each one to three steps lower than the last, the last held at least
 * {@link #LONG_TICKS}.</li>
 * <li>War March: a bugle call climbing the chord, four notes each two to four steps higher.</li>
 * <li>Lullaby: rock-a-bye, high-low-high-low, small swings (one to four steps) back to the same two notes.</li>
 * <li>Disco: low-high-low-high, big jumps (five steps or more, as an octave bass line) back to the same two notes.</li>
 * </ul>
 */
public enum TromboneSong {
	SAD_TROMBONE("sad_trombone", 0xFF6FA8FF, 600) {
		@Override
		boolean matches(List<Note> n) {
			return steps(n, -3, -1) && n.get(3).ticks() >= LONG_TICKS;
		}
	},
	WAR_MARCH("war_march", 0xFFFF6A4D, 900) {
		@Override
		boolean matches(List<Note> n) {
			return steps(n, 2, 4);
		}
	},
	LULLABY("lullaby", 0xFFC9A0FF, 600) {
		@Override
		boolean matches(List<Note> n) {
			return rocks(n, -1, 1, 4);
		}
	},
	DISCO("disco", 0xFFFF7FE0, 600) {
		@Override
		boolean matches(List<Note> n) {
			return rocks(n, 1, 5, TromboneNotes.TOP);
		}
	};

	/** How long the Sad Trombone's last note must be held: the waaah. */
	public static final int LONG_TICKS = 10;
	public static final int LENGTH = 4;

	/** One note of a phrase: a scale degree and how long it was held. */
	public record Note(int degree, int ticks) {
	}

	private final String id;
	private final int colour;
	private final int cooldown;

	TromboneSong(String id, int colour, int cooldown) {
		this.id = id;
		this.colour = colour;
		this.cooldown = cooldown;
	}

	public String id() {
		return id;
	}

	/** ARGB, for the HUD. */
	public int colour() {
		return colour;
	}

	public int cooldown() {
		return cooldown;
	}

	public String translationKey() {
		return "song.tomyen." + id;
	}

	/** Whether the last {@link #LENGTH} notes of the phrase are this song. */
	public boolean matchesEnd(List<Note> phrase) {
		return phrase.size() >= LENGTH && matches(phrase.subList(phrase.size() - LENGTH, phrase.size()));
	}

	/** n: exactly {@link #LENGTH} notes. */
	abstract boolean matches(List<Note> n);

	/** Every step from one note to the next is between min and max scale steps (negative is down). */
	private static boolean steps(List<Note> n, int min, int max) {
		for (int i = 1; i < n.size(); i++) {
			int step = n.get(i).degree() - n.get(i - 1).degree();
			if (step < min || step > max) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Swinging between two notes: the first move goes the way {@code direction} says (-1 down, 1 up), every move swings
	 * back the other way by between min and max steps, and the notes come back within a step of where they were.
	 */
	private static boolean rocks(List<Note> n, int direction, int min, int max) {
		for (int i = 1; i < n.size(); i++) {
			int step = (n.get(i).degree() - n.get(i - 1).degree()) * (i % 2 == 1 ? direction : -direction);
			if (step < min || step > max) {
				return false;
			}
		}
		return Math.abs(n.get(2).degree() - n.get(0).degree()) <= 1 && Math.abs(n.get(3).degree() - n.get(1).degree()) <= 1;
	}
}
