package com.jheinz.tomyen.client.skullcrane;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import net.minecraft.client.renderer.entity.NoopRenderer;

import com.jheinz.tomyen.skullcrane.Skullcranes;

/**
 * The skullcrane's and its pack leader's renderer and models (their grip is invisible); the Servo Drone, the Grapple Claw's claw and cable,
 * the worn monocle and backpack, and the monocle's zoom and health bars.
 */
public final class SkullcraneClient {
	private SkullcraneClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(Skullcranes.SKULLCRANE, SkullcraneRenderer::new);
		ModelLayerRegistry.registerModelLayer(SkullcraneModel.LAYER, SkullcraneModel::createBodyLayer);
		EntityRendererRegistry.register(Skullcranes.SKULLCRANE_LEADER, context -> new SkullcraneRenderer(context, true));
		ModelLayerRegistry.registerModelLayer(SkullcraneModel.LEADER_LAYER, SkullcraneModel::createLeaderLayer);
		EntityRendererRegistry.register(Skullcranes.GRIP, NoopRenderer::new);
		EntityRendererRegistry.register(Skullcranes.SERVO_DRONE_ENTITY, ServoDroneRenderer::new);
		EntityRendererRegistry.register(Skullcranes.GRAPPLE_HOOK, GrappleHookRenderer::new);
		ServoGearRenderer.initialize();
		MonocleHud.initialize();
	}
}
