package com.jheinz.mymod.client;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/** Glowing beams (crucifix light, optic lasers) are built from these: flat strips turned to face the camera. */
public final class BeamRibbon {
	private BeamRibbon() {
	}

	/** A flat strip from a to b (relative to the camera, which is at the origin), turned to face the camera. */
	public static void draw(PoseStack.Pose pose, VertexConsumer buffer, Vector3f a, Vector3f b, float halfWidth, float r, float g,
			float bl, float alpha) {
		Vector3f direction = new Vector3f(b).sub(a);
		Vector3f mid = new Vector3f(a).add(b).mul(0.5f);
		Vector3f side = new Vector3f(direction).cross(mid);
		if (side.lengthSquared() < 1.0e-10f) {
			// Pointing straight at the camera: any sideways direction will do.
			side.set(direction).cross(0.0f, 1.0f, 0.0f);
			if (side.lengthSquared() < 1.0e-10f) {
				side.set(1.0f, 0.0f, 0.0f);
			}
		}
		side.normalize().mul(halfWidth);
		Vector3f[] corners = {new Vector3f(a).sub(side), new Vector3f(a).add(side), new Vector3f(b).add(side), new Vector3f(b).sub(side)};
		// Wind it counter-clockwise as seen from the camera, so it isn't culled.
		Vector3f normal = new Vector3f(corners[1]).sub(corners[0]).cross(new Vector3f(corners[2]).sub(corners[0]));
		boolean facing = normal.dot(new Vector3f(corners[0]).negate()) > 0.0f;
		for (int i = 0; i < 4; i++) {
			Vector3f corner = corners[facing ? i : 3 - i];
			buffer.addVertex(pose, corner.x, corner.y, corner.z).setColor(r, g, bl, alpha);
		}
	}
}
