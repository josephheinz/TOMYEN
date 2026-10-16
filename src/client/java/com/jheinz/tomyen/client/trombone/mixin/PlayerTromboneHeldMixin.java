package com.jheinz.tomyen.client.trombone.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.tomyen.trombone.TromboneItem;

/**
 * In third person a Trombone in the main hand is drawn from the head, mouthpiece at the lips, not from the hand: it
 * follows the player's look (which works the slide), and both arms are free to hold it where they belong (see
 * HumanoidTrombonePoseMixin). The space it's drawn in is the head's turned to item axes (x right, y up, -z ahead,
 * origin at the head's centre); its third-person display transform (tools/gen_trombone.py) puts the mouthpiece at the
 * mouth.
 */
@Mixin(PlayerItemInHandLayer.class)
public abstract class PlayerTromboneHeldMixin {
	@Inject(method = "submitArmWithItem(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
			at = @At("HEAD"), cancellable = true)
	private void tomyen$holdTromboneToLips(AvatarRenderState state, ItemStackRenderState item, ItemStack itemStack, HumanoidArm arm,
			PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		if (item.isEmpty() || arm != state.mainArm || !(itemStack.getItem() instanceof TromboneItem)) {
			return;
		}
		EntityModel<?> model = (EntityModel<?>) ((RenderLayer<?, ?>) (Object) this).getParentModel();
		poseStack.pushPose();
		model.root().translateAndRotate(poseStack);
		((HeadedModel) model).getHead().translateAndRotate(poseStack);
		poseStack.translate(0.0f, -0.25f, 0.0f);
		poseStack.rotateDegrees(Axis.ZP, 180.0f);
		item.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
		ci.cancel();
	}
}
