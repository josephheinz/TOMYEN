package com.jheinz.tomyen.client.gravedigger;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import com.jheinz.tomyen.gravedigger.GraveDiggers;

/** The grave digger's renderer and model. */
public final class GraveDiggerClient {
	private GraveDiggerClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(GraveDiggers.GRAVE_DIGGER, GraveDiggerRenderer::new);
		ModelLayerRegistry.registerModelLayer(GraveDiggerModel.LAYER, GraveDiggerModel::createBodyLayer);
	}
}
