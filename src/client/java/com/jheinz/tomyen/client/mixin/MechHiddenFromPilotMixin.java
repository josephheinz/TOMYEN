package com.jheinz.tomyen.client.mixin;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.tomyen.client.mech.MechClient;

/**
 * The Bulwark Walker a player pilots isn't drawn for them in the cockpit view: their eyes are in its chest. Vanilla
 * always draws whatever the local player rides, culling or not, so this is where that exception is made. Whatever
 * its fist holds is still drawn.
 */
@Mixin(LevelExtractor.class)
public abstract class MechHiddenFromPilotMixin {
	@Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true)
	private void tomyen$hideOwnMech(Entity entity, Frustum frustum, double camX, double camY, double camZ, float partialTicks,
			long chunkFadeDuration, CallbackInfoReturnable<Boolean> cir) {
		if (MechClient.hiddenInCockpit(entity)) {
			cir.setReturnValue(false);
		}
	}
}
