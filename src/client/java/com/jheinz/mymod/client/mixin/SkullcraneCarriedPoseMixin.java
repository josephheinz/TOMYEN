package com.jheinz.mymod.client.mixin;

import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.mymod.skullcrane.SkullcraneGrip;

/** Carried off by skullcranes, a player (or any humanoid) dangles and kicks rather than sitting. */
@Mixin(HumanoidMobRenderer.class)
public abstract class SkullcraneCarriedPoseMixin {
	@Inject(method = "extractHumanoidRenderState", at = @At("TAIL"))
	private static void mymod$dangle(LivingEntity entity, HumanoidRenderState state, float partialTicks, ItemModelResolver itemModelResolver,
			CallbackInfo ci) {
		if (entity.getVehicle() instanceof SkullcraneGrip) {
			state.isPassenger = false;
			state.walkAnimationPos = state.ageInTicks * 0.9f;
			state.walkAnimationSpeed = 0.7f;
		}
	}
}
