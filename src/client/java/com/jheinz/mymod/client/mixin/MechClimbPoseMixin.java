package com.jheinz.mymod.client.mixin;

import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.mymod.mech.Mech;

/** Aboard a Bulwark Walker nobody sits: its pilot climbs the ladder standing, and whatever its fist holds hangs upright. */
@Mixin(HumanoidMobRenderer.class)
public abstract class MechClimbPoseMixin {
	@Inject(method = "extractHumanoidRenderState", at = @At("TAIL"))
	private static void mymod$standOnMech(LivingEntity entity, HumanoidRenderState state, float partialTicks, ItemModelResolver itemModelResolver,
			CallbackInfo ci) {
		if (entity.getVehicle() instanceof Mech) {
			state.isPassenger = false;
		}
	}
}
