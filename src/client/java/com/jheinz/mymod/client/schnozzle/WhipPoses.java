package com.jheinz.mymod.client.schnozzle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * How the whip hand moves through a {@link WhipMotion}: the first-person hand and item, the third-person arm and body,
 * and the wrist flick of the whip in a third-person hand.
 */
public final class WhipPoses {
	/** Vanilla's resting first-person item position (FirstPersonHandsAndItemsRenderer.applyItemArmTransform). */
	private static final float HELD_X = 0.56f;
	private static final float HELD_Y = -0.52f;
	private static final float HELD_Z = -0.72f;
	private static final float EQUIP_DROP = -0.6f;
	/** First person, the hand turns about the grip, which sits about here from the held position. */
	private static final float GRIP_X = 0.07f;
	private static final float GRIP_Y = -0.07f;
	private static final float GRIP_Z = -0.06f;

	/**
	 * First-person offsets (x out to the side, y up, z back toward the camera) and turns about the grip in degrees (x tips
	 * the whip back over the shoulder, y, z) for the right hand; the left hand mirrors them.
	 */
	private static final float[] FP_REST = {0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
	private static final float[] FP_CRACK_WINDUP = {-0.02f, 0.38f, 0.08f, 45.0f, 0.0f, -15.0f};
	private static final float[] FP_CRACK_STRIKE = {-0.14f, -0.1f, -0.2f, -75.0f, -10.0f, 12.0f};
	private static final float[] FP_SNAG_WINDUP = {-0.02f, 0.18f, 0.04f, 25.0f, 0.0f, -5.0f};
	private static final float[] FP_SNAG_STRIKE = {-0.1f, 0.02f, -0.28f, -55.0f, -8.0f, 8.0f};
	private static final float[] FP_SNAG_YANK = {-0.04f, -0.06f, 0.06f, -15.0f, 0.0f, 4.0f};

	/** Third person: the arm raised up and back over the shoulder for the wind-up (radians; vanilla's -PI is straight up). */
	private static final float ARM_WINDUP = -2.75f;
	private static final float ARM_SNAG_WINDUP = -2.2f;
	/** How far below the line of sight the arm ends a crack, and a yank. */
	private static final float ARM_STRIKE_DROP = 0.3f;
	private static final float ARM_YANK_DROP = 0.55f;

	private WhipPoses() {
	}

	/**
	 * In place of vanilla's held-item position and swing: the whip hand at {@code motion}, in the pose stack the item is
	 * then drawn in (before its display transform). WhipHandles repeats this exactly to find where the lash starts.
	 */
	public static void applyFirstPerson(PoseStack poseStack, HumanoidArm arm, float inverseArmHeight, WhipMotion motion) {
		float invert = arm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
		poseStack.translate(invert * HELD_X, HELD_Y + inverseArmHeight * EQUIP_DROP, HELD_Z);
		if (!motion.active()) {
			return;
		}
		WhipMotion.Blend blend = motion.blend();
		boolean snag = motion.kind() == WhipMotion.Kind.SNAG;
		float[] from = firstPerson(blend.from(), snag);
		float[] to = firstPerson(blend.to(), snag);
		float t = blend.t();
		poseStack.translate(invert * Mth.lerp(t, from[0], to[0]), Mth.lerp(t, from[1], to[1]), Mth.lerp(t, from[2], to[2]));
		poseStack.translate(invert * GRIP_X, GRIP_Y, GRIP_Z);
		poseStack.rotateDegrees(Axis.XP, Mth.lerp(t, from[3], to[3]));
		poseStack.rotateDegrees(Axis.YP, invert * Mth.lerp(t, from[4], to[4]));
		poseStack.rotateDegrees(Axis.ZP, invert * Mth.lerp(t, from[5], to[5]));
		poseStack.translate(-invert * GRIP_X, -GRIP_Y, -GRIP_Z);
	}

	private static float[] firstPerson(WhipMotion.Pose pose, boolean snag) {
		return switch (pose) {
			case REST -> FP_REST;
			case WINDUP -> snag ? FP_SNAG_WINDUP : FP_CRACK_WINDUP;
			case STRIKE -> snag ? FP_SNAG_STRIKE : FP_CRACK_STRIKE;
			case YANK -> FP_SNAG_YANK;
		};
	}

	/**
	 * In place of vanilla's attack swing (HumanoidModel.setupAttackAnimation): the whip arm and a twist of the body. The
	 * rest pose is wherever the arm already is (walking, holding the item); crouching and the idle bob still add on after.
	 */
	public static void poseArm(HumanoidModel<?> model, HumanoidRenderState state, HumanoidArm arm, WhipMotion motion) {
		float invert = arm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
		ModelPart whipArm = model.getArm(arm);
		float[] rest = {whipArm.xRot, whipArm.yRot, whipArm.zRot, 0.0f};
		WhipMotion.Blend blend = motion.blend();
		float[] from = thirdPerson(blend.from(), motion.kind(), rest, model.head, invert);
		float[] to = thirdPerson(blend.to(), motion.kind(), rest, model.head, invert);
		float t = blend.t();

		// The body turns as vanilla's swing turns it, and both shoulders go round with it.
		model.body.yRot = Mth.lerp(t, from[3], to[3]);
		float ageScale = state.ageScale;
		model.rightArm.z = Mth.sin(model.body.yRot) * 5.0f * ageScale;
		model.rightArm.x = -Mth.cos(model.body.yRot) * 5.0f * ageScale;
		model.leftArm.z = -Mth.sin(model.body.yRot) * 5.0f * ageScale;
		model.leftArm.x = Mth.cos(model.body.yRot) * 5.0f * ageScale;
		ModelPart otherArm = model.getArm(arm.getOpposite());
		otherArm.yRot += model.body.yRot;

		whipArm.xRot = Mth.lerp(t, from[0], to[0]);
		whipArm.yRot = Mth.lerp(t, from[1], to[1]) + model.body.yRot;
		whipArm.zRot = Mth.lerp(t, from[2], to[2]);
	}

	/** Arm x, y, z turns and the body's turn for a pose (right arm; invert mirrors them for the left). */
	private static float[] thirdPerson(WhipMotion.Pose pose, WhipMotion.Kind kind, float[] rest, ModelPart head, float invert) {
		boolean snag = kind == WhipMotion.Kind.SNAG;
		// Out along the line of sight, a little low.
		float aimX = Mth.clamp((float) (-Math.PI / 2) + head.xRot, -2.6f, -0.3f);
		float aimY = head.yRot * 0.6f;
		return switch (pose) {
			case REST -> rest;
			case WINDUP -> snag
					? new float[] {ARM_SNAG_WINDUP, invert * 0.1f, invert * 0.15f, invert * 0.15f}
					: new float[] {ARM_WINDUP, invert * 0.1f, invert * 0.25f, invert * 0.35f};
			case STRIKE -> snag
					? new float[] {aimX, aimY - invert * 0.05f, -invert * 0.05f, -invert * 0.15f}
					: new float[] {aimX + ARM_STRIKE_DROP, aimY - invert * 0.15f, -invert * 0.1f, -invert * 0.3f};
			case YANK -> new float[] {aimX + ARM_YANK_DROP, aimY, invert * 0.05f, invert * 0.1f};
		};
	}

	/**
	 * Third person, the whip turns in the hand as well: cocked back for the wind-up, flicked forward on the strike. Applied
	 * in the item-in-hand layer's hold frame (after its turn to the hand), where turning about x is turning about the arm's
	 * own x, the other way round, about the grip.
	 */
	public static void applyWrist(PoseStack poseStack, WhipMotion motion) {
		if (!motion.active()) {
			return;
		}
		WhipMotion.Blend blend = motion.blend();
		boolean snag = motion.kind() == WhipMotion.Kind.SNAG;
		float angle = Mth.lerp(blend.t(), wrist(blend.from(), snag), wrist(blend.to(), snag));
		poseStack.rotateDegrees(Axis.XP, -angle);
	}

	/** Degrees the whip is flicked forward in the hand (negative: cocked back). */
	private static float wrist(WhipMotion.Pose pose, boolean snag) {
		return switch (pose) {
			case REST -> 0.0f;
			case WINDUP -> snag ? -15.0f : -25.0f;
			case STRIKE -> snag ? 60.0f : 70.0f;
			case YANK -> 30.0f;
		};
	}
}
