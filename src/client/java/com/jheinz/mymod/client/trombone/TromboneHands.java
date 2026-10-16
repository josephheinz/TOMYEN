package com.jheinz.mymod.client.trombone;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import com.jheinz.mymod.trombone.TromboneNotes;

/**
 * Where the hands go on the Trombone, as tools/gen_trombone.py prints them from the model and its display transforms:
 * the off hand round the bell brace and inner slide brace by the face (it holds the instrument up), the main hand on
 * the outer slide's grip bar, which moves out with the slide frame.
 * <ul>
 * <li>First person: in the held item's space after vanilla's arm offset, as {@code GunHands} anchors are.</li>
 * <li>Third person: in head space turned to item axes (x right, y up, -z ahead, origin at the head's centre), where
 * {@link com.jheinz.mymod.client.trombone.mixin.PlayerTromboneHeldMixin} draws the instrument.</li>
 * </ul>
 */
public final class TromboneHands {
	/** How far the slide goes out, in model pixels, over the frames. */
	private static final float EXTEND = 11.0f;

	private static final Vector3f FP_GRIP = new Vector3f(-0.4296f, 0.0806f, 0.0488f);
	private static final Vector3f FP_SLIDE = new Vector3f(-0.2501f, 0.0876f, -0.0859f);
	/** The first-person slide hand's travel per model pixel the slide goes out. */
	private static final Vector3f FP_SLIDE_STEP = new Vector3f(0.01352f, 0.00218f, -0.04155f);

	private static final Vector3f TP_GRIP = new Vector3f(-0.084f, -0.167f, -0.457f);
	private static final Vector3f TP_SLIDE = new Vector3f(0.0193f, -0.167f, -0.604f);
	private static final Vector3f TP_SLIDE_STEP = new Vector3f(0.0f, 0.0f, -0.035f);

	private TromboneHands() {
	}

	private static float extension(int frame) {
		return EXTEND * frame / TromboneNotes.TOP;
	}

	public static Vector3f firstPersonGrip() {
		return new Vector3f(FP_GRIP);
	}

	public static Vector3f firstPersonSlide(int frame) {
		return new Vector3f(FP_SLIDE_STEP).mul(extension(frame)).add(FP_SLIDE);
	}

	public static Vector3f thirdPersonGrip() {
		return new Vector3f(TP_GRIP);
	}

	public static Vector3f thirdPersonSlide(int frame) {
		return new Vector3f(TP_SLIDE_STEP).mul(extension(frame)).add(TP_SLIDE);
	}

	/**
	 * Lowers the trombone from the lips while it isn't being played ({@code raise} 0), and brings it up as the player
	 * blows (1). Applied to the item's pose in first person, so the hands ride along.
	 */
	public static void applyRaise(PoseStack poseStack, float raise) {
		float down = 1.0f - raise;
		if (down <= 0.0f) {
			return;
		}
		float eased = down * down * (3.0f - 2.0f * down);
		poseStack.translate(0.05f * eased, -0.16f * eased, 0.08f * eased);
		poseStack.rotateDegrees(Axis.XP, -14.0f * eased);
	}
}
