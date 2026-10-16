package com.jheinz.mymod.client.trombone.mixin;

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

import com.jheinz.mymod.client.GunHands;
import com.jheinz.mymod.client.trombone.TromboneHands;
import com.jheinz.mymod.client.trombone.TromboneSlide;
import com.jheinz.mymod.trombone.TromboneItem;

/**
 * Draws the player's own arms holding the Trombone in first person, as the drills and guns do theirs: the off hand round
 * the braces by the face, the main hand on the slide's grip bar, out and in with the slide. The instrument sits lowered
 * until the player blows, then comes up to the lips ({@link TromboneHands#applyRaise}); the hands ride on it.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonTromboneArmsMixin {
	@Shadow
	private void renderPlayerHand(PoseStack poseStack, SubmitNodeCollector collector, int light, HumanoidArm arm,
			PlayerRenderState playerState) {
	}

	@Unique
	private PoseStack.Pose mymod$tromboneViewPose;

	@Inject(method = "submitHandsWithItems", at = @At("HEAD"))
	private void mymod$rememberTromboneViewPose(float frameTime, PoseStack poseStack, SubmitNodeCollector collector,
			PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
		mymod$tromboneViewPose = poseStack.last().copy();
	}

	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
	private void mymod$drawTromboneArms(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float frameTime,
			float attackSwing, InteractionHand hand, float swingProgress, ItemStack stack, float equipProgress,
			PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		Player player = Minecraft.getInstance().player;
		if (hand != InteractionHand.MAIN_HAND || !(stack.getItem() instanceof TromboneItem) || player == null) {
			return;
		}
		HumanoidArm mainArm = playerState.avatarRenderState.mainArm;
		boolean mirrored = mainArm == HumanoidArm.LEFT;
		GunHands.applyIdleSway(poseStack, 0.7f);
		TromboneHands.applyRaise(poseStack, TromboneSlide.raise(player));
		if (!Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
			return;
		}
		Matrix4f itemPose = new Matrix4f(poseStack.last().pose());
		Vector3f slide = GunHands.toView(mymod$tromboneViewPose, itemPose, TromboneHands.firstPersonSlide(TromboneSlide.frame(player)), mirrored);
		Vector3f grip = GunHands.toView(mymod$tromboneViewPose, itemPose, TromboneHands.firstPersonGrip(), mirrored);

		poseStack.pushPose();
		GunHands.poseArm(poseStack, mymod$tromboneViewPose, mainArm, true, slide, mirrored);
		renderPlayerHand(poseStack, collector, light, mainArm, playerState);
		poseStack.popPose();

		// Something in the off hand: it holds that instead.
		if (!state.offHandItem.isEmpty()) {
			return;
		}
		poseStack.pushPose();
		GunHands.poseArm(poseStack, mymod$tromboneViewPose, mainArm.getOpposite(), false, grip, mirrored);
		renderPlayerHand(poseStack, collector, light, mainArm.getOpposite(), playerState);
		poseStack.popPose();
	}
}
