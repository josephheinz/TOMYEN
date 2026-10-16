package com.jheinz.tomyen.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.tomyen.client.schnozzle.WhipMotion;
import com.jheinz.tomyen.client.schnozzle.WhipPoses;
import com.jheinz.tomyen.schnozzle.NoseHairWhipItem;

/**
 * The Nose Hair Whip in first person: held where any item is, but cracked with a wind-up over the shoulder and a snap
 * forward (and thrown for a snag) instead of vanilla's swing. WhipHandles builds the same pose to start the lash at the
 * handle's tip, so the two stay together.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class WhipFirstPersonMixin {
	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
	private void tomyen$whipHand(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float partialTicks, float xRot,
			InteractionHand hand, float attack, ItemStack stack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector collector,
			int light, CallbackInfo ci) {
		AvatarRenderState avatar = playerState.avatarRenderState;
		if (!(stack.getItem() instanceof NoseHairWhipItem) || state.isScoping || avatar == null) {
			return;
		}
		HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? avatar.mainArm : avatar.mainArm.getOpposite();
		poseStack.pushPose();
		WhipPoses.applyFirstPerson(poseStack, arm, inverseArmHeight, WhipMotion.of(avatar, arm));
		(hand == InteractionHand.MAIN_HAND ? state.mainHandRenderState : state.offHandRenderState)
				.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
		ci.cancel();
	}
}
