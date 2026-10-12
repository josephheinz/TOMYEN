package com.jheinz.mymod.client.schnozzle;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import net.minecraft.client.renderer.entity.ThrownItemRenderer;

import com.jheinz.mymod.schnozzle.Schnozzles;

/** The Schnozzle's renderer and model, and its snot rockets and thrown boogers, both drawn as the booger item. */
public final class SchnozzleClient {
	private SchnozzleClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(Schnozzles.SCHNOZZLE, SchnozzleRenderer::new);
		ModelLayerRegistry.registerModelLayer(SchnozzleModel.LAYER, SchnozzleModel::createBodyLayer);
		EntityRendererRegistry.register(Schnozzles.SNOT_ROCKET, context -> new ThrownItemRenderer<>(context, 2.0f, false));
		EntityRendererRegistry.register(Schnozzles.THROWN_BOOGER, ThrownItemRenderer::new);
	}
}
