package com.jheinz.tomyen.client.schnozzle;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.ModNetworking;
import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.schnozzle.NoseHairWhipItem;

/**
 * Draws the Nose Hair Whip's lash, a hairy ribbon fixed to the tip of the handle ({@link WhipHandles}) of every whip in
 * sight: a short length hanging from it at rest, and for a crack or a snag the whole lash, moving with the hand
 * ({@link WhipMotion}).
 *
 * <p>The lash leaves the handle along the handle and bends from there, tapering to the tip. A crack streams it out
 * behind the hand on the wind-up; on the snap it turns over in a loop that rolls out along it, the tip whipping past
 * the hand and cracking straight out at whatever is within {@value NoseHairWhipItem#ATTACK_REACH} blocks along the line
 * of sight, then it slackens and falls back to hang. A snag ({@link ModNetworking.WhipLash}, from the server, with the
 * point it caught) is thrown the same way, holds taut while it pulls and is yanked back.
 */
public final class WhipLashes {
	private static final Identifier TEXTURE = MyMod.id("textures/entity/nose_hair_whip_lash.png");
	private static final int SEGMENTS = 32;
	/** A snag's whole throw, hold and yank, in ticks. */
	private static final float SNAG_TICKS = 11.0f;
	/** Half-widths of the ribbon at the handle and at the tip. The texture's hair fills the middle half of it. */
	private static final float HANDLE_WIDTH = 0.08f;
	private static final float TIP_WIDTH = 0.025f;
	/** The ribbon starts this far back inside the booger binding, so there's never a gap at the handle. */
	private static final double TUCK = 0.03;
	/** How much lash hangs from the handle at rest, and streams out behind on the wind-up. */
	private static final double REST_LENGTH = 0.8;
	private static final double TRAIL_LENGTH = 2.0;
	/** How far the lash runs along the handle's line before bending away, at rest and when lashing. */
	private static final double REST_BEND = 0.15;
	private static final double LASH_BEND = 0.35;
	/** The turnover loop's length along the lash, and how far above straight back the lash streams on the wind-up. */
	private static final double LOOP_LENGTH = 0.9;
	private static final double TRAIL_RISE = 0.45;
	/** Further along the lash follows later: how far behind the handle end the tip is in a change of shape. */
	private static final double LAG = 0.7;
	private static final double WAVE = 0.1;
	private static final double MAX_DISTANCE = 96.0;

	private record Snag(Vec3 to, float startAge) {
	}

	private static final Map<Integer, Snag> SNAGS = new HashMap<>();

	private WhipLashes() {
	}

	public static void receive(ModNetworking.WhipLash payload) {
		Minecraft minecraft = Minecraft.getInstance();
		Entity player = minecraft.level == null ? null : minecraft.level.getEntity(payload.playerId());
		if (player != null) {
			float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
			SNAGS.put(payload.playerId(), new Snag(payload.to(), player.tickCount + partialTick));
		}
	}

	/** How far through a snag the player with this id is, at this age (ticks), or -1 if they aren't snagging. */
	public static float snagProgress(int playerId, float ageInTicks) {
		Snag snag = SNAGS.get(playerId);
		if (snag == null) {
			return -1.0f;
		}
		float progress = (ageInTicks - snag.startAge) / SNAG_TICKS;
		return progress >= 1.0f ? -1.0f : Math.max(progress, 0.0f);
	}

	public static void submit(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			SNAGS.clear();
			return;
		}
		SNAGS.entrySet().removeIf(entry -> {
			Entity player = level.getEntity(entry.getKey());
			return player == null || player.tickCount > entry.getValue().startAge + SNAG_TICKS + 1.0f;
		});
		CameraRenderState camera = context.levelState().cameraRenderState;
		Vec3 cameraPos = camera.pos;
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Set<Integer> lashing = new HashSet<>();

		// Whips drawn in third person this frame, by anyone.
		for (WhipHandles.Handle handle : WhipHandles.captured()) {
			Vec3 tip = handle.tip().add(cameraPos);
			Player player = handle.mainHand() && handle.motion().active() && level.getEntity(handle.entityId()) instanceof Player p ? p : null;
			if (player != null && lashing.add(player.getId())) {
				lash(context, level, cameraPos, player, tip, handle.along(), handle.motion(), handle.ageInTicks(), partialTick);
			} else {
				draw(context, level, cameraPos, shape(tip, handle.along(), null, WhipMotion.RESTING, handle.ageInTicks()));
			}
		}

