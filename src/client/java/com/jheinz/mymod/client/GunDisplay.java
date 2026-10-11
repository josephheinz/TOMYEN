package com.jheinz.mymod.client;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import net.minecraft.world.item.Item;

import com.jheinz.mymod.ModItems;

/**
 * A gun's first-person display transform, copied from its item model (both hands use the same one;
 * vanilla mirrors it for the left). Lets code place things on the gun model, in model pixels, in the
 * space the held item's pose is in.
 *
 * @param translation in pixels
 * @param yaw         in degrees (every gun's display rotation is a yaw only)
 */
public record GunDisplay(Vector3f translation, float yaw, float scale) {
	public static GunDisplay of(Item gun) {
		if (gun == ModItems.GLOCK) {
			return display(0.0f, 3.0f, -6.0f, -6, 0.65f);
		} else if (gun == ModItems.M1911) {
			return display(-0.56f, 3.08f, -0.71f, -6, 0.65f);
		} else if (gun == ModItems.REVOLVER_357) {
			return display(-0.46f, 3.5f, -1.66f, -6, 0.65f);
		} else if (gun == ModItems.MAC10) {
			return display(-0.73f, 3.71f, 0.98f, -6, 0.65f);
		} else if (gun == ModItems.MP5) {
			return display(-0.45f, 4.16f, 0.21f, -8, 0.62f);
		} else if (gun == ModItems.M4) {
			return display(0.0f, 4.0f, 0.0f, -8, 0.6f);
		} else if (gun == ModItems.AKM) {
			return display(0.0f, 4.0f, -3.0f, -8, 0.6f);
		} else if (gun == ModItems.GALIL) {
			return display(-0.18f, 4.0f, -1.69f, -8, 0.6f);
		} else if (gun == ModItems.AWP) {
			return display(-0.35f, 3.85f, -0.53f, -8, 0.5f);
		}
		return null;
	}

	private static GunDisplay display(float tx, float ty, float tz, float yaw, float scale) {
		return new GunDisplay(new Vector3f(tx, ty, tz), yaw, scale);
	}

	/** What vanilla applies after the held item's pose: model blocks (pixels / 16) to item pose space. */
	public Matrix4f matrix(boolean mirrored) {
		float side = mirrored ? -1.0f : 1.0f;
		return new Matrix4f()
				.translate(translation.x / 16.0f * side, translation.y / 16.0f, translation.z / 16.0f)
				.rotateY((float) Math.toRadians(yaw * side))
				.scale(scale)
				.translate(-0.5f, -0.5f, -0.5f);
	}
}
