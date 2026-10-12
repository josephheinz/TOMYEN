package com.jheinz.mymod.client.schnozzle;

import java.util.HashMap;
import java.util.Map;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.ModNetworking;
import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.schnozzle.NoseHairWhipItem;

/**
 * Draws the Nose Hair Whip's lash for every player using one: a hairy ribbon from the hand that flicks out to the
 * target and snaps back, bulging into a travelling loop on the way out and back with a wave running down it to the tip.
 *
 * <p>An attack lash follows the player's attack swing (its progress drives the flick) and reaches along their line of
 * sight to whatever is within {@value NoseHairWhipItem#ATTACK_REACH} blocks. A snag lash is announced by the server
 * ({@link ModNetworking.WhipLash}) with the point it caught, and holds taut there for a moment before coming back.
 */
public final class WhipLashes {
	private static final Identifier TEXTURE = MyMod.id("textures/entity/nose_hair_whip_lash.png");
	private static final int SEGMENTS = 28;
	private static final long SNAG_MILLIS = 560;
	/** Half-widths of the ribbon at the hand and at the tip. The texture's hair fills the middle half of it. */
	private static final float HAND_WIDTH = 0.075f;
	private static final float TIP_WIDTH = 0.03f;
	/** How far the lash bulges out of line, as a share of its length, halfway out (and halfway back). */
	private static final double LOOP = 0.5;
	private static final double WAVE = 0.12;
	private static final double MAX_DISTANCE = 96.0;

	private record Snag(Vec3 to, long startMillis) {
	}

	private static final Map<Integer, Snag> SNAGS = new HashMap<>();

	private WhipLashes() {
	}

	public static void receive(ModNetworking.WhipLash payload) {
		SNAGS.put(payload.playerId(), new Snag(payload.to(), Util.getMillis()));
	}

	public static void submit(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		long now = Util.getMillis();
		SNAGS.values().removeIf(snag -> now - snag.startMillis > SNAG_MILLIS);
		if (minecraft.level == null) {
			return;
		}
		Vec3 camera = context.levelState().cameraRenderState.pos;
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		for (Player player : minecraft.level.players()) {
			if (!NoseHairWhipItem.isHeldBy(player) || player.isSpectator() || player.isInvisible()
					|| player.distanceToSqr(camera) > MAX_DISTANCE * MAX_DISTANCE) {
				continue;
			}
			Snag snag = SNAGS.get(player.getId());
			LivingEntity.SwingDescription swing = player.getCurrentSwing();
			if (snag != null) {
				float progress = (now - snag.startMillis) / (float) SNAG_MILLIS;
				// Out fast, hang on taut while it pulls, then back.
				float extension = progress < 0.3f ? easeOut(progress / 0.3f) : progress < 0.55f ? 1.0f : 1.0f - easeIn((progress - 0.55f) / 0.45f);
				draw(context, minecraft.level, camera, hand(minecraft, player, partialTick), snag.to, extension, progress);
			} else if (swing != null && swing.hand() == InteractionHand.MAIN_HAND && swing.animation().equals(NoseHairWhipItem.ATTACK_SWING)) {
				float progress = player.getSwingAnimation(partialTick);
				// The crack comes at full stretch, 40% of the way through the swing.
				float extension = progress < 0.4f ? easeOut(progress / 0.4f) : 1.0f - easeIn((progress - 0.4f) / 0.6f);
				draw(context, minecraft.level, camera, hand(minecraft, player, partialTick), attackTip(player, partialTick), extension, progress);
			}
		}
	}

	private static float easeOut(float t) {
		float u = 1.0f - Mth.clamp(t, 0.0f, 1.0f);
		return 1.0f - u * u * u;
	}

	private static float easeIn(float t) {
		float u = Mth.clamp(t, 0.0f, 1.0f);
		return u * u;
	}

	/** Roughly where the whip's handle is: on screen for our own in first person, at the side for everyone else. */
	private static Vec3 hand(Minecraft minecraft, Player player, float partialTick) {
		Vec3 look = player.getViewVector(partialTick);
		float side = player.getMainArm() == HumanoidArm.RIGHT ? 1.0f : -1.0f;
		Vec3 right = Vec3.directionFromRotation(0.0f, player.getViewYRot(partialTick) + 90.0f).scale(side);
		Vec3 eye = player.getEyePosition(partialTick);
		if (player == minecraft.player && minecraft.options.getCameraType().isFirstPerson()) {
			return eye.add(look.scale(0.75)).add(right.scale(0.3)).add(0.0, -0.25, 0.0);
		}
		return eye.add(look.scale(0.55)).add(right.scale(0.38)).add(0.0, -0.4, 0.0);
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
	 * The lash from the hand toward the target, stretched out to {@code extension} (0 coiled at the hand, 1 at the
	 * target). Slack makes a loop that rides up on the way out and drops on the way back.
	 */
	private static void draw(LevelRenderContext context, ClientLevel level, Vec3 camera, Vec3 hand, Vec3 target, float extension,
			float progress) {
		if (extension <= 0.01f) {
			return;
		}
		Vec3 reach = target.subtract(hand);
		double length = reach.length();
		if (length < 0.1) {
			return;
		}
		Vec3 along = reach.scale(1.0 / length);
		Vec3 up = new Vec3(0.0, 1.0, 0.0).subtract(along.scale(along.y));
		up = up.lengthSqr() < 1.0e-4 ? new Vec3(1.0, 0.0, 0.0) : up.normalize();
		Vec3 sideways = along.cross(up);
		boolean goingOut = progress < 0.4f;
		double loop = LOOP * length * extension * (1.0 - extension) * (goingOut ? 1.0 : -0.6);
		double wave = WAVE * (1.0 - 0.5 * extension) * Math.min(1.0, length / 3.0);

		Vector3f[] points = new Vector3f[SEGMENTS + 1];
		int[] light = new int[SEGMENTS + 1];
		for (int i = 0; i <= SEGMENTS; i++) {
			double s = i / (double) SEGMENTS;
			double bulge = Math.sin(Math.PI * s);
			// The loop leans a little sideways too, and the wave runs from the hand to the tip as the lash goes.
			Vec3 point = hand.add(reach.scale(extension * s))
					.add(up.scale(loop * bulge))
					.add(sideways.scale(0.35 * loop * bulge + wave * bulge * Math.sin(Math.PI * 2.0 * (1.25 * s - 2.5 * progress))));
			points[i] = point.subtract(camera).toVector3f();
			light[i] = LightCoordsUtil.getLightCoords(level, BlockPos.containing(point));
		}
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entityCutout(TEXTURE),
				(pose, buffer) -> ribbon(pose, buffer, points, light));
	}

	/** A strip through the points, turned to face the camera (at the origin) and tapering to the tip. */
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
			float width = Mth.lerp(i / (float) (n - 1), HAND_WIDTH, TIP_WIDTH);
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
