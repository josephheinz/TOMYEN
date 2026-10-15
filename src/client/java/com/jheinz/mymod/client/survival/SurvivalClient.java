package com.jheinz.mymod.client.survival;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.client.renderer.entity.ThrownItemRenderer;

import com.jheinz.mymod.survival.Survival;

/** The survival kit on the client: the thrown Molotov and pipe bomb are drawn as their item sprites. */
public final class SurvivalClient {
	private SurvivalClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(Survival.MOLOTOV, ThrownItemRenderer::new);
		EntityRendererRegistry.register(Survival.PIPE_BOMB, ThrownItemRenderer::new);
	}
}
