package com.jheinz.mymod.client.alien;

import java.util.HashMap;
import java.util.Map;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;

import com.jheinz.mymod.alien.Aliens;
import com.jheinz.mymod.alien.Ufo;

/** The UFO, alien and tractor beam renderers, and the saucers' hum. */
public final class AlienClient {
	private static final Map<Integer, UfoHumSound> HUMS = new HashMap<>();

	private AlienClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(Aliens.UFO, UfoRenderer::new);
		EntityRendererRegistry.register(Aliens.ALIEN, AlienRenderer::new);
		ModelLayerRegistry.registerModelLayer(AlienModel.LAYER, AlienModel::createBodyLayer);
		EntityRendererRegistry.register(Aliens.TRACTOR_BEAM, TractorBeamRenderer::new);
		ClientTickEvents.END_CLIENT_TICK.register(AlienClient::tick);
	}

	/** Starts the hum of every saucer within earshot that isn't humming yet. */
	private static void tick(Minecraft client) {
		if (client.player == null || client.level == null) {
			HUMS.clear();
			return;
		}
		HUMS.values().removeIf(UfoHumSound::isStopped);
		AABB hearing = client.player.getBoundingBox().inflate(UfoHumSound.RANGE);
		for (Ufo ufo : client.level.getEntitiesOfClass(Ufo.class, hearing, Ufo::isAlive)) {
			HUMS.computeIfAbsent(ufo.getId(), id -> {
				UfoHumSound sound = new UfoHumSound(ufo);
				client.getSoundManager().play(sound);
				return sound;
			});
		}
	}
}
