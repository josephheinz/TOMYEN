package com.jheinz.mymod.client.mixin;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.mymod.GunItem;
import com.jheinz.mymod.client.GunAim;
import com.jheinz.mymod.client.GunAnimation;
import com.jheinz.mymod.client.GunHands;
import com.jheinz.mymod.client.GunScope;
import com.jheinz.mymod.client.LaserBeams;
import com.jheinz.mymod.client.launcher.LauncherAnimation;
import com.jheinz.mymod.client.shotgun.ShotgunAnimation;

/**
 * Draws the player's own arms, with their skin and sleeves, holding a gun in first person: main hand on
 * the grip, off hand on the handguard (or cupping a pistol grip). With an item in the off hand, only the
 * main hand holds the gun and the off hand shows its item as usual. The hands ride on the gun's pose (equip,
 * swing, bob, plus a little idle sway) while the shoulders stay fixed on screen. A rocket launcher's hoisting and
 * reloading (LauncherAnimation) move it too, and a rocket being loaded is drawn in the off hand.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonGunArmsMixin {
	@Shadow
	private void renderPlayerHand(PoseStack poseStack, SubmitNodeCollector collector, int light, HumanoidArm arm,
			PlayerRenderState playerState) {
	}

	/** The pose first-person rendering starts from: view space, turning with the camera. */
	private PoseStack.Pose mymod$viewPose;

	/** Looking through a scope, like the spyglass: only the scope view, no gun or hands in the way. */
	@Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void mymod$hideWhileScoped(float frameTime, PoseStack poseStack, SubmitNodeCollector collector,
			PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
		if (Minecraft.getInstance().player != null && GunScope.throughScope(Minecraft.getInstance().player)) {
			ci.cancel();
		}
	}

	@Inject(method = "submitHandsWithItems", at = @At("HEAD"))
	private void mymod$rememberViewPose(float frameTime, PoseStack poseStack, SubmitNodeCollector collector,
			PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
		mymod$viewPose = poseStack.last().copy();
	}

	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
	private void mymod$drawGunArms(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float frameTime,
			float attackSwing, InteractionHand hand, float swingProgress, ItemStack stack, float equipProgress,
			PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		if (hand != InteractionHand.MAIN_HAND || !(stack.getItem() instanceof GunItem)) {
			return;
		}
		GunHands.Anchors anchors = GunHands.anchors(stack.getItem());
		// Sway and recoil move the gun itself; the hands follow it while the shoulders stay put.
		HumanoidArm mainArm = playerState.avatarRenderState.mainArm;
		boolean mirrored = mainArm == HumanoidArm.LEFT;
		// Aiming down a red dot brings the gun up to the eye; sway and recoil still move it from there.
		float aim = GunAim.update(Minecraft.getInstance().player);
		GunAim.apply(poseStack, mymod$viewPose, stack, mirrored, aim);
		GunHands.applyIdleSway(poseStack, 1.0f - 0.85f * aim);
		// A shouldered gun kicks less, so the sight stays usable between shots.
		GunHands.applyRecoil(poseStack, anchors, (1.0f - 0.8f * aim) * LauncherAnimation.recoilScale(stack));
		GunAnimation.applyGunPose(poseStack, stack, anchors, mirrored);
		LauncherAnimation.applyFirstPerson(poseStack, stack, anchors, mirrored);
		LaserBeams.recordFirstPerson(poseStack.last().pose(), mymod$viewPose, stack, mirrored);
		if (anchors == null || !Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
			return;
		}

		Matrix4f itemPose = new Matrix4f(poseStack.last().pose());
		// Racking, reloading, loading and the like pull a hand off its hold to work the gun, along a keyframed path.
		Vector3f gripTarget = anchors.grip();
		Vector3f supportTarget = anchors.support();
		float gripTwist = 0.0f;
		float supportTwist = 0.0f;
		GunAnimation.HandOverride working = GunAnimation.hand(stack);
		if (working == null) {
			// A launcher's off hand fetches and loads a rocket instead.
			working = LauncherAnimation.hand(stack, anchors);
		}
		if (working != null) {
			if (working.mainHand()) {
				gripTarget = new Vector3f(gripTarget).lerp(working.target(), working.blend());
				gripTwist = working.twist();
			} else {
				supportTarget = new Vector3f(supportTarget).lerp(working.target(), working.blend());
				supportTwist = working.twist();
			}
		}
		// Eased toward their targets, so a hand let go mid-stroke (fired, switched away) glides back rather than jumps.
		GunHands.Hand gripHand = GunHands.settle(true, stack.getItem(), anchors.grip(), gripTarget, gripTwist);
		GunHands.Hand supportHand = GunHands.settle(false, stack.getItem(), anchors.support(), supportTarget, supportTwist);
		Vector3f grip = GunHands.toView(mymod$viewPose, itemPose, gripHand.point(), mirrored);
		Vector3f gripHold = GunHands.toView(mymod$viewPose, itemPose, anchors.grip(), mirrored);
		Vector3f support = GunHands.toView(mymod$viewPose, itemPose, supportHand.point(), mirrored);
		Vector3f supportHold = GunHands.toView(mymod$viewPose, itemPose, anchors.support(), mirrored);

		poseStack.pushPose();
		GunHands.poseArm(poseStack, mymod$viewPose, mainArm, true, grip, gripHold, gripHand.twist(), mirrored);
		renderPlayerHand(poseStack, collector, light, mainArm, playerState);
		poseStack.popPose();

		// Something in the off hand: it holds that instead (drawn by vanilla where an off-hand item always is),
		// and the gun is held one-handed.
		if (!state.offHandItem.isEmpty()) {
			return;
		}
		LauncherAnimation.submitRocket(poseStack, collector, light, stack, anchors, mirrored, Minecraft.getInstance().player);
		ShotgunAnimation.submitShells(poseStack, collector, light, stack, mirrored, Minecraft.getInstance().player);
		poseStack.pushPose();
		GunHands.poseArm(poseStack, mymod$viewPose, mainArm.getOpposite(), false, support, supportHold, supportHand.twist(), mirrored);
		renderPlayerHand(poseStack, collector, light, mainArm.getOpposite(), playerState);
		poseStack.popPose();
	}
}
