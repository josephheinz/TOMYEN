package com.jheinz.mymod.client.skullcrane;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import net.minecraft.util.Mth;

import com.jheinz.mymod.skullcrane.ServoGear;

/**
 * Poses the Servo Arm Backpack's four arms, in the wearer's body space (model units, y down, the back at +z).
 *
 * <p>At rest each arm is a chain of {@value #SEGMENTS} segments rising from the spine pack: back and up, then curling
 * forward over the shoulders and head in an S, swaying slowly out of step with the others. Reaching, it's solved
 * toward its target with a few passes of FABRIK, starting from the rest pose so it keeps a bend; if the target's
 * further than the arm is long, the segments telescope out (up to {@value #MAX_STRETCH} times their length). The
 * reach blends in and out with the arm's progress, and the three-pronged claw opens wide on the way and snaps shut on
 * arrival.
 *
 * <p>Each segment and prong is posed directly in body space (no part nesting), so it can be stretched along its length
 * without skewing the rest; {@link #pose} writes {@value #FLOATS} floats per part: position, x/y/z rotation and length
 * scale.
 */
public final class ServoArmRig {
	public static final int ARMS = ServoGear.ARMS;
	public static final int SEGMENTS = 4;
	public static final int PRONGS = 3;
	public static final int PARTS_PER_ARM = SEGMENTS + PRONGS;
	public static final int FLOATS = 7;
	/** Where each arm leaves the pack: upper right, upper left, lower right, lower left. */
	static final float[][] ROOTS = {{-1.5f, 1.5f, 4.0f}, {1.5f, 1.5f, 4.0f}, {-1.5f, 6.0f, 4.0f}, {1.5f, 6.0f, 4.0f}};
	/** Each arm's lean out to its side at the root. */
	private static final float[] SPLAY = {-0.2f, 0.2f, -0.5f, 0.5f};
	/** Each arm's bends, root to tip (positive curls forward): a little back, up, over, and the claw tipped up. */
	private static final float[][] BENDS = {{-0.3f, 0.4f, 1.0f, -0.4f}, {-0.3f, 0.4f, 1.0f, -0.4f},
			{-0.15f, 0.5f, 0.9f, -0.35f}, {-0.15f, 0.5f, 0.9f, -0.35f}};
	/** Each segment's thickness and length, root to tip. */
	public static final float[][] SEGMENT_SIZE = {{1.5f, 5.5f}, {1.25f, 5.0f}, {1.25f, 4.5f}, {1.0f, 4.0f}};
	/** How far each segment runs back past its joint, under the one before it. */
	public static final float OVERLAP = 0.25f;
	public static final float PRONG_LENGTH = 2.0f;
	private static final float MAX_STRETCH = 5.0f;
	private static final int ITERATIONS = 8;

	private ServoArmRig() {
	}

	/**
	 * The arms' poses this frame: each arm's target in body space (or null) and how far it's got toward it, 0 to 1
	 * (either array may be null for all at rest).
	 */
	public static float[] pose(float age, Vector3f @Nullable [] targets, float @Nullable [] progress) {
		float[] out = new float[ARMS * PARTS_PER_ARM * FLOATS];
		for (int a = 0; a < ARMS; a++) {
			float phase = a * 1.7f;
			Vector3f[] rest = restChain(a, age, phase);
			float reach = progress == null ? 0.0f : Mth.clamp(progress[a], 0.0f, 1.0f);
			Vector3f target = targets == null ? null : targets[a];
			Vector3f[] joints = rest;
			if (target != null && reach > 0.0f) {
				Vector3f[] solved = solve(rest, target);
				float t = reach * reach * (3.0f - 2.0f * reach);
				joints = new Vector3f[rest.length];
				for (int j = 0; j < rest.length; j++) {
					joints[j] = new Vector3f(rest[j]).lerp(solved[j], t);
				}
			}
			int base = a * PARTS_PER_ARM * FLOATS;
			Quaternionf last = new Quaternionf();
			for (int s = 0; s < SEGMENTS; s++) {
				Vector3f span = new Vector3f(joints[s + 1]).sub(joints[s]);
				float length = Math.max(span.length(), 1.0e-3f);
				span.div(length);
				// The rotation taking the segment's own -y onto the span: z then x.
				float xRot = (float) Math.asin(Mth.clamp(-span.z, -1.0f, 1.0f));
				float zRot = (float) Mth.atan2(span.x, -span.y);
				write(out, base + s * FLOATS, joints[s], xRot, 0.0f, zRot, length / SEGMENT_SIZE[s][1]);
				last.rotationZYX(zRot, 0.0f, xRot);
			}
			// The claw: open wide on the way, shut on arrival.
			float p = Mth.clamp(reach / 0.95f, 0.0f, 1.0f);
			float relaxed = 0.3f + 0.06f * Mth.sin(age * 0.1f + phase);
			float spread = relaxed * (1.0f - p) - 0.1f * p + 0.55f * Mth.sin(Mth.PI * p);
			Vector3f tip = joints[SEGMENTS];
			for (int k = 0; k < PRONGS; k++) {
				float angle = k * Mth.TWO_PI / PRONGS;
				Vector3f pivot = last.transform(new Vector3f(Mth.sin(angle) * 0.3f, 0.2f, Mth.cos(angle) * 0.3f)).add(tip);
				Quaternionf prong = new Quaternionf(last).rotateY(angle).rotateX(-spread);
				// Back into the z, y, x rotations a model part applies: R = Rz Ry Rx (JOML's mCR is column C, row R).
				Matrix3f m = prong.get(new Matrix3f());
				float yRot = (float) Math.asin(Mth.clamp(-m.m02, -1.0f, 1.0f));
				write(out, base + (SEGMENTS + k) * FLOATS, pivot, (float) Mth.atan2(m.m12, m.m22), yRot, (float) Mth.atan2(m.m01, m.m00), 1.0f);
			}
		}
		return out;
	}

