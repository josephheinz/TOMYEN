package com.jheinz.tomyen.client.hag;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import net.minecraft.client.renderer.entity.ThrownItemRenderer;

import com.jheinz.tomyen.hag.BogHags;

/** The Bog Hag's renderer and model, and her hexes, drawn as the gob of bog muck they are. */
public final class BogHagClient {
	private BogHagClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(BogHags.BOG_HAG, BogHagRenderer::new);
		ModelLayerRegistry.registerModelLayer(BogHagModel.LAYER, BogHagModel::createBodyLayer);
		EntityRendererRegistry.register(BogHags.HEX_GLOB, context -> new ThrownItemRenderer<>(context, 1.2f, true));
	}
}
