package com.jheinz.tomyen.client.fishing.mixin;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.tomyen.client.fishing.FishingClient;

/**
 * While a fish is fighting, the use key reels (FishingClient reads it) and does nothing else: holding it mustn't
 * open the chest you happen to be looking at, place a block from the off hand, or reel the bobber in.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftFishingMixin {
	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void tomyen$reelInstead(CallbackInfo ci) {
		if (FishingClient.reeling()) {
			ci.cancel();
		}
	}
}