		// Our own, in first person.
		PlayerRenderState playerState = context.levelState().playerRenderState;
		if (camera.isFirstPerson && minecraft.player != null && minecraft.getCameraEntity() == minecraft.player && playerState.avatarRenderState != null
				&& !minecraft.player.isSpectator()) {
			for (InteractionHand hand : InteractionHand.values()) {
				WhipHandles.Handle handle = WhipHandles.firstPerson(camera, playerState.avatarRenderState, playerState.firstPersonHandsAndItems, hand);
				if (handle == null) {
					continue;
				}
				Vec3 tip = handle.tip().add(cameraPos);
				if (handle.mainHand() && handle.motion().active() && lashing.add(minecraft.player.getId())) {
					lash(context, level, cameraPos, minecraft.player, tip, handle.along(), handle.motion(), handle.ageInTicks(), partialTick);
				} else {
					draw(context, level, cameraPos, shape(tip, handle.along(), null, WhipMotion.RESTING, handle.ageInTicks()));
				}
			}
		}

		// Anyone lashing whose arm wasn't drawn (out of sight): from about where their hand is.
		for (Player player : level.players()) {
			if (lashing.contains(player.getId()) || !NoseHairWhipItem.isHeldBy(player) || player.isSpectator() || player.isInvisible()
					|| player.distanceToSqr(cameraPos) > MAX_DISTANCE * MAX_DISTANCE) {
				continue;
			}
			float age = player.tickCount + partialTick;
			float snag = snagProgress(player.getId(), age);
			WhipMotion motion = snag >= 0.0f ? new WhipMotion(WhipMotion.Kind.SNAG, snag)
					: WhipMotion.crack(player.getCurrentSwing(), player.getSwingAnimation(partialTick));
			if (motion.active()) {
				lash(context, level, cameraPos, player, estimatedHand(player, partialTick), player.getViewVector(partialTick), motion, age,
						partialTick);
			}
		}
	}

	/** A crack or snag from this handle tip. */
	private static void lash(LevelRenderContext context, ClientLevel level, Vec3 cameraPos, Player player, Vec3 tip, Vec3 along,
			WhipMotion motion, float age, float partialTick) {
		Snag snag = SNAGS.get(player.getId());
		Vec3 target = motion.kind() == WhipMotion.Kind.SNAG && snag != null ? snag.to : attackTip(player, partialTick);
		draw(context, level, cameraPos, shape(tip, along, target, motion, age));
	}

	/** About where a player's whip hand is, for when their arm isn't drawn. */
	private static Vec3 estimatedHand(Player player, float partialTick) {
		Vec3 look = player.getViewVector(partialTick);
		float side = player.getMainArm() == HumanoidArm.RIGHT ? 1.0f : -1.0f;
		Vec3 right = Vec3.directionFromRotation(0.0f, player.getViewYRot(partialTick) + 90.0f).scale(side);
		return player.getEyePosition(partialTick).add(look.scale(0.55)).add(right.scale(0.38)).add(0.0, -0.4, 0.0);
	}

	/** What an attack lash reaches: the first creature or block along the line of sight, else full reach. */
	private static Vec3 attackTip(Player player, float partialTick) {
		Vec3 start = player.getEyePosition(partialTick);
		Vec3 end = start.add(player.getViewVector(partialTick).scale(NoseHairWhipItem.ATTACK_REACH));
		end = player.level().clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player)).getLocation();
		EntityHitResult entity = ProjectileUtil.getEntityHitResult(player, start, end, new AABB(start, end).inflate(1.0),
				target -> !target.isSpectator() && target.isPickable(), start.distanceToSqr(end));
		return entity != null ? entity.getLocation() : end;
	}

	/**
	 * The lash's points, from just inside the handle tip to the lash's tip.
	 *
	 * <p>Its shape is a direction for every point along it, walked out from the handle: hanging, streaming back, a loop
	 * turning over from back to forward, or straight at the target. Changes of shape reach the handle end first and the
	 * tip last ({@link #lagged}), the first {@link #LASH_BEND} blocks bend away from the handle's own line, and while it's
	 * reaching for its target the tip is drawn onto it (bending only the far end), so it cracks exactly there.
	 */
	private static Vec3[] shape(Vec3 tip, Vec3 along, @Nullable Vec3 target, WhipMotion motion, float age) {
		Vec3 flat = new Vec3(along.x, 0.0, along.z);
		Vec3 sway = new Vec3(Mth.sin(age * 0.11f) * 0.07, 0.0, Mth.cos(age * 0.083f) * 0.07);
		if (!motion.active() || target == null) {
			return walk(tip, along, REST_LENGTH, REST_BEND, s -> hang(flat, sway, s), null, 0.0, motion, 0.0);
		}

		Vec3 reach = target.subtract(tip);
		double distance = reach.length();
		Vec3 forward = distance < 1.0e-3 ? along : reach.scale(1.0 / distance);
		Vec3 vertical = new Vec3(0.0, 1.0, 0.0).subtract(forward.scale(forward.y));
		Vec3 up = vertical.lengthSqr() < 1.0e-4 ? new Vec3(1.0, 0.0, 0.0) : vertical.normalize();
		Vec3 side = forward.cross(up);
		double full = Math.max(distance, REST_LENGTH);
		double trail = Math.min(full, TRAIL_LENGTH);
		boolean snag = motion.kind() == WhipMotion.Kind.SNAG;
		float windup = snag ? WhipMotion.SNAG_WINDUP : WhipMotion.CRACK_WINDUP;
		float taut = snag ? WhipMotion.SNAG_CATCH : WhipMotion.CRACK_AT;
		float release = snag ? WhipMotion.SNAG_HOLD : WhipMotion.CRACK_AT;
		float p = motion.progress();

		if (p < windup) {
			// Streaming out behind as the hand goes back.
			double w = WhipMotion.easeOut(p / windup);
			double length = Mth.lerp(w, REST_LENGTH, trail);
			double loop = LOOP_LENGTH / length;
			return walk(tip, along, length, Mth.lerp(w, REST_BEND, LASH_BEND),
					s -> nlerp(hang(flat, sway, s), turnover(forward, up, side, s, -loop, loop), lagged(w, s)), null, 0.0, motion, 0.0);
		}
		if (p < taut) {
			// The turnover: the loop rolls from the handle to the tip, faster and faster, and the tip cracks out straight.
			double t = (p - windup) / (taut - windup);
			double length = Mth.lerp(WhipMotion.easeInOut((float) t), trail, full);
			double loop = Math.min(LOOP_LENGTH / length, 0.45);
			double rolled = -loop + (1.0 + loop) * Math.pow(t, 1.7);
			return walk(tip, along, length, LASH_BEND, s -> turnover(forward, up, side, s, rolled, loop), target, Math.pow(t, 4.0), motion,
					WAVE * Math.sin(Math.PI * t) * Math.min(1.0, length / 3.0));
		}
		if (p < release) {
			// A snag holding on, taut and quivering.
			double t = (p - taut) / (release - taut);
			return walk(tip, along, full, LASH_BEND, s -> forward, target, 1.0, motion, 0.04 * Math.sin(Math.PI * t));
		}
		// Slack: it drops, falls back to hang and is reeled in, the handle end first.
		double t = (p - release) / (1.0 - release);
		double w = WhipMotion.easeInOut((float) t);
		double length = Mth.lerp(w, full, REST_LENGTH);
		double drop = 0.7 * Math.sin(Math.PI * t);
		return walk(tip, along, length, Mth.lerp(w, LASH_BEND, REST_BEND),
				s -> nlerp(forward.add(0.0, -drop * s, 0.0), hang(flat, sway, s), lagged(w, s)), target, Math.pow(1.0 - t, 4.0), motion,
				WAVE * 0.6 * Math.sin(Math.PI * t) * Math.min(1.0, length / 3.0));
	}

	/** Hanging from the handle, leaning out a little the way the handle points and swaying at the end. */
	private static Vec3 hang(Vec3 flat, Vec3 sway, double s) {
		return new Vec3(0.0, -1.0, 0.0).add(flat.scale(0.25 * (1.0 - s))).add(sway.scale(s));
	}

	/**
	 * A lash turning over in a loop: forward (at the target) up to {@code rolled} of the way along, then curling up and
	 * over through the loop's length to stream back behind, a little above straight back and leaning to the side.
	 */
	private static Vec3 turnover(Vec3 forward, Vec3 up, Vec3 side, double s, double rolled, double loop) {
		double k = Mth.clamp((s - rolled) / loop, 0.0, 1.0);
		double angle = (Math.PI - TRAIL_RISE) * k * k * (3.0 - 2.0 * k);
		double sin = Math.sin(angle);
		return forward.scale(Math.cos(angle)).add(up.scale(sin)).add(side.scale(0.25 * sin));
	}

	/** How far a change of shape {@code w} of the way through has got at {@code s} along the lash: the far end lags. */
	private static double lagged(double w, double s) {
		return Mth.clamp(w * (1.0 + LAG) - LAG * s, 0.0, 1.0);
	}

	private static Vec3 nlerp(Vec3 from, Vec3 to, double t) {
		Vec3 mixed = from.normalize().scale(1.0 - t).add(to.normalize().scale(t));
		return mixed.lengthSqr() < 1.0e-6 ? to : mixed;
	}

	private interface Field {
		Vec3 at(double s);
	}

	/**
	 * Walks the lash out from the handle tip: the field gives the direction at each point, the first {@code bend} blocks
	 * blending from the handle's own line into it so the lash leaves the handle straight. Then the tip is pulled onto the
	 * target by {@code pull} (bending in from the handle end smoothly), and a wave runs down it sideways.
	 */
	private static Vec3[] walk(Vec3 tip, Vec3 along, double length, double bend, Field field, @Nullable Vec3 target, double pull,
			WhipMotion motion, double wave) {
		Vec3[] points = new Vec3[SEGMENTS + 1];
		points[0] = tip.subtract(along.scale(TUCK));
		double step = (length + TUCK) / SEGMENTS;
		for (int i = 0; i < SEGMENTS; i++) {
			double s = (i + 0.5) / SEGMENTS;
			double straight = Mth.clamp((s * (length + TUCK) - TUCK) / bend, 0.0, 1.0);
			Vec3 direction = nlerp(along, field.at(s), straight * straight * (3.0 - 2.0 * straight)).normalize();
			points[i + 1] = points[i].add(direction.scale(step));
		}
		if (target != null && pull > 0.0) {
			Vec3 miss = target.subtract(points[SEGMENTS]).scale(pull);
			for (int i = 1; i <= SEGMENTS; i++) {
				double s = i / (double) SEGMENTS;
				points[i] = points[i].add(miss.scale(s * s));
			}
		}
		if (wave > 0.0 && target != null) {
			Vec3 reach = target.subtract(tip);
			Vec3 across = reach.cross(new Vec3(0.0, 1.0, 0.0));
			across = across.lengthSqr() < 1.0e-6 ? new Vec3(1.0, 0.0, 0.0) : across.normalize();
			for (int i = 1; i <= SEGMENTS; i++) {
				double s = i / (double) SEGMENTS;
				double envelope = Math.sin(Math.PI * s);
				points[i] = points[i].add(across.scale(wave * envelope * envelope * Math.sin(Math.PI * 2.0 * (1.25 * s - 2.5 * motion.progress()))));
			}
		}
		return points;
	}

	private static void draw(LevelRenderContext context, ClientLevel level, Vec3 cameraPos, Vec3[] points) {
		Vector3f[] relative = new Vector3f[points.length];
		int[] light = new int[points.length];
		for (int i = 0; i < points.length; i++) {
			relative[i] = points[i].subtract(cameraPos).toVector3f();
			light[i] = LightCoordsUtil.getLightCoords(level, BlockPos.containing(points[i]));
		}
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entityCutout(TEXTURE),
				(pose, buffer) -> ribbon(pose, buffer, relative, light));
	}

	/** A strip through the points, turned to face the camera (at the origin) and tapering from the handle to the tip. */
	private static void ribbon(PoseStack.Pose pose, VertexConsumer buffer, Vector3f[] points, int[] light) {
		int n = points.length;
		Vector3f[] side = new Vector3f[n];
		Vector3f[] normal = new Vector3f[n];
		for (int i = 0; i < n; i++) {
			Vector3f tangent = new Vector3f(points[Math.min(i + 1, n - 1)]).sub(points[Math.max(i - 1, 0)]);
			Vector3f toCamera = new Vector3f(points[i]).negate();
			Vector3f across = new Vector3f(tangent).cross(toCamera);
			if (across.lengthSquared() < 1.0e-10f) {
				across.set(tangent).cross(0.0f, 1.0f, 0.0f);
			}
			float width = Mth.lerp(i / (float) (n - 1), HANDLE_WIDTH, TIP_WIDTH);
			side[i] = across.lengthSquared() < 1.0e-10f ? new Vector3f() : across.normalize().mul(width);
			normal[i] = toCamera.lengthSquared() < 1.0e-10f ? new Vector3f(0.0f, 1.0f, 0.0f) : toCamera.normalize();
		}
		for (int i = 0; i < n - 1; i++) {
			float v0 = i / (float) (n - 1);
			float v1 = (i + 1) / (float) (n - 1);
			vertex(pose, buffer, new Vector3f(points[i]).sub(side[i]), 0.0f, v0, light[i], normal[i]);
			vertex(pose, buffer, new Vector3f(points[i]).add(side[i]), 1.0f, v0, light[i], normal[i]);
			vertex(pose, buffer, new Vector3f(points[i + 1]).add(side[i + 1]), 1.0f, v1, light[i + 1], normal[i + 1]);
			vertex(pose, buffer, new Vector3f(points[i + 1]).sub(side[i + 1]), 0.0f, v1, light[i + 1], normal[i + 1]);
		}
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vector3f at, float u, float v, int light, Vector3f normal) {
		buffer.addVertex(pose, at.x, at.y, at.z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
				.setNormal(pose, normal.x, normal.y, normal.z);
	}
}
