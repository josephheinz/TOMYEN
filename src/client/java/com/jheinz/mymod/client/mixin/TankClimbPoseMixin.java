package com.jheinz.mymod.client.mixin;

import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.mymod.tank.Tank;

/** Climbing in or out of a tank, the crew stands (and a collector stands in the hatch) rather than sitting. */
@Mixin(HumanoidMobRenderer.class)
public abstract class TankClimbPoseMixin {
	@Inject(method = "extractHumanoidRenderState", at = @At("TAIL"))
	private static void mymod$standOnTank(LivingEntity entity, HumanoidRenderState state, float partialTicks, ItemModelResolver itemModelResolver,
			CallbackInfo ci) {
		if (entity.getVehicle() instanceof Tank) {
			state.isPassenger = false;
		}
	}
}
