package com.jheinz.tomyen.client.mixin;

import net.minecraft.client.renderer.LevelRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.tomyen.client.schnozzle.WhipHandles;

/** Whip handle tips are only taken while the level's entities are submitted, in the level's camera-relative space. */
@Mixin(LevelRenderer.class)
public abstract class WhipLevelEntitiesMixin {
	@Inject(method = "submitEntities", at = @At("HEAD"))
	private void tomyen$beginWhipHandles(CallbackInfo ci) {
		WhipHandles.beginEntities();
	}

	@Inject(method = "submitEntities", at = @At("RETURN"))
	private void tomyen$endWhipHandles(CallbackInfo ci) {
		WhipHandles.endEntities();
	}
}
