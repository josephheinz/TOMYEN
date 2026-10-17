package com.jheinz.tomyen.client.banshee;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import com.jheinz.tomyen.banshee.Banshees;

/** The Banshee's renderer and model. */
public final class BansheeClient {
	private BansheeClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(Banshees.BANSHEE, BansheeRenderer::new);
		ModelLayerRegistry.registerModelLayer(BansheeModel.LAYER, BansheeModel::createBodyLayer);
	}
}
