package com.jheinz.tomyen.client.snowwarden;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import net.minecraft.client.renderer.entity.ThrownItemRenderer;

import com.jheinz.tomyen.snowwarden.SnowWardens;

/** The Snow Warden's renderer and model layer, and its thrown snowballs, drawn as a snowball three times the size. */
public final class SnowWardenClient {
	private SnowWardenClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(SnowWardens.SNOW_WARDEN, SnowWardenRenderer::new);
		ModelLayerRegistry.registerModelLayer(SnowWardenModel.LAYER, SnowWardenModel::createLayer);
		EntityRendererRegistry.register(SnowWardens.FROST_BOULDER, context -> new ThrownItemRenderer<>(context, 3.0f, true));
	}
}
