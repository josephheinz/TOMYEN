package com.jheinz.mymod.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.world.InteractionHand;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.mymod.GunItem;

/**
 * Using an item without swinging drops it to the bottom of the screen and raises it again. Firing a gun
 * is a use, so skip that: the recoil animation is the only motion a shot should cause.
 */
@Mixin(FirstPersonHandsAndItems.class)
public abstract class FirstPersonHandsAndItemsMixin {
	@Inject(method = "itemUsed", at = @At("HEAD"), cancellable = true)
	private void mymod$noDipOnFire(InteractionHand hand, CallbackInfo ci) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null && minecraft.player.getItemInHand(hand).getItem() instanceof GunItem) {
			ci.cancel();
		}
	}
}
