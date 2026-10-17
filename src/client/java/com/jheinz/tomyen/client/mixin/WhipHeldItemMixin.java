package com.jheinz.tomyen.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.tomyen.client.schnozzle.WhipHandles;
import com.jheinz.tomyen.client.schnozzle.WhipMotion;
import com.jheinz.tomyen.client.schnozzle.WhipPoses;
import com.jheinz.tomyen.schnozzle.NoseHairWhipItem;

/**
 * A Nose Hair Whip held in third person (by anyone: players, mobs, armor stands): turned in the hand with the crack's
 * wrist flick, and its pose handed to WhipHandles as it is drawn, so the lash starts at the handle's tip.
 */
@Mixin(ItemInHandLayer.class)
public abstract class WhipHeldItemMixin {
	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
	private void tomyen$holdWhip(ArmedEntityRenderState state, ItemStackRenderState item, ItemStack stack, HumanoidArm arm,
			PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		if (!(stack.getItem() instanceof NoseHairWhipItem whip)) {
			return;
		}
		WhipMotion motion = WhipMotion.of(state, arm);
		WhipPoses.applyWrist(poseStack, motion);
		int id = state instanceof AvatarRenderState avatar ? avatar.id : -1;
		WhipHandles.capture(poseStack.last(), item, id, arm == state.mainArm, motion, state.ageInTicks, whip);
	}
}
