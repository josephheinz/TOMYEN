package com.jheinz.mymod.client;

import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Util;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.ModItems;
import com.jheinz.mymod.attachment.AttachmentItem;
import com.jheinz.mymod.attachment.AttachmentSlot;
import com.jheinz.mymod.attachment.Attachments;

/**
 * A faint glowing beam from each laser sight to its dot, fading out over the first few blocks the way a
 * laser shows in hazy air. Drawn in the world for every player with a laser.
 *
 * <p>For our own gun in first person the beam starts exactly at the lens on the gun model. The gun is drawn
 * in a separate pass with its own fixed field of view, so the lens position recorded there is rescaled to
 * where it appears against the world.
 */
public final class LaserBeams {
	/** Where each gun's laser lens is, in model pixels (from tools/gen_attachments.py: the laser box's front face). */
	private static final Map<Item, Vector3f> LENSES = Map.of(
			ModItems.GLOCK, new Vector3f(8.0f, 7.425f, 0.4f),
			ModItems.M1911, new Vector3f(8.0f, 7.425f, -6.0f),
			ModItems.REVOLVER_357, new Vector3f(8.0f, 7.925f, -8.6f),
			ModItems.MAC10, new Vector3f(8.0f, 7.575f, -8.8f),
			ModItems.MP5, new Vector3f(8.0f, 5.925f, -7.5f),
			ModItems.M4, new Vector3f(8.0f, 5.525f, -6.5f),
			ModItems.AKM, new Vector3f(8.0f, 6.025f, -1.0f),
			ModItems.GALIL, new Vector3f(8.0f, 6.025f, -2.0f),
			ModItems.AWP, new Vector3f(8.0f, 5.925f, -6.5f));
	private static final float FADE_LENGTH = 10.0f;
	private static final float CORE_ALPHA = 0.55f;
	private static final float GLOW_ALPHA = 0.12f;
	private static final float GLOW_WIDTH = 3.5f;
	/** Half-width at the camera and how much it grows per block away, so the beam stays a thin line on screen. */
	private static final float BASE_WIDTH = 0.0012f;
	private static final float WIDTH_PER_BLOCK = 0.0012f;
	private static final long FIRST_PERSON_STALE_MILLIS = 100;

	/** Our lens, relative to the camera in world axes, as of the last first-person frame. */
	private static Vector3f firstPersonLens;
	private static long firstPersonMillis;

	private LaserBeams() {
	}

	/**
	 * Notes where our gun's laser lens is this frame, from the first-person pass.
	 *
	 * @param itemPose the held item's pose (camera-relative, world axes), before its display transform
	 * @param view     the pose first-person rendering starts from
	 */
	public static void recordFirstPerson(Matrix4f itemPose, PoseStack.Pose view, ItemStack stack, boolean mirrored) {
		Vector3f lens = LENSES.get(stack.getItem());
		GunDisplay display = GunDisplay.of(stack.getItem());
		if (lens == null || display == null || laserColour(stack) == null) {
			return;
		}
		Vector3f inCamera = GunHands.toView(view, new Matrix4f(itemPose).mul(display.matrix(mirrored)), new Vector3f(lens).div(16.0f),
				false);
		// Same spot on screen, but under the world's field of view instead of the first-person pass's.
		Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
		float rescale = (float) (Math.tan(Math.toRadians(camera.getFov() / 2.0)) / Math.tan(Math.toRadians(Camera.BASE_HUD_FOV / 2.0)));
		inCamera.mul(rescale, rescale, 1.0f);
		firstPersonLens = new Matrix4f(view.pose()).transformPosition(inCamera);
		firstPersonMillis = Util.getMillis();
	}

	/** Red or green (RGB 0-1) for the laser on this gun, or null if it has none. */
	private static float[] laserColour(ItemStack gun) {
		AttachmentItem laser = Attachments.get(gun, AttachmentSlot.UNDERBARREL);
		if (laser == ModItems.LASER_SIGHT) {
			return new float[] {1.0f, 0.12f, 0.08f};
		} else if (laser == ModItems.GREEN_LASER_SIGHT) {
			return new float[] {0.2f, 1.0f, 0.3f};
		}
		return null;
	}

