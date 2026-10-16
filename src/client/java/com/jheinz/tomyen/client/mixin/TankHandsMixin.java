package com.jheinz.tomyen.client.mixin;

import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.tomyen.client.tank.TankClient;

/** No hands or held item in front of a tank gunner's sight. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class TankHandsMixin {
	@Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void tomyen$noHandsInTank(CallbackInfo ci) {
		if (TankClient.isCrewSightView()) {
			ci.cancel();
		}
	}
}
