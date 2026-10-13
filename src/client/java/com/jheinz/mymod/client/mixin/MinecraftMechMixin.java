package com.jheinz.mymod.client.mixin;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.mymod.client.mech.MechClient;

/**
 * Piloting a Bulwark Walker, attack and use are its arms' triggers (see {@link MechClient}): they never punch, mine,
 * use the held item or pick blocks.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMechMixin {
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void mymod$noAttackInMech(CallbackInfoReturnable<Boolean> cir) {
		if (MechClient.pilotedMech() != null) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void mymod$noMiningInMech(boolean down, CallbackInfo ci) {
		if (MechClient.pilotedMech() != null) {
			ci.cancel();
		}
	}

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void mymod$noUseInMech(CallbackInfo ci) {
		if (MechClient.pilotedMech() != null) {
			ci.cancel();
		}
	}

	@Inject(method = "pickBlockOrEntity", at = @At("HEAD"), cancellable = true)
	private void mymod$noPickInMech(CallbackInfo ci) {
		if (MechClient.pilotedMech() != null) {
			ci.cancel();
		}
	}
}
