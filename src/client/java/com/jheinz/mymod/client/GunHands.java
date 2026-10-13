package com.jheinz.mymod.client;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Item;

import com.jheinz.mymod.GunRecoil;
import com.jheinz.mymod.ModItems;
import com.jheinz.mymod.energy.EnergyWeapons;
import com.jheinz.mymod.client.launcher.LauncherAnimation;
import com.jheinz.mymod.launcher.Launchers;
import com.jheinz.mymod.napalm.Napalm;

/**
 * How the player's arms hold each gun in first person. The shoulders stay fixed just off the bottom of
 * the screen while the hands ride on the gun, so bobbing, turning sway and recoil swing the hands and the
 * arms stretch to follow.
 *
 * <p>Grip anchors are in the space vanilla renders the held item in (after its arm transform, in blocks:
 * +x right, +y up, +z toward the camera), worked out from each gun's model and first-person display
 * transform. Shoulders are in view space (camera at the origin looking down -z).
 */
public final class GunHands {
	/** @param support where the off hand goes: under the handguard, or cupping the grip on a pistol */
	public record Anchors(Vector3f grip, Vector3f support) {
	}

	private static final Vector3f MAIN_SHOULDER = new Vector3f(0.42f, -0.78f, -0.12f);
	private static final Vector3f OFF_SHOULDER = new Vector3f(-0.40f, -0.80f, -0.30f);
	// Arms are drawn slimmer than vanilla's so they suit the guns.
	private static final float ARM_WIDTH = 0.5f;
	// Arm model: the shoulder pivot and the centre of the hand along its axis (pixels from the model origin).
	private static final float ARM_AXIS_X = 6.0f;
	private static final float SHOULDER_Y = 2.0f;
	private static final float HAND_Y = 12.0f;

	private GunHands() {
	}

	public static Anchors anchors(Item gun) {
		if (gun == ModItems.M4) {
			return rifle(-0.022f, 0.119f, 0.156f, 0.055f, 0.179f, -0.390f);
		} else if (gun == ModItems.AKM) {
			return rifle(-0.048f, 0.156f, 0.150f, 0.029f, 0.197f, -0.392f);
		} else if (gun == Launchers.RPG7) {
			return LauncherAnimation.anchors();
		} else if (gun == ModItems.AWP) {
			return rifle(-0.048f, 0.156f, 0.151f, 0.028f, 0.194f, -0.389f);
		} else if (gun == ModItems.GALIL) {
			return rifle(-0.047f, 0.156f, 0.151f, 0.033f, 0.197f, -0.421f);
		} else if (gun == ModItems.MP5) {
			return rifle(-0.047f, 0.125f, 0.150f, 0.037f, 0.202f, -0.447f);
		} else if (gun == ModItems.MAC10) {
			return rifle(-0.020f, 0.037f, -0.181f, 0.008f, 0.191f, -0.444f);
		} else if (gun == ModItems.M249) {
			return rifle(-0.022f, 0.119f, 0.156f, 0.041f, 0.185f, -0.290f);
		} else if (gun == ModItems.REMINGTON_870) {
			// The shooting hand round the stock's wrist, the off hand under the fore-end (it rides the pump).
			return rifle(-0.022f, 0.119f, 0.156f, 0.069f, 0.086f, -0.491f);
		} else if (gun == ModItems.SPAS12) {
			return rifle(-0.022f, 0.119f, 0.156f, 0.062f, 0.154f, -0.441f);
		} else if (gun == Napalm.FLAMETHROWER) {
			// The shooting hand on the rear grip, the off hand on the front grip (tools/gen_napalm.py).
			return rifle(-0.022f, 0.119f, 0.156f, 0.034f, 0.133f, -0.242f);
		} else if (gun == EnergyWeapons.LASER_RIFLE) {
			// The shooting hand on the pistol grip, the off hand under the emitter shroud (tools/gen_energy_weapons.py).
			return rifle(-0.022f, 0.119f, 0.156f, 0.050f, 0.188f, -0.355f);
		} else if (gun == EnergyWeapons.RAILGUN) {
			// The off hand under the rails, between the coils.
			return rifle(-0.022f, 0.119f, 0.156f, 0.045f, 0.153f, -0.321f);
		} else if (gun == ModItems.GLOCK || gun == ModItems.M1911 || gun == ModItems.REVOLVER_357 || gun == EnergyWeapons.RAY_GUN) {
			return pistol(-0.020f, 0.131f, -0.181f);
		}
		return null;
	}

