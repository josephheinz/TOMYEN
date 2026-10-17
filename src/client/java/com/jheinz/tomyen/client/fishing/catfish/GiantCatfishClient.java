package com.jheinz.tomyen.client.fishing.catfish;

import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import com.jheinz.tomyen.fishing.catfish.GiantCatfishes;

/** The Giant Catfish's renderer and model, and the Giant Fish Mount's trophy renderer. */
public final class GiantCatfishClient {
	private GiantCatfishClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(GiantCatfishes.GIANT_CATFISH_ENTITY, GiantCatfishRenderer::new);
		ModelLayerRegistry.registerModelLayer(GiantCatfishModel.LAYER, GiantCatfishModel::createBodyLayer);
		BlockEntityRendererRegistry.register(GiantCatfishes.GIANT_FISH_MOUNT_ENTITY, GiantFishMountRenderer::new);
	}
}
