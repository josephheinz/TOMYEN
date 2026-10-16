package com.jheinz.tomyen.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.tomyen.client.skullcrane.MonocleHud;

/**
 * Zooming through an Optic Monocle, the local player counts as looking through a spyglass: vanilla narrows the
 * view, slows the mouse and draws the vignette.
 */
@Mixin(Player.class)
public abstract class PlayerMonocleZoomMixin {
	@Inject(method = "isScoping", at = @At("RETURN"), cancellable = true)
	private void tomyen$monocleZoom(CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && (Object) this == Minecraft.getInstance().player && MonocleHud.zooming()) {
			cir.setReturnValue(true);
		}
	}
}
