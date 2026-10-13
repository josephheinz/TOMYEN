package com.jheinz.mymod.client.cathode;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.cathode.CathodeWalkers;
import com.jheinz.mymod.cathode.StaticBurstPayload;

/** The Cathode Walker's renderer and model, and the snow its Static Burst throws over your screen. */
public final class CathodeWalkerClient {
	private CathodeWalkerClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(CathodeWalkers.CATHODE_WALKER, CathodeWalkerRenderer::new);
		ModelLayerRegistry.registerModelLayer(CathodeWalkerModel.LAYER, CathodeWalkerModel::createBodyLayer);
		ClientPlayNetworking.registerGlobalReceiver(StaticBurstPayload.TYPE, (payload, context) -> StaticOverlay.start(payload.ticks()));
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, MyMod.id("static_burst"), StaticOverlay::extract);
	}
}
