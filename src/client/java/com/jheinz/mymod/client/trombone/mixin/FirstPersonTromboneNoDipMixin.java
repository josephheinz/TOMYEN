package com.jheinz.mymod.client.trombone.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.world.InteractionHand;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.mymod.trombone.TromboneItem;

/**
 * Starting to use an item without a swing drops it to the bottom of the screen and raises it again. Blowing the
 * Trombone shouldn't: it lifts to the lips smoothly instead (TromboneHands.applyRaise).
 */
@Mixin(FirstPersonHandsAndItems.class)
public abstract class FirstPersonTromboneNoDipMixin {
	@Inject(method = "itemUsed", at = @At("HEAD"), cancellable = true)
	private void mymod$noTromboneDip(InteractionHand hand, CallbackInfo ci) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null && minecraft.player.getItemInHand(hand).getItem() instanceof TromboneItem) {
			ci.cancel();
		}
	}
}
