package com.jheinz.tomyen.client.bathtub;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.bathtub.BathtubBoat;
import com.jheinz.tomyen.bathtub.Bathtubs;

/**
 * The Bathtub Boat's client side: its renderer and model, the dash, the outboard's sound on every running boat nearby,
 * and the honk: the honk key, or attacking thin air while riding, squeezes the rubber duck.
 */
public final class BathtubClient {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(MyMod.id("vehicles"));
	public static final KeyMapping HONK_KEY = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.tomyen.honk", InputConstants.Type.KEYBOARD, InputConstants.KEY_H, CATEGORY));
	/** How far off a motor can be heard starting. */
	private static final double HEARING = 48.0;
	private static final Map<Integer, MotorSound> MOTORS = new HashMap<>();
	private static boolean attackWasDown;

	private BathtubClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(Bathtubs.BATHTUB_BOAT_ENTITY, BathtubBoatRenderer::new);
		ModelLayerRegistry.registerModelLayer(BathtubBoatModel.LAYER, BathtubBoatModel::createBodyLayer);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, MyMod.id("bathtub_boat"), BathtubHud::extract);
		ClientTickEvents.END_CLIENT_TICK.register(BathtubClient::tick);
	}

	private static void tick(Minecraft minecraft) {
		if (minecraft.player == null || minecraft.level == null) {
			MOTORS.clear();
			while (HONK_KEY.consumeClick()) {
				// Not riding anything: ignore.
			}
			return;
		}
		boolean riding = minecraft.player.getVehicle() instanceof BathtubBoat;
		boolean honk = false;
		while (HONK_KEY.consumeClick()) {
			honk |= riding;
		}
		// Swinging at nothing from the tub squeezes the duck too (it doesn't take the click: the arm still swings).
		boolean attack = minecraft.options.keyAttack.isDown();
		if (riding && attack && !attackWasDown && minecraft.gui.screen() == null
				&& (minecraft.hitResult == null || minecraft.hitResult.getType() == HitResult.Type.MISS)) {
			honk = true;
		}
		attackWasDown = attack;
		if (honk) {
			ClientPlayNetworking.send(Bathtubs.Honk.INSTANCE);
		}

		MOTORS.values().removeIf(MotorSound::isStopped);
		AABB hearing = minecraft.player.getBoundingBox().inflate(HEARING);
		for (BathtubBoat boat : minecraft.level.getEntitiesOfClass(BathtubBoat.class, hearing, BathtubBoat::engineRunning)) {
			MOTORS.computeIfAbsent(boat.getId(), id -> {
				MotorSound sound = new MotorSound(boat);
				minecraft.getSoundManager().play(sound);
				return sound;
			});
		}
	}
}