	private static Anchors rifle(float gx, float gy, float gz, float sx, float sy, float sz) {
		return new Anchors(new Vector3f(gx, gy, gz), new Vector3f(sx, sy, sz));
	}

	private static Anchors pistol(float gx, float gy, float gz) {
		return new Anchors(new Vector3f(gx, gy, gz), new Vector3f(gx - 0.07f, gy - 0.03f, gz + 0.03f));
	}

	/** A slow breathing drift for the gun (and so the hands), applied to the item's pose; {@code amount} scales it. */
	public static void applyIdleSway(PoseStack poseStack, float amount) {
		float t = (Util.getMillis() % 1_000_000L) / 1000.0f;
		poseStack.translate(Mth.sin(t * 0.9f) * 0.004f * amount, Mth.sin(t * 1.7f) * 0.005f * amount, 0.0f);
		poseStack.rotateDegrees(com.mojang.math.Axis.ZP, Mth.sin(t * 0.7f) * 0.6f * amount);
	}

	/**
	 * Kicks the gun back and up by the current recoil (scaled by {@code amount}), pivoting at the grip (or the
	 * item origin) with a little random twist. The hands ride along since they follow the gun's pose.
	 */
	public static void applyRecoil(PoseStack poseStack, Anchors anchors, float amount) {
		float kick = GunRecoil.kick() * amount;
		if (kick < 0.001f) {
			return;
		}
		Vector3f pivot = anchors == null ? new Vector3f() : anchors.grip();
		poseStack.translate(0.0f, kick * 0.02f, kick * 0.11f);
		poseStack.rotateAround(new Quaternionf().rotationXYZ(
				(float) Math.toRadians(kick * 9.0f), 0.0f, (float) Math.toRadians(kick * GunRecoil.roll() * 3.0f)),
				pivot.x, pivot.y, pivot.z);
	}

	/** Where an item-space anchor ends up in view space, given the item's pose and the view pose. */
	public static Vector3f toView(PoseStack.Pose view, Matrix4f itemPose, Vector3f anchor, boolean mirrored) {
		Matrix4f itemToView = new Matrix4f(view.pose()).invert().mul(itemPose);
		return itemToView.transformPosition(new Vector3f(mirrored ? -anchor.x : anchor.x, anchor.y, anchor.z));
	}

	/**
	 * Sets the pose so the given arm model, drawn next, runs from its fixed shoulder to {@code hand}, both in
	 * view space ({@code view} is the pose first-person rendering starts from, which turns with the camera).
	 * {@code mirrored} flips the shoulders for left-handed players.
	 */
	public static void poseArm(PoseStack poseStack, PoseStack.Pose view, HumanoidArm arm, boolean mainArm, Vector3f hand,
			boolean mirrored) {
		float flip = mirrored ? -1.0f : 1.0f;
		Vector3f shoulder = new Vector3f(mainArm ? MAIN_SHOULDER : OFF_SHOULDER).mul(flip, 1.0f, 1.0f);
		Vector3f reach = new Vector3f(hand).sub(shoulder);
		float length = reach.length();
		Vector3f yAxis = reach.div(length);
		// Back of the main hand faces out to the side; the off hand cups the gun from below.
		Vector3f outward = mainArm ? new Vector3f(flip, -0.3f, 0.0f) : new Vector3f(-flip * 0.4f, -1.0f, 0.0f);
		Vector3f xAxis = outward.sub(new Vector3f(yAxis).mul(outward.dot(yAxis))).normalize();
		// The arm model's outer side is -X for the right arm, +X for the left.
		if (arm == HumanoidArm.RIGHT) {
			xAxis.negate();
		}
		Vector3f zAxis = new Vector3f(xAxis).cross(yAxis);

		float axisX = arm == HumanoidArm.RIGHT ? -ARM_AXIS_X : ARM_AXIS_X;
		poseStack.last().set(view);
		poseStack.translate(shoulder.x, shoulder.y, shoulder.z);
		poseStack.mulPose(new Matrix4f().set3x3(new Matrix3f(xAxis, yAxis, zAxis)));
		poseStack.scale(ARM_WIDTH, length / ((HAND_Y - SHOULDER_Y) / 16.0f), ARM_WIDTH);
		poseStack.translate(-axisX / 16.0f, -SHOULDER_Y / 16.0f, 0.0f);
	}
}
