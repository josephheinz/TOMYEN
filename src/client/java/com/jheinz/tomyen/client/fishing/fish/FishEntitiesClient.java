package com.jheinz.tomyen.client.fishing.fish;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import net.minecraft.client.model.geom.ModelLayerLocation;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.fishing.fish.FishKind;

/** Each living fish's model layer and renderer (one renderer class, each with its own model and texture). */
public final class FishEntitiesClient {
	private FishEntitiesClient() {
	}

	public static void initialize() {
		Map<String, FishModelLayers.Shape> shapes = FishModelLayers.SHAPES.stream()
				.collect(Collectors.toMap(FishModelLayers.Shape::id, Function.identity()));
		for (FishKind kind : FishKind.ALL) {
			FishModelLayers.Shape shape = shapes.get(kind.id());
			if (shape == null) {
				throw new IllegalStateException("No model for fish " + kind.id() + ": run tools/gen_fish_entities.py");
			}
			ModelLayerLocation layer = new ModelLayerLocation(MyMod.id(kind.id()), "main");
			ModelLayerRegistry.registerModelLayer(layer, () -> shape.layer().get());
			EntityRendererRegistry.register(kind.type(), context -> new FishEntityRenderer(context, shape, layer));
		}
	}
}
