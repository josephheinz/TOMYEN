package com.jheinz.tomyen.client.moose;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import com.jheinz.tomyen.moose.Mooses;

/** The moose's renderer and model layer. */
public final class MooseClient {
	private MooseClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(Mooses.MOOSE, MooseRenderer::new);
		ModelLayerRegistry.registerModelLayer(MooseModel.LAYER, MooseModel::createLayer);
	}
}
