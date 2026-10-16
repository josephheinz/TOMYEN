package com.jheinz.tomyen.client.trombone.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.tomyen.client.trombone.TromboneMoodState;
import com.jheinz.tomyen.client.trombone.TromboneMoodsClient;

/**
 * Shows the Trombone's spells on mobs: under the Sad Trombone a mob hangs its head, its whole body tipped forward and
 * sagging, swaying glumly; under the Lullaby it keels over onto its side, fast asleep, rising and falling as it breathes.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererTromboneMoodMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
			at = @At("TAIL"))
	private void tomyen$extractTromboneMoods(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		((TromboneMoodState) state).tomyen$setMoods(TromboneMoodsClient.droop(entity, partialTicks), TromboneMoodsClient.sleep(entity, partialTicks));
	}

	@Inject(method = "setupRotations", at = @At("TAIL"))
	private void tomyen$applyTromboneMoods(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float entityScale,
			CallbackInfo ci) {
		TromboneMoodState moods = (TromboneMoodState) state;
		float droop = moods.tomyen$droop();
		float sleep = moods.tomyen$sleep();
		if (droop > 0.0f) {
			float sway = Mth.sin(state.ageInTicks * 0.08f) * 4.0f;
			poseStack.translate(0.0f, -0.06f * droop, 0.0f);
			poseStack.rotateDegrees(Axis.XP, -22.0f * droop);
			poseStack.rotateDegrees(Axis.ZP, sway * droop);
		}
		if (sleep > 0.0f) {
			float breath = 1.0f + 0.03f * Mth.sin(state.ageInTicks * 0.15f) * sleep;
			poseStack.rotateDegrees(Axis.ZP, 82.0f * sleep);
			poseStack.scale(1.0f, breath, 1.0f);
		}
	}
}
