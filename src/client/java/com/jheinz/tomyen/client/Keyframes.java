package com.jheinz.tomyen.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.joml.Vector3f;

import net.minecraft.util.Mth;

/**
 * Keyframed motion for the handling animations. An animation declares, as data, tracks of keys at points in its
 * progress (0 to 1) and samples them each frame: a {@link Track} for one number (how far a slide is back, how far the
 * gun is turned), a {@link Path} for a point plus a hand twist (where a hand is, in the held item's space or in model
 * pixels). Each key says how the motion eases into it, so a slap accelerates into the magazine and a toss flies off
 * the hand, and nothing ever snaps: the value at a key is the value leaving it.
 *
 * <p>A hand's path starts and ends at its hold on the gun, so the animation hands the hand back where it found it.
 */
public final class Keyframes {
	/** How the motion runs from the previous key into a key. */
	public enum Ease {
		/** Steady speed: for following another track step by step. */
		LINEAR,
		/** Eases out of the previous key and into this one. */
		SMOOTH,
		/** Accelerates into this key and stops dead: a slap, a slam, a knock. */
		IN,
		/** Leaves the previous key fast and settles into this one: a yank, a toss, a flick away. */
		OUT,
		/** Overshoots this key a little and settles back onto it: a part or hand landing with a bounce. */
		BACK;

		float apply(float t) {
			t = Mth.clamp(t, 0.0f, 1.0f);
			return switch (this) {
				case LINEAR -> t;
				case SMOOTH -> t * t * (3.0f - 2.0f * t);
				case IN -> t * t * t;
				case OUT -> 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
				case BACK -> {
					float s = 1.7f;
					float u = t - 1.0f;
					yield 1.0f + (s + 1.0f) * u * u * u + s * u * u;
				}
			};
		}
	}

	private Keyframes() {
	}

	/** The key at or before {@code p}: the index of the segment {@code p} is in (keys must be in order). */
	private static int segment(float[] at, float p) {
		int i = 0;
		while (i + 1 < at.length && p >= at[i + 1]) {
			i++;
		}
		return i;
	}

	// --------------------------------------------------------------------------------------------------- numbers

	/** One number over an animation's progress. */
	public static final class Track {
		private final float[] at;
		private final float[] values;
		private final Ease[] eases;

		private Track(float[] at, float[] values, Ease[] eases) {
			this.at = at;
			this.values = values;
			this.eases = eases;
		}

		public float sample(float p) {
			if (p <= at[0]) {
				return values[0];
			}
			int i = segment(at, p);
			if (i + 1 >= at.length) {
				return values[at.length - 1];
			}
			float t = eases[i + 1].apply((p - at[i]) / (at[i + 1] - at[i]));
			return Mth.lerp(t, values[i], values[i + 1]);
		}
	}

	public static TrackBuilder track(float start) {
		return new TrackBuilder(start);
	}

	public static final class TrackBuilder {
		private final List<Float> at = new ArrayList<>();
		private final List<Float> values = new ArrayList<>();
		private final List<Ease> eases = new ArrayList<>();

		private TrackBuilder(float start) {
			at.add(0.0f);
			values.add(start);
			eases.add(Ease.LINEAR);
		}

		public TrackBuilder key(float time, float value) {
			return key(time, value, Ease.SMOOTH);
		}

		public TrackBuilder key(float time, float value, Ease ease) {
			at.add(time);
			values.add(value);
			eases.add(ease);
			return this;
		}

		/** Stays at the last value until {@code time}. */
		public TrackBuilder hold(float time) {
			return key(time, values.getLast(), Ease.LINEAR);
		}

		public Track build() {
			float[] times = new float[at.size()];
			float[] vals = new float[at.size()];
			for (int i = 0; i < times.length; i++) {
				times[i] = at.get(i);
				vals[i] = values.get(i);
			}
			return new Track(times, vals, eases.toArray(new Ease[0]));
		}
	}

	// ---------------------------------------------------------------------------------------------------- points

	/** A point and a hand twist, sampled together. */
	public record Sample(Vector3f point, float twist) {
	}

