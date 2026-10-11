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
import com.jheinz.mymod.client.GunAnimation;
import com.jheinz.mymod.client.GunHands;

/**
 * Draws the player's own arms, with their skin and sleeves, holding a gun in first person: main hand on
 * the grip, off hand on the handguard (or cupping a pistol grip). The hands ride on the gun's pose (equip,
 * swing, bob, plus a little idle sway) while the shoulders stay fixed on screen.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonGunArmsMixin {
	@Shadow
	private void renderPlayerHand(PoseStack poseStack, SubmitNodeCollector collector, int light, HumanoidArm arm,
			PlayerRenderState playerState) {
	}

	/** The pose first-person rendering starts from: view space, turning with the camera. */
	private PoseStack.Pose mymod$viewPose;

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
		GunHands.applyIdleSway(poseStack);
		GunHands.applyRecoil(poseStack, anchors);
		GunAnimation.applyGunPose(poseStack, stack, anchors, mirrored);
		if (anchors == null || !state.offHandItem.isEmpty() || !Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
			return;
		}

		Matrix4f itemPose = new Matrix4f(poseStack.last().pose());
		// A racking or bolt animation pulls one hand off its hold to work the gun.
		Vector3f gripAnchor = anchors.grip();
		Vector3f supportAnchor = anchors.support();
		GunAnimation.HandOverride working = GunAnimation.hand(stack);
		if (working != null) {
			if (working.mainHand()) {
				gripAnchor = new Vector3f(gripAnchor).lerp(working.target(), working.blend());
			} else {
				supportAnchor = new Vector3f(supportAnchor).lerp(working.target(), working.blend());
			}
		}
		Vector3f grip = GunHands.toView(mymod$viewPose, itemPose, gripAnchor, mirrored);
		Vector3f support = GunHands.toView(mymod$viewPose, itemPose, supportAnchor, mirrored);

		poseStack.pushPose();
		GunHands.poseArm(poseStack, mymod$viewPose, mainArm, true, grip, mirrored);
		renderPlayerHand(poseStack, collector, light, mainArm, playerState);
		poseStack.popPose();

		poseStack.pushPose();
		GunHands.poseArm(poseStack, mymod$viewPose, mainArm.getOpposite(), false, support, mirrored);
		renderPlayerHand(poseStack, collector, light, mainArm.getOpposite(), playerState);
		poseStack.popPose();
	}
}
