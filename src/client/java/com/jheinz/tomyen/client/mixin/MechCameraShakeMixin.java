package com.jheinz.tomyen.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.tomyen.client.mech.MechClient;

/**
 * The weight of a Bulwark Walker in the view: its pilot's shakes, footfall jolts and lean into the torso's turns, and
 * the jolt of its footfalls for anyone standing near (see {@link MechClient#applyCamera}).
 */
@Mixin(GameRenderer.class)
public abstract class MechCameraShakeMixin {
	@Inject(method = "bobHurt", at = @At("TAIL"))
	private void tomyen$mechShake(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
		MechClient.applyCamera(poseStack);
	}
}
