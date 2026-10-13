package com.jheinz.mymod.client.uranium;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import net.minecraft.client.gui.screens.MenuScreens;

import com.jheinz.mymod.uranium.Uranium;

/** Uranium on the client: the Centrifuge's screen and rotor, the Geiger Counter. */
public final class UraniumClient {
	private UraniumClient() {
	}

	public static void initialize() {
		MenuScreens.register(Uranium.CENTRIFUGE_MENU, CentrifugeScreen::new);
		ModelLayerRegistry.registerModelLayer(CentrifugeRenderer.LAYER, CentrifugeRenderer::createLayer);
		BlockEntityRendererRegistry.register(Uranium.CENTRIFUGE_ENTITY, CentrifugeRenderer::new);
		ClientTickEvents.END_CLIENT_TICK.register(GeigerCounterClient::tick);
	}
}
