package com.jheinz.mymod.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;

import com.jheinz.mymod.GunItem;
import com.jheinz.mymod.GunRecoil;
import com.jheinz.mymod.ModNetworking;
import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.station.ModMenus;

public class MyModClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(MyMod.id("weapons"));
	private static final KeyMapping FIRE_MODE_KEY = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.mymod.fire_mode", InputConstants.Type.KEYBOARD, InputConstants.KEY_B, CATEGORY));

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			GunRecoil.tick(client.player);
			GunAnimation.tick(client.player);
			while (FIRE_MODE_KEY.consumeClick()) {
				if (client.player != null && client.player.getMainHandItem().getItem() instanceof GunItem) {
					ClientPlayNetworking.send(ModNetworking.CycleFireMode.INSTANCE);
				}
			}
		});
		MenuScreens.register(ModMenus.WEAPON_STATION, WeaponStationScreen::new);
		RangeSelectItemModelProperties.ID_MAPPER.put(MyMod.id("gun_animation"), GunAnimationProperty.MAP_CODEC);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, MyMod.id("ammo"), AmmoHud::extract);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR, MyMod.id("scope"), GunScope::extract);
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (graphics, delta) -> {
			if (!GunCrosshair.extract(graphics, delta)) {
				vanilla.extractRenderState(graphics, delta);
			}
		});
	}
}
