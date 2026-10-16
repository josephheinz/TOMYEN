package com.jheinz.tomyen.client.mixin;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.tomyen.client.tank.TankClient;
import com.jheinz.tomyen.tank.Tank;

/**
 * The tank a player is looking out of through the gunner's sight isn't drawn for them: the camera is in its
 * turret. Vanilla always draws whatever the local player rides, culling or not, so the renderer's own
 * shouldRender isn't enough; this is where that exception is made.
 */
@Mixin(LevelExtractor.class)
public abstract class TankHiddenFromCrewMixin {
	@Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true)
	private void tomyen$hideOwnTank(Entity entity, Frustum frustum, double camX, double camY, double camZ, float partialTicks,
			long chunkFadeDuration, CallbackInfoReturnable<Boolean> cir) {
		Tank tank = TankClient.sightTank();
		if (tank != null && (entity == tank || entity.getVehicle() == tank)) {
			cir.setReturnValue(false);
		}
	}
}
