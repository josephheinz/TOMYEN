package com.jheinz.tomyen.client.mixin;

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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.tomyen.client.GunHands;
import com.jheinz.tomyen.client.drill.DrillHands;
import com.jheinz.tomyen.client.drill.DrillSpin;
import com.jheinz.tomyen.drill.DrillItem;

/**
 * Draws the player's own arms holding a drill in first person, as FirstPersonGunArmsMixin does a gun: main hand on the
 * grip, off hand on the side handle (or turning the Hand Drill's crank). With an item in the off hand only the main
 * hand holds the drill. The drill sways a little at rest and shudders while it runs (DrillHands); the hands ride on it
 * while the shoulders stay put.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonDrillArmsMixin {
	@Shadow
	private void renderPlayerHand(PoseStack poseStack, SubmitNodeCollector collector, int light, HumanoidArm arm,
			PlayerRenderState playerState) {
	}

	/** The pose first-person rendering starts from: view space, turning with the camera. */
	@Unique
	private PoseStack.Pose tomyen$drillViewPose;

	@Inject(method = "submitHandsWithItems", at = @At("HEAD"))
	private void tomyen$rememberDrillViewPose(float frameTime, PoseStack poseStack, SubmitNodeCollector collector,
			PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
		tomyen$drillViewPose = poseStack.last().copy();
	}

	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
	private void tomyen$drawDrillArms(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float frameTime,
			float attackSwing, InteractionHand hand, float swingProgress, ItemStack stack, float equipProgress,
			PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		Player player = Minecraft.getInstance().player;
		if (hand != InteractionHand.MAIN_HAND || !(stack.getItem() instanceof DrillItem) || player == null) {
			return;
		}
		HumanoidArm mainArm = playerState.avatarRenderState.mainArm;
		boolean mirrored = mainArm == HumanoidArm.LEFT;
		GunHands.applyIdleSway(poseStack, 1.0f);
		DrillHands.applyShake(poseStack, player, stack);
		if (!Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
			return;
		}

		GunHands.Anchors anchors = DrillHands.anchors(stack, DrillSpin.frame(player, stack));
		Matrix4f itemPose = new Matrix4f(poseStack.last().pose());
		Vector3f grip = GunHands.toView(tomyen$drillViewPose, itemPose, anchors.grip(), mirrored);
		Vector3f support = GunHands.toView(tomyen$drillViewPose, itemPose, anchors.support(), mirrored);

		poseStack.pushPose();
		GunHands.poseArm(poseStack, tomyen$drillViewPose, mainArm, true, grip, mirrored);
		renderPlayerHand(poseStack, collector, light, mainArm, playerState);
		poseStack.popPose();

		// Something in the off hand: it holds that instead, and the drill is held one-handed.
		if (!state.offHandItem.isEmpty()) {
			return;
		}
		poseStack.pushPose();
		GunHands.poseArm(poseStack, tomyen$drillViewPose, mainArm.getOpposite(), false, support, mirrored);
		renderPlayerHand(poseStack, collector, light, mainArm.getOpposite(), playerState);
		poseStack.popPose();
	}
}
