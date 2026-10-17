package com.jheinz.tomyen.client.gnome;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import com.jheinz.tomyen.gnome.Gnomes;

/** The gnome's and the Gnome Captain's renderers and model layers. */
public final class GnomeClient {
	private GnomeClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(Gnomes.GNOME, context -> new GnomeRenderer(context, false));
		EntityRendererRegistry.register(Gnomes.GNOME_CAPTAIN, context -> new GnomeRenderer(context, true));
		ModelLayerRegistry.registerModelLayer(GnomeModel.TROOP_LAYER, GnomeModel::createTroopLayer);
		ModelLayerRegistry.registerModelLayer(GnomeModel.CAPTAIN_LAYER, GnomeModel::createCaptainLayer);
	}
}
