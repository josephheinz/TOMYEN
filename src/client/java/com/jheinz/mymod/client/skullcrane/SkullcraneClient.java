package com.jheinz.mymod.client.skullcrane;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import net.minecraft.client.renderer.entity.NoopRenderer;

import com.jheinz.mymod.skullcrane.Skullcranes;

/** The skullcrane's renderer and model; its grip is invisible. */
public final class SkullcraneClient {
	private SkullcraneClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(Skullcranes.SKULLCRANE, SkullcraneRenderer::new);
		ModelLayerRegistry.registerModelLayer(SkullcraneModel.LAYER, SkullcraneModel::createBodyLayer);
		EntityRendererRegistry.register(Skullcranes.GRIP, NoopRenderer::new);
	}
}
