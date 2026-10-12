package com.jheinz.mymod.client;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;

/**
 * Easing and posing shared by the handling animations of the shotguns and the M249 (ShotgunAnimation,
 * M249Animation), which run their own timelines but move the gun the way GunAnimation does: turned toward the
 * working hand about a hold, shaken when parts hit their stops, and turned over for an inspection.
 */
public final class GunMotion {
	private GunMotion() {
	}

	public static float smooth(float t) {
		t = Mth.clamp(t, 0.0f, 1.0f);
		return t * t * (3.0f - 2.0f * t);
	}

	/** How far through the stretch from {@code a} to {@code b} progress {@code p} is, 0 to 1. */
	public static float span(float p, float a, float b) {
		return Mth.clamp((p - a) / (b - a), 0.0f, 1.0f);
	}

	/** Springy shake starting at {@code at} (progress) for an action lasting {@code millis}. */
	public static float jolt(float p, float at, long millis, float size) {
		float t = (p - at) * millis / 1000.0f;
		return t < 0.0f ? 0.0f : size * Mth.sin(t * Mth.TWO_PI * 7.0f) * (float) Math.exp(-t / 0.07f);
	}

	/** Where a model point (pixels) of this gun is in the held item's space, under its first-person display transform. */
	public static Vector3f onGun(Item gun, Vector3f modelPoint) {
		return GunDisplay.of(gun).matrix(false).transformPosition(new Vector3f(modelPoint).div(16.0f));
	}

	/**
	 * Turns the gun by {@code engaged} of a pose (degrees of pitch, yaw, roll with the top to the left, and a lift),
	 * pivoting on {@code pivot}, plus a shake.
	 */
	public static void turn(PoseStack poseStack, Vector3f pivot, float pitch, float yaw, float roll, float lift, float engaged,
			float shake, boolean mirrored) {
		float side = mirrored ? -1.0f : 1.0f;
		poseStack.translate(0.0f, lift * engaged, -shake * 0.012f);
		poseStack.rotateAround(new Quaternionf().rotationXYZ(
				(float) Math.toRadians(pitch * engaged + shake * 2.5f),
				(float) Math.toRadians(yaw * engaged * side),
				(float) Math.toRadians(roll * engaged * side + shake * 1.5f)),
				pivot.x * side, pivot.y, pivot.z);
	}

	/**
	 * An inspection over {@code p} (0 to 1): the gun comes in toward the middle of the screen turned muzzle-left,
	 * showing its left side, then rolls over to bring the right side up to the camera, and goes back, drifting
	 * slowly while it's held up (as GunAnimation's inspection does).
	 */
	public static void inspect(PoseStack poseStack, float p, float seconds, Vector3f pivot, boolean mirrored) {
		float raised = smooth(span(p, 0.0f, 0.12f)) * (1.0f - smooth(span(p, 0.86f, 1.0f)));
		float flipped = smooth(span(p, 0.34f, 0.5f)) * (1.0f - smooth(span(p, 0.84f, 0.97f)));
		float drift = Mth.sin(seconds * 2.3f) * raised;
		float side = mirrored ? -1.0f : 1.0f;
		float roll = -14.0f * raised + 88.0f * flipped + drift * 2.0f;
		float yaw = 38.0f * raised - 14.0f * flipped + drift;
		float pitch = 6.0f * raised + 8.0f * flipped;
		poseStack.translate(-0.1f * raised * side, 0.05f * raised + 0.03f * flipped, 0.07f * raised);
		poseStack.rotateAround(new Quaternionf().rotationXYZ(
				(float) Math.toRadians(pitch), (float) Math.toRadians(yaw * side), (float) Math.toRadians(roll * side)),
				pivot.x * side, pivot.y, pivot.z);
	}
}
