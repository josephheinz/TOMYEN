package com.jheinz.mymod.client.mixin;

import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.mymod.client.mech.MechClient;

/** No hands or held item in front of a Bulwark Walker's viewport. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class MechHandsMixin {
	@Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void mymod$noHandsInMech(CallbackInfo ci) {
		if (MechClient.cockpitMech() != null) {
			ci.cancel();
		}
	}
}
