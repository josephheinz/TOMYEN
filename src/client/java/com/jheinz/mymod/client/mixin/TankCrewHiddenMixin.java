package com.jheinz.mymod.client.mixin;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.mymod.tank.Tank;

/** A tank's crew is out of sight inside the hull once they're in the seat (not while they climb in or out). */
@Mixin(EntityRenderDispatcher.class)
public abstract class TankCrewHiddenMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private <E extends Entity> void mymod$hideCrew(E entity, Frustum culler, double camX, double camY, double camZ, float partialTicks,
			CallbackInfoReturnable<Boolean> cir) {
		if (entity.getVehicle() instanceof Tank tank && entity == tank.seatedPlayer()) {
			cir.setReturnValue(false);
		}
	}
}
