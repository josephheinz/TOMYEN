package com.jheinz.tomyen.client.mixin;

import net.minecraft.client.MouseHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.tomyen.client.neuralink.NeuralTrigger;
import com.jheinz.tomyen.client.tvhelmet.TvEmoteWheel;

/** While the Neuralink's module selector or the TV Helmet's emote wheel is open, the mouse steers its cursor and the view stays put. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerRadialMixin {
	@Shadow
	private double accumulatedDX;
	@Shadow
	private double accumulatedDY;

	@Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
	private void tomyen$steerSelector(double mousea, CallbackInfo ci) {
		if (NeuralTrigger.isSelecting()) {
			NeuralTrigger.moveCursor(accumulatedDX, accumulatedDY);
			ci.cancel();
		} else if (TvEmoteWheel.isSelecting()) {
			TvEmoteWheel.moveCursor(accumulatedDX, accumulatedDY);
			ci.cancel();
		}
	}
}
