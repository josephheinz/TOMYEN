package com.jheinz.mymod.client.mixin;

import net.minecraft.client.player.AbstractClientPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.mymod.client.tank.TankClient;

/** Zooms the view to the tank gunner's sight magnification. */
@Mixin(AbstractClientPlayer.class)
public abstract class TankSightZoomMixin {
	@Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
	private void mymod$sightZoom(boolean firstPerson, float effectScale, CallbackInfoReturnable<Float> cir) {
		float zoom = TankClient.sightZoom((AbstractClientPlayer) (Object) this);
		if (firstPerson && zoom > 0.0f) {
			cir.setReturnValue(1.0f / zoom);
		}
	}
}
