package com.jheinz.tomyen.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.tomyen.client.hogfire.HogfireClient;

/**
 * Flying a Hogfire with a gun mount, attack is the gun's trigger (HogfireClient sends it with the flight controls), so
 * it neither swings at nor mines whatever's under the crosshair.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftHogfireGunMixin {
	@Shadow
	public LocalPlayer player;

	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void tomyen$mountedGunTrigger(CallbackInfoReturnable<Boolean> cir) {
		if (HogfireClient.gunner(player)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void tomyen$noMiningWhileGunning(boolean attacking, CallbackInfo ci) {
		if (HogfireClient.gunner(player)) {
			ci.cancel();
		}
	}
}
