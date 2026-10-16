package com.jheinz.tomyen.client.banana;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import net.minecraft.client.renderer.entity.ThrownItemRenderer;

import com.jheinz.tomyen.banana.Bananas;

/** The Big Banana's renderer and model, and the thrown peel's. */
public final class BananaClient {
	private BananaClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(Bananas.BIG_BANANA, BigBananaRenderer::new);
		ModelLayerRegistry.registerModelLayer(BigBananaModel.LAYER, BigBananaModel::createBodyLayer);
		EntityRendererRegistry.register(Bananas.THROWN_BANANA_PEEL, ThrownItemRenderer::new);
	}
}
