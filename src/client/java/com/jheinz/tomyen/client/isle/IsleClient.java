package com.jheinz.tomyen.client.isle;

import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.isle.Isle;

/** The Isle of the Damned on the client: the Revenant's renderer, the gravestone's carved name and the HUD hint. */
public final class IsleClient {
	private IsleClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(Isle.REVENANT, RevenantRenderer::new);
		ModelLayerRegistry.registerModelLayer(RevenantRenderer.AURA, () -> RevenantRenderer.createAura(false));
		ModelLayerRegistry.registerModelLayer(RevenantRenderer.AURA_SLIM, () -> RevenantRenderer.createAura(true));
		BlockEntityRendererRegistry.register(Isle.GRAVESTONE_ENTITY, GravestoneRenderer::new);
		HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, MyMod.id("isle_hint"), IsleHud::extract);
	}
}
