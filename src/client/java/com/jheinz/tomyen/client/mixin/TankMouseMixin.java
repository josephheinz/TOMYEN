package com.jheinz.tomyen.client.mixin;

import net.minecraft.client.MouseHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.jheinz.tomyen.client.tank.TankClient;

/** Slows the mouse in a tank's magnified sight (so aiming at 8x isn't twitchy), and stills it while climbing in or out. */
@Mixin(MouseHandler.class)
public abstract class TankMouseMixin {
	@ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 0)
	private double tomyen$tankYaw(double xo) {
		return xo * TankClient.mouseScale();
	}

	@ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 1)
	private double tomyen$tankPitch(double yo) {
		return yo * TankClient.mouseScale();
	}
}
