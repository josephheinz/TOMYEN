package com.jheinz.mymod.client.hogfire;

import java.util.HashMap;
import java.util.Map;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.hogfire.Hogfire;
import com.jheinz.mymod.hogfire.Hogfires;

/**
 * The Hogfire's client side. Flying one, the pilot's view and keys go to the server every tick (forward and back
 * work the throttle, jump is the afterburner); the server flies it and the flight panel shows how it's doing.
 * Every geared-up Hogfire nearby gets an engine sound that follows it.
 */
public final class HogfireClient {
	/** How far away an engine can be heard starting up. */
	private static final double HEARING = 64.0;
	private static final Map<Integer, HogfireEngineSound> ENGINES = new HashMap<>();

	private HogfireClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(Hogfires.HOGFIRE, HogfireRenderer::new);
		ModelLayerRegistry.registerModelLayer(HogfireGearLayer.LAYER, HogfireGearLayer::createLayer);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, MyMod.id("hogfire"), HogfireHud::extract);
		ClientTickEvents.END_CLIENT_TICK.register(HogfireClient::tick);
	}

	private static void tick(Minecraft client) {
		if (client.player == null || client.level == null) {
			ENGINES.clear();
			return;
		}
		if (client.player.getVehicle() instanceof Hogfire hogfire && hogfire.pilot() == client.player) {
			ClientPlayNetworking.send(new Hogfires.Controls(client.player.getYRot(), client.player.getXRot(),
					client.options.keyUp.isDown(), client.options.keyDown.isDown(), client.options.keyJump.isDown()));
		}
		ENGINES.values().removeIf(HogfireEngineSound::isStopped);
		AABB hearing = client.player.getBoundingBox().inflate(HEARING);
		for (Hogfire hogfire : client.level.getEntitiesOfClass(Hogfire.class, hearing, Hogfire::hasEngine)) {
			ENGINES.computeIfAbsent(hogfire.getId(), id -> {
				HogfireEngineSound sound = new HogfireEngineSound(hogfire);
				client.getSoundManager().play(sound);
				return sound;
			});
		}
	}
}
