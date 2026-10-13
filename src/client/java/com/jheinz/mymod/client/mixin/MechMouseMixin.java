package com.jheinz.mymod.client.mixin;

import net.minecraft.client.MouseHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.jheinz.mymod.client.mech.MechClient;

/** Stills the mouse while a Bulwark Walker's pilot climbs in or out (the climb has the view). */
@Mixin(MouseHandler.class)
public abstract class MechMouseMixin {
	@ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 0)
	private double mymod$mechYaw(double xo) {
		return xo * MechClient.mouseScale();
	}

	@ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 1)
	private double mymod$mechPitch(double yo) {
		return yo * MechClient.mouseScale();
	}
}
