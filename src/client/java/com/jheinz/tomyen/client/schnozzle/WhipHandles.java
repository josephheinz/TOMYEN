package com.jheinz.tomyen.client.schnozzle;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.client.mixin.ItemLayerRenderStateInvoker;
import com.jheinz.tomyen.client.mixin.ItemStackRenderStateAccessor;
import com.jheinz.tomyen.schnozzle.NoseHairWhipItem;

/**
 * Where the lash leaves the whip: the tip of the handle in the held sprite, carried through exactly the transforms the
 * held item is drawn with this frame, so the lash is fixed to the handle.
 *
 * <p>Third person (other players, mobs, armor stands, and yourself in F5) the item-in-hand layer hands over its pose
 * stack as the item is drawn ({@code WhipHeldItemMixin}); that is in the same camera-relative space as the level, and the
 * capture is made while the level's entities are submitted, just before {@link WhipLashes#submit} runs.
 *
 * <p>First person the held item is drawn after the level, in its own pass with its own field of view, so its pose isn't
 * known yet when the lash is submitted. {@link #firstPerson} builds it: the same view lag, held position and whip motion
 * ({@link WhipPoses#applyFirstPerson}, which {@code WhipFirstPersonMixin} draws the item with) and the item's own display
 * transform, all from this frame's extracted render state. The point is then carried from the hand pass to the level:
 * the hand pass draws {@code hudProjection * bob * p} and the level {@code projection * bob * view * w}, so scaling the
 * bobbed point across the screen by the ratio of the two fields of view and undoing the bob and the view turn gives the
 * level point that lands on the same pixel, at the same depth.
 */
public final class WhipHandles {
	/**
	 * The handle tip in the in-hand sprite's model space (pixels / 16, y up): the top end of the booger binding, where the
	 * hair is bound on. The handle runs up and to the right, from (1, 14) to (5, 10) in the texture.
	 */
	private static final Vector3f TIP = new Vector3f(6.85f / 16.0f, 6.35f / 16.0f, 0.5f);
	private static final Vector3f ALONG = new Vector3f(1.0f, 1.0f, 0.0f).normalize();

	/**
	 * A handle tip, camera-relative.
	 *
	 * @param entityId the holder's id, if it's a player or mannequin, else -1
	 * @param mainHand whether it's the whip in the main hand
	 * @param ageInTicks the holder's age this frame, which the lash's sway runs on
	 * @param whip the whip held, for its lash's reach and texture
	 */
	public record Handle(int entityId, boolean mainHand, Vec3 tip, Vec3 along, WhipMotion motion, float ageInTicks, NoseHairWhipItem whip) {
	}

	private static final List<Handle> CAPTURED = new ArrayList<>();
	private static boolean capturing;
	/** The view bob the level is drawn with this frame (GameRenderer.renderLevel), which the hand pass shares. */
	private static final Matrix4f BOB = new Matrix4f();

	private WhipHandles() {
	}

	/** The level's entities are about to be submitted: forget last frame's handles, and take this frame's. */
	public static void beginEntities() {
		CAPTURED.clear();
		capturing = true;
	}

	public static void endEntities() {
		capturing = false;
	}

	public static List<Handle> captured() {
		return CAPTURED;
	}

	public static void setBob(Matrix4fc bob) {
		BOB.set(bob);
	}

	/**
	 * Called by the item-in-hand layer with its pose stack just before it draws a whip, in the hold frame the item's
	 * display transform starts from. Only while the level's entities are submitted: GUI previews of an entity aren't in
	 * the level's space.
	 */
	public static void capture(PoseStack.Pose holdPose, ItemStackRenderState item, int entityId, boolean mainHand, WhipMotion motion,
			float ageInTicks, NoseHairWhipItem whip) {
		if (!capturing || item.isEmpty()) {
			return;
		}
		PoseStack.Pose pose = holdPose.copy();
		applyItemTransform(item, pose);
		Vector3f tip = pose.pose().transformPosition(TIP, new Vector3f());
		Vector3f along = pose.pose().transformDirection(ALONG, new Vector3f());
		if (along.lengthSquared() < 1.0e-8f) {
			return;
		}
		CAPTURED.add(new Handle(entityId, mainHand, new Vec3(tip), new Vec3(along.normalize()), motion, ageInTicks, whip));
	}

