package com.jheinz.tomyen.client.tvhelmet;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import com.jheinz.tomyen.MyMod;

/** The TV Helmet's client side: the worn set and its screen, the emote wheel and keys, and the view through it. */
public final class TvHelmetClient {
	private TvHelmetClient() {
	}

	public static void initialize() {
		TvHelmetRenderer.initialize();
		TvEmoteWheel.initialize();
		ClientTickEvents.END_CLIENT_TICK.register(TvEmoteWheel::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, MyMod.id("tv_vision"), TvVisionOverlay::extract);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR, MyMod.id("tv_emote_wheel"), TvEmoteWheel::extract);
	}
}
