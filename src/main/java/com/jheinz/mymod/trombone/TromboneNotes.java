package com.jheinz.mymod.trombone;

import net.minecraft.util.Mth;

/**
 * The notes the Trombone plays: two octaves of the Bb major scale, Bb2 to Bb4, fifteen scale degrees. Where the player
 * looks picks the degree: straight ahead is Bb3 (degree 7), {@link #LOOK_RANGE} degrees up the top Bb and as far down the
 * bottom one, a little under eight degrees of look per note. The slide follows: fully in at the top note, fully out at
 * the bottom (slide frame {@code TOP - degree}).
 * <p>
 * Shared by the server (which hears the notes and matches songs) and every client (which plays them for each
 * trombonist it can see, from their synced look).
 */
public final class TromboneNotes {
	public static final int TOP = 14;
	public static final int DEGREES = TOP + 1;
	/** The degree the sustain sample is recorded at (Bb3, played at pitch 1). */
	public static final int BASE = 7;
	public static final float LOOK_RANGE = 55.0f;
	private static final int[] SCALE = {0, 2, 4, 5, 7, 9, 11};
	private static final String[] NAMES = {"Bb", "C", "D", "Eb", "F", "G", "A"};

	private TromboneNotes() {
	}

	/** Where along the scale this look pitch points, 0 (bottom Bb) to {@link #TOP}, unrounded. */
	public static float position(float xRot) {
		return Mth.clamp((LOOK_RANGE - xRot) / (2.0f * LOOK_RANGE) * TOP, 0.0f, TOP);
	}

	public static int degree(float xRot) {
		return Math.round(position(xRot));
	}

	/** Semitones above the bottom Bb. */
	public static int semitone(int degree) {
		return degree / 7 * 12 + SCALE[degree % 7];
	}

	/** The sound pitch for a (fractional) number of semitones above the bottom Bb: 0.5 there, 2 two octaves up. */
	public static float pitch(float semitones) {
		return (float) Math.pow(2.0, (semitones - semitone(BASE)) / 12.0);
	}

	public static String name(int degree) {
		// Octave numbers turn over at C, one degree above each Bb.
		return NAMES[degree % 7] + (2 + (degree + 6) / 7);
	}

	/** The slide frame (tools/gen_trombone.py) for a degree: 0 fully in, {@link #TOP} fully out. */
	public static int frame(int degree) {
		return TOP - degree;
	}
}
