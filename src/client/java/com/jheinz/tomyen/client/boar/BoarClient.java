package com.jheinz.tomyen.client.boar;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import com.jheinz.tomyen.boar.Boars;

/** The wild boar's renderer and its adult and piglet model layers. */
public final class BoarClient {
	private BoarClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(Boars.BOAR, BoarRenderer::new);
		ModelLayerRegistry.registerModelLayer(BoarModel.LAYER, BoarModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(BoarModel.BABY_LAYER, () -> BoarModel.createBodyLayer().apply(BoarModel.BABY_TRANSFORMER));
	}
}