	/**
	 * A point (and how far the hand is twisted about its arm, in degrees) over an animation's progress. Points are in
	 * whatever space the animation works in; the hand code turns them into the held item's space.
	 */
	public static final class Path {
		private final float[] at;
		private final Vector3f[] points;
		private final float[] twists;
		private final Ease[] eases;

		private Path(float[] at, Vector3f[] points, float[] twists, Ease[] eases) {
			this.at = at;
			this.points = points;
			this.twists = twists;
			this.eases = eases;
		}

		public Sample sample(float p) {
			if (p <= at[0]) {
				return new Sample(new Vector3f(points[0]), twists[0]);
			}
			int i = segment(at, p);
			if (i + 1 >= at.length) {
				return new Sample(new Vector3f(points[at.length - 1]), twists[at.length - 1]);
			}
			float t = eases[i + 1].apply((p - at[i]) / (at[i + 1] - at[i]));
			return new Sample(new Vector3f(points[i]).lerp(points[i + 1], t), Mth.lerp(t, twists[i], twists[i + 1]));
		}

		/** The same path with every point mapped (e.g. from model pixels into the held item's space). */
		public Path map(Function<Vector3f, Vector3f> to) {
			Vector3f[] mapped = new Vector3f[points.length];
			for (int i = 0; i < points.length; i++) {
				mapped[i] = to.apply(new Vector3f(points[i]));
			}
			return new Path(at, mapped, twists, eases);
		}
	}

	/** A path starting at {@code start} (usually the hand's hold) with no twist. */
	public static PathBuilder path(Vector3f start) {
		return new PathBuilder(start, 0.0f);
	}

	/** A path starting at {@code start} with the hand already twisted by {@code twist} (picking up mid-motion). */
	public static PathBuilder path(Vector3f start, float twist) {
		return new PathBuilder(start, twist);
	}

	public static final class PathBuilder {
		/** Steps per unit of progress when following another track: fine enough that the hand stays on the part. */
		private static final int FOLLOW_STEPS = 60;

		private final List<Float> at = new ArrayList<>();
		private final List<Vector3f> points = new ArrayList<>();
		private final List<Float> twists = new ArrayList<>();
		private final List<Ease> eases = new ArrayList<>();

		private PathBuilder(Vector3f start, float twist) {
			at.add(0.0f);
			points.add(new Vector3f(start));
			twists.add(twist);
			eases.add(Ease.LINEAR);
		}

		/** Moves to {@code point}, keeping the current twist, eased smoothly. */
		public PathBuilder key(float time, Vector3f point) {
			return key(time, point, twists.getLast(), Ease.SMOOTH);
		}

		public PathBuilder key(float time, Vector3f point, float twist) {
			return key(time, point, twist, Ease.SMOOTH);
		}

		public PathBuilder key(float time, Vector3f point, float twist, Ease ease) {
			at.add(time);
			points.add(new Vector3f(point));
			twists.add(twist);
			eases.add(ease);
			return this;
		}

		/** Stays put until {@code time}. */
		public PathBuilder hold(float time) {
			return key(time, points.getLast(), twists.getLast(), Ease.LINEAR);
		}

		/**
		 * Rides a moving part from {@code from} to {@code to}: the point is {@code where} the part is at each moment
		 * (usually read off the part's own track), sampled often enough to stay on it. The path should already be at the
		 * part's starting point.
		 */
		public PathBuilder follow(float from, float to, Function<Float, Vector3f> where) {
			int steps = Math.max(2, Math.round((to - from) * FOLLOW_STEPS));
			float twist = twists.getLast();
			for (int k = 1; k <= steps; k++) {
				float time = from + (to - from) * k / steps;
				key(time, where.apply(time), twist, Ease.LINEAR);
			}
			return this;
		}

		public Path build() {
			float[] times = new float[at.size()];
			float[] tw = new float[at.size()];
			for (int i = 0; i < times.length; i++) {
				times[i] = at.get(i);
				tw[i] = twists.get(i);
			}
			return new Path(times, points.toArray(new Vector3f[0]), tw, eases.toArray(new Ease[0]));
		}
	}
}