	/** The item's own display transform, as its first layer applies it when drawn (ItemStackRenderState.LayerRenderState.submit). */
	private static void applyItemTransform(ItemStackRenderState item, PoseStack.Pose pose) {
		((ItemLayerRenderStateInvoker) ((ItemStackRenderStateAccessor) item).tomyen$layers()[0]).tomyen$applyTransform(pose);
	}

	/**
	 * The handle tip of a whip held in first person, camera-relative, or null if that hand doesn't show a whip.
	 *
	 * @param avatar the local player's render state this frame
	 */
	public static @Nullable Handle firstPerson(CameraRenderState camera, AvatarRenderState avatar, FirstPersonHandsAndItemsRenderState hands,
			InteractionHand hand) {
		boolean main = hand == InteractionHand.MAIN_HAND;
		ItemStack shown = main ? hands.mainHandItem : hands.offHandItem;
		ItemStackRenderState item = main ? hands.mainHandRenderState : hands.offHandRenderState;
		if (!(shown.getItem() instanceof NoseHairWhipItem whip) || item.isEmpty() || hands.handRenderSelection == null) {
			return null;
		}
		HumanoidArm arm = main ? avatar.mainArm : avatar.mainArm.getOpposite();
		float partialTicks = camera.cameraEntityPartialTicks;
		float inverseArmHeight = main
				? hands.mainHandSwapScale * (1.0f - Mth.lerp(partialTicks, hands.oldMainHandHeight, hands.mainHandHeight))
				: hands.offHandSwapScale * (1.0f - Mth.lerp(partialTicks, hands.oldOffHandHeight, hands.offHandHeight));
		WhipMotion motion = WhipMotion.of(avatar, arm);

		// The hand pass, from just inside its view turn and bob (FirstPersonHandsAndItemsRenderer.submitHandsWithItems).
		PoseStack poseStack = new PoseStack();
		poseStack.rotateDegrees(Axis.XP, (hands.viewXRot - hands.xBob) * 0.1f);
		poseStack.rotateDegrees(Axis.YP, (hands.viewYRot - hands.yBob) * 0.1f);
		WhipPoses.applyFirstPerson(poseStack, arm, inverseArmHeight, motion);
		applyItemTransform(item, poseStack.last());
		Matrix4f pose = poseStack.last().pose();
		Vector3f tip = toLevel(camera, pose.transformPosition(TIP, new Vector3f()));
		Vector3f ahead = toLevel(camera, pose.transformPosition(new Vector3f(ALONG).mul(0.1f).add(TIP), new Vector3f()));
		Vector3f along = ahead.sub(tip, new Vector3f());
		if (along.lengthSquared() < 1.0e-8f) {
			return null;
		}
		return new Handle(avatar.id, main, new Vec3(tip), new Vec3(along.normalize()), motion, avatar.ageInTicks, whip);
	}

	/** A point of the hand pass (before its bob), as the camera-relative level point drawn on the same pixel. */
	private static Vector3f toLevel(CameraRenderState camera, Vector3f handPoint) {
		Vector3f point = BOB.transformPosition(handPoint, new Vector3f());
		// Both passes share the aspect ratio; only the field of view differs (the hand's is a fixed 70 degrees).
		float handFocal = 1.0f / (float) Math.tan(camera.hudFov * Mth.DEG_TO_RAD * 0.5f);
		float levelFocal = camera.projectionMatrix.m11();
		if (levelFocal > 1.0e-4f) {
			float spread = handFocal / levelFocal;
			point.x *= spread;
			point.y *= spread;
		}
		new Matrix4f(BOB).invert().transformPosition(point);
		return new Matrix4f(camera.viewRotationMatrix).invert().transformPosition(point);
	}
}