	/** The arm at rest: its joints, root to tip, with a slow sway on every bend. */
	private static Vector3f[] restChain(int arm, float age, float phase) {
		Vector3f[] joints = new Vector3f[SEGMENTS + 1];
		joints[0] = new Vector3f(ROOTS[arm][0], ROOTS[arm][1], ROOTS[arm][2]);
		float splay = SPLAY[arm] + 0.06f * Mth.sin(age * 0.05f + phase * 1.3f);
		Quaternionf rotation = new Quaternionf().rotationZ(splay);
		for (int s = 0; s < SEGMENTS; s++) {
			rotation.rotateX(BENDS[arm][s] + 0.07f * Mth.sin(age * 0.07f + phase + s * 0.7f));
			Vector3f step = rotation.transform(new Vector3f(0.0f, -SEGMENT_SIZE[s][1], 0.0f));
			joints[s + 1] = new Vector3f(joints[s]).add(step);
		}
		return joints;
	}

	/**
	 * The chain bent toward a target (stopping a claw's length short of it), from the rest pose: FABRIK, the segments
	 * stretched first if it's out of reach.
	 */
	private static Vector3f[] solve(Vector3f[] rest, Vector3f target) {
		Vector3f root = rest[0];
		Vector3f to = new Vector3f(target).sub(root);
		float distance = to.length();
		Vector3f goal = distance > PRONG_LENGTH ? new Vector3f(to).mul((distance - PRONG_LENGTH) / distance).add(root) : new Vector3f(root);
		float[] lengths = new float[SEGMENTS];
		float total = 0.0f;
		for (int s = 0; s < SEGMENTS; s++) {
			lengths[s] = SEGMENT_SIZE[s][1];
			total += lengths[s];
		}
		// A little slack, so a stretched arm still curves.
		float wanted = goal.distance(root) / (total * 0.9f);
		if (wanted > 1.0f) {
			float stretch = Math.min(wanted, MAX_STRETCH);
			for (int s = 0; s < SEGMENTS; s++) {
				lengths[s] *= stretch;
			}
		}
		Vector3f[] joints = new Vector3f[rest.length];
		for (int j = 0; j < rest.length; j++) {
			joints[j] = new Vector3f(rest[j]);
		}
		Vector3f dir = new Vector3f();
		for (int i = 0; i < ITERATIONS; i++) {
			joints[SEGMENTS].set(goal);
			for (int s = SEGMENTS - 1; s >= 0; s--) {
				towards(joints[s], joints[s + 1], lengths[s], dir);
			}
			joints[0].set(root);
			for (int s = 0; s < SEGMENTS; s++) {
				towards(joints[s + 1], joints[s], lengths[s], dir);
			}
		}
		return joints;
	}

	/** Moves {@code point} to {@code length} from {@code anchor}, along the line between them. */
	private static void towards(Vector3f point, Vector3f anchor, float length, Vector3f scratch) {
		scratch.set(point).sub(anchor);
		float distance = scratch.length();
		if (distance < 1.0e-4f) {
			scratch.set(0.0f, -1.0f, 0.0f);
		} else {
			scratch.div(distance);
		}
		point.set(anchor).add(scratch.mul(length));
	}

	private static void write(float[] out, int at, Vector3f pos, float xRot, float yRot, float zRot, float scale) {
		out[at] = pos.x;
		out[at + 1] = pos.y;
		out[at + 2] = pos.z;
		out[at + 3] = xRot;
		out[at + 4] = yRot;
		out[at + 5] = zRot;
		out[at + 6] = scale;
	}
}
