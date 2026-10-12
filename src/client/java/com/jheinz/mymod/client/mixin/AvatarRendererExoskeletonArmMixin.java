package com.jheinz.mymod.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.mymod.client.exoskeleton.ExoskeletonRenderer;

/** Draws a worn exoskeleton's arm frame, and a socketed chestplate's sleeve over it, on the player's own hand in first person. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererExoskeletonArmMixin {
	@Inject(method = "renderRightHand", at = @At("TAIL"))
	private void mymod$exoskeletonRightArm(PoseStack poseStack, SubmitNodeCollector collector, int light, Identifier skinTexture,
			boolean hasSleeve, CallbackInfo ci) {
		ExoskeletonRenderer.submitFirstPersonArm(poseStack, collector, light, HumanoidArm.RIGHT);
	}

	@Inject(method = "renderLeftHand", at = @At("TAIL"))
	private void mymod$exoskeletonLeftArm(PoseStack poseStack, SubmitNodeCollector collector, int light, Identifier skinTexture,
			boolean hasSleeve, CallbackInfo ci) {
		ExoskeletonRenderer.submitFirstPersonArm(poseStack, collector, light, HumanoidArm.LEFT);
	}
}
