package com.jheinz.mymod.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.mymod.client.mech.MechClient;

/** Shakes a Bulwark Walker pilot's view at every stomp, landing, shot and hit. */
@Mixin(GameRenderer.class)
public abstract class MechCameraShakeMixin {
	@Inject(method = "bobHurt", at = @At("TAIL"))
	private void mymod$mechShake(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
		float[] shake = MechClient.cameraShake();
		if (shake != null) {
			poseStack.rotateDegrees(Axis.ZP, shake[0]);
			poseStack.rotateDegrees(Axis.XP, shake[1]);
		}
	}
}
