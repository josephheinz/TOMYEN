package com.jheinz.tomyen.client.crow;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import com.jheinz.tomyen.crow.Crows;

/** The crow's renderer and model layer. (The Crow Nest is a plain block model; nothing to register for it.) */
public final class CrowClient {
	private CrowClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(Crows.CROW, CrowRenderer::new);
		ModelLayerRegistry.registerModelLayer(CrowModel.LAYER, CrowModel::createBodyLayer);
	}
}
