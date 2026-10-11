package com.jheinz.mymod.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.mymod.GunItem;

/**
 * Semi-auto and burst fire once per click. Vanilla re-runs startUseItem every 4 ticks while use is
 * held, so drop those repeats: a use only goes through if the key wasn't already down last tick.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftSemiAutoMixin {
	@Shadow
	public LocalPlayer player;

	@Shadow
	@Final
	public Options options;

	private boolean mymod$useHeldLastTick;

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void mymod$blockHeldSemiAuto(CallbackInfo ci) {
		if (mymod$useHeldLastTick && player != null
				&& player.getMainHandItem().getItem() instanceof GunItem gun
				&& gun.fireMode(player.getMainHandItem()).needsFreshClick()) {
			ci.cancel();
		}
	}

	@Inject(method = "handleKeybinds", at = @At("TAIL"))
	private void mymod$rememberUseKey(CallbackInfo ci) {
		mymod$useHeldLastTick = options.keyUse.isDown();
	}
}
