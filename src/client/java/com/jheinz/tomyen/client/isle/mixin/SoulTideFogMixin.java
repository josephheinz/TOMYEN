package com.jheinz.tomyen.client.isle.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;

import com.jheinz.tomyen.client.isle.shade.SoulTideClient;

/** The Soul Tide thickens the Isle's fog: it closes in as the tide rises (see SoulTideClient). */
@Mixin(AtmosphericFogEnvironment.class)
public abstract class SoulTideFogMixin {
	@Inject(method = "setupFog", at = @At("TAIL"))
	private void tomyen$soulTideFog(FogData fog, Camera camera, ClientLevel level, float renderDistance, DeltaTracker deltaTracker, CallbackInfo ci) {
		SoulTideClient.thickenFog(fog, level);
	}
}