	public static void submit(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return;
		}
		Vec3 camera = context.levelState().cameraRenderState.pos;
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		boolean firstPerson = minecraft.options.getCameraType().isFirstPerson();
		for (Player player : minecraft.level.players()) {
			ItemStack held = player.getMainHandItem();
			float[] colour = laserColour(held);
			if (colour == null || player.isSpectator() || player.isInvisible()) {
				continue;
			}
			Vec3 dot = LaserSights.target(player, LaserSights.range(held), partialTick);
			Vector3f start;
			if (player == minecraft.player && firstPerson) {
				if (firstPersonLens == null || Util.getMillis() - firstPersonMillis > FIRST_PERSON_STALE_MILLIS) {
					continue;
				}
				start = new Vector3f(firstPersonLens);
			} else {
				start = toCamera(approximateLens(player, partialTick), camera);
			}
			Vector3f end = dot == null
					? new Vector3f(start).add(player.getViewVector(partialTick).toVector3f().mul(FADE_LENGTH))
					: toCamera(dot, camera);
			context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(),
					(pose, buffer) -> beam(pose, buffer, start, end, colour));
		}
	}

	/** Roughly where a laser under a gun held in another player's hand (or ours, in third person) is. */
	private static Vec3 approximateLens(Player player, float partialTick) {
		Vec3 look = player.getViewVector(partialTick);
		Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
		double side = player.getMainArm() == HumanoidArm.RIGHT ? 1.0 : -1.0;
		return player.getEyePosition(partialTick).add(look.scale(0.6)).add(right.scale(0.3 * side)).add(0.0, -0.35, 0.0);
	}

	private static Vector3f toCamera(Vec3 point, Vec3 camera) {
		return new Vector3f((float) (point.x - camera.x), (float) (point.y - camera.y), (float) (point.z - camera.z));
	}

	/** A soft wide glow with a bright thin core, from the lens to the dot (or until it fades out). */
	private static void beam(PoseStack.Pose pose, VertexConsumer buffer, Vector3f start, Vector3f end, float[] colour) {
		Vector3f direction = new Vector3f(end).sub(start);
		float length = direction.length();
		if (length < 0.05f) {
			return;
		}
		float drawn = Math.min(length, FADE_LENGTH);
		Vector3f tip = new Vector3f(direction).mul(drawn / length).add(start);
		float tipFade = length > FADE_LENGTH ? 0.0f : 1.0f - length / FADE_LENGTH;
		ribbon(pose, buffer, start, tip, GLOW_WIDTH, GLOW_ALPHA, GLOW_ALPHA * tipFade, colour);
		ribbon(pose, buffer, start, tip, 1.0f, CORE_ALPHA, CORE_ALPHA * tipFade, colour);
	}

	/** A flat strip from a to b, turned to face the camera (at the origin). */
	private static void ribbon(PoseStack.Pose pose, VertexConsumer buffer, Vector3f a, Vector3f b, float widthScale, float alphaA,
			float alphaB, float[] colour) {
		Vector3f direction = new Vector3f(b).sub(a);
		Vector3f sideA = new Vector3f(direction).cross(a).normalize().mul((BASE_WIDTH + WIDTH_PER_BLOCK * a.length()) * widthScale);
		Vector3f sideB = new Vector3f(direction).cross(b).normalize().mul((BASE_WIDTH + WIDTH_PER_BLOCK * b.length()) * widthScale);
		Vector3f[] corners = {new Vector3f(a).sub(sideA), new Vector3f(a).add(sideA), new Vector3f(b).add(sideB), new Vector3f(b).sub(sideB)};
		float[] alphas = {alphaA, alphaA, alphaB, alphaB};
		// Wind it counter-clockwise as seen from the camera, so it isn't culled.
		Vector3f normal = new Vector3f(corners[1]).sub(corners[0]).cross(new Vector3f(corners[2]).sub(corners[0]));
		boolean facing = normal.dot(new Vector3f(corners[0]).negate()) > 0.0f;
		for (int i = 0; i < 4; i++) {
			int k = facing ? i : 3 - i;
			buffer.addVertex(pose, corners[k].x, corners[k].y, corners[k].z).setColor(colour[0], colour[1], colour[2], alphas[k]);
		}
	}
}
