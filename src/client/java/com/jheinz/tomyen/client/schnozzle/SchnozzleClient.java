package com.jheinz.tomyen.client.schnozzle;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import net.minecraft.client.renderer.entity.ThrownItemRenderer;

import com.jheinz.tomyen.ModNetworking;
import com.jheinz.tomyen.schnozzle.Schnozzles;

/**
 * The Schnozzle's renderer and model, and its snot rockets and thrown boogers, both drawn as the booger item; the
 * Nose Hair Whip's lashes and crack.
 */
public final class SchnozzleClient {
	private SchnozzleClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(Schnozzles.SCHNOZZLE, SchnozzleRenderer::new);
		ModelLayerRegistry.registerModelLayer(SchnozzleModel.LAYER, SchnozzleModel::createBodyLayer);
		EntityRendererRegistry.register(Schnozzles.SNOT_ROCKET, context -> new ThrownItemRenderer<>(context, 2.0f, false));
		EntityRendererRegistry.register(Schnozzles.THROWN_BOOGER, ThrownItemRenderer::new);
		ParticleProviderRegistry.getInstance().register(Schnozzles.WHIP_CRACK_PARTICLE, WhipCrackParticle.Provider::new);
		ClientPlayNetworking.registerGlobalReceiver(ModNetworking.WhipLash.TYPE, (payload, context) -> WhipLashes.receive(payload));
		LevelRenderEvents.COLLECT_SUBMITS.register(WhipLashes::submit);
	}
}
