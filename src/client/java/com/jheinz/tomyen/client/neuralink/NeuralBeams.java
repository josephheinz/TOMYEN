package com.jheinz.tomyen.client.neuralink;

import java.util.Optional;

import org.joml.Vector3f;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.GlobalPos;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.client.BeamRibbon;
import com.jheinz.tomyen.neuralink.NeuralinkItem;
import com.jheinz.tomyen.neuralink.NeuralinkModule;

/**
 * Neuralink beams drawn in the world: every lasering player's twin optic lasers (red, from each eye to what the
 * gaze lands on, traced on the client so they follow the head smoothly), and Recall's light pillars over the
 * wearer's last death and respawn point.
 */
public final class NeuralBeams {
	/** Matches the server's reach (OpticLasers.RANGE). */
	private static final double LASER_RANGE = 32.0;
	/** Half the distance between the eyes, and how far in front of the face the beams start. */
	private static final double EYE_SPACING = 0.11;
	private static final double FACE_DEPTH = 0.25;
	private static final float GLOW_WIDTH = 0.06f;
	private static final float CORE_WIDTH = 0.016f;
	private static final double PILLAR_HEIGHT = 64.0;
	private static final double PILLAR_RANGE = 256.0;

	/** The level the lasering ids belong to; they mean nothing in another. */
	private static ClientLevel lastLevel;

	private NeuralBeams() {
	}

	public static void submit(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) {
			return;
		}
		if (minecraft.level != lastLevel) {
			lastLevel = minecraft.level;
			NeuralClient.LASERING.clear();
		}
		Vec3 camera = context.levelState().cameraRenderState.pos;
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		// Someone who leaves view mid-beam is forgotten; the server repeats "on" every second while it lasts.
		NeuralClient.LASERING.removeIf(id -> !(minecraft.level.getEntity(id) instanceof Player));
		for (int id : NeuralClient.LASERING) {
			if (minecraft.level.getEntity(id) instanceof Player player && player.isAlive()) {
				lasers(context, minecraft, player, camera, partialTick);
			}
		}
		if (NeuralinkItem.isActive(minecraft.player, NeuralinkModule.RECALL)) {
			pillar(context, minecraft, NeuralClient.deathPoint, camera, 1.0f, 0.3f, 0.3f);
			pillar(context, minecraft, NeuralClient.respawnPoint, camera, 0.5f, 1.0f, 1.0f);
		}
	}

	private static void lasers(LevelRenderContext context, Minecraft minecraft, Player player, Vec3 camera, float partialTick) {
		Vec3 eye = player.getEyePosition(partialTick);
		Vec3 look = player.getViewVector(partialTick);
		Vec3 end = eye.add(look.scale(LASER_RANGE));
		HitResult block = minecraft.level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		AABB sweep = new AABB(eye, end).inflate(1.0);
		EntityHitResult entity = ProjectileUtil.getEntityHitResult(player, eye, end, sweep,
				other -> !other.isSpectator() && other.isPickable(), eye.distanceToSqr(end));
		if (entity != null) {
			end = entity.getLocation();
		}

		Vec3 right = new Vec3(-look.z, 0.0, look.x);
		right = right.lengthSqr() < 1.0e-6 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
		boolean firstPerson = player == minecraft.getCameraEntity() && minecraft.options.getCameraType().isFirstPerson();
		// In first person the beams start a little below and to the sides of the view, so they show without blinding.
		Vec3 face = firstPerson ? eye.add(look.scale(0.4)).add(0.0, -0.12, 0.0) : eye.add(look.scale(FACE_DEPTH));
		double spacing = firstPerson ? 0.14 : EYE_SPACING;
		float flicker = 0.85f + 0.15f * Mth.sin(Util.getMillis() / 30.0f);
		Vector3f target = end.subtract(camera).toVector3f();
		for (int side = -1; side <= 1; side += 2) {
			Vector3f from = face.add(right.scale(side * spacing)).subtract(camera).toVector3f();
			context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(), (pose, buffer) -> {
				BeamRibbon.draw(pose, buffer, from, target, GLOW_WIDTH * flicker, 1.0f, 0.12f, 0.08f, 0.55f);
				BeamRibbon.draw(pose, buffer, from, target, CORE_WIDTH * flicker, 1.0f, 0.85f, 0.8f, 0.95f);
			});
		}
	}

	/** A tall, faint column of light standing on a waypoint, if it's in this dimension and not too far off. */
	private static void pillar(LevelRenderContext context, Minecraft minecraft, Optional<GlobalPos> point, Vec3 camera, float r, float g, float b) {
		if (point.isEmpty() || point.get().dimension() != minecraft.level.dimension()) {
			return;
		}
		Vec3 base = Vec3.atBottomCenterOf(point.get().pos());
		if (base.distanceTo(camera) > PILLAR_RANGE) {
			return;
		}
		Vector3f bottom = base.subtract(camera).toVector3f();
		Vector3f top = base.add(0.0, PILLAR_HEIGHT, 0.0).subtract(camera).toVector3f();
		float pulse = 0.75f + 0.25f * Mth.sin(Util.getMillis() / 400.0f);
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(), (pose, buffer) -> {
			BeamRibbon.draw(pose, buffer, bottom, top, 0.35f, r, g, b, 0.25f * pulse);
			BeamRibbon.draw(pose, buffer, bottom, top, 0.08f, r, g, b, 0.6f * pulse);
		});
	}
}
