package com.jheinz.tomyen.client.scarecrow;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import com.jheinz.tomyen.scarecrow.Scarecrows;

/** The scarecrow's renderer and model. */
public final class ScarecrowClient {
	private ScarecrowClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(Scarecrows.SCARECROW, ScarecrowRenderer::new);
		ModelLayerRegistry.registerModelLayer(ScarecrowModel.LAYER, ScarecrowModel::createBodyLayer);
	}
}
