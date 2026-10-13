package com.jheinz.mymod.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;

import com.jheinz.mymod.GunItem;
import com.jheinz.mymod.GunRecoil;
import com.jheinz.mymod.ModEntities;
import com.jheinz.mymod.ModNetworking;
import com.jheinz.mymod.ModParticles;
import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.client.bank.BankClient;
import com.jheinz.mymod.client.cathode.CathodeWalkerClient;
import com.jheinz.mymod.client.cart.ShoppingCartModel;
import com.jheinz.mymod.client.cart.ShoppingCartRenderer;
import com.jheinz.mymod.client.holy.HolyBeams;
import com.jheinz.mymod.client.holy.LightOrbRenderer;
import com.jheinz.mymod.client.vampire.VampireCloakModel;
import com.jheinz.mymod.client.vampire.VampireModel;
import com.jheinz.mymod.client.vampire.VampireRenderer;
import com.jheinz.mymod.client.neuralink.NeuralBeams;
import com.jheinz.mymod.client.neuralink.NeuralClient;
import com.jheinz.mymod.client.neuralink.NeuralHud;
import com.jheinz.mymod.client.neuralink.NeuralSenses;
import com.jheinz.mymod.client.neuralink.NeuralTrigger;
import com.jheinz.mymod.client.neuralink.NeuralinkScreen;
import com.jheinz.mymod.client.neuralink.NeuralinkVision;
import com.jheinz.mymod.client.neuralink.OreScanner;
import com.jheinz.mymod.client.dragonscale.DragonscaleClient;
import com.jheinz.mymod.client.drill.DrillClient;
import com.jheinz.mymod.client.exoskeleton.ExoskeletonRenderer;
import com.jheinz.mymod.client.exoskeleton.ExoskeletonScreen;
import com.jheinz.mymod.client.fae.FaeClient;
import com.jheinz.mymod.client.hogfire.HogfireClient;
import com.jheinz.mymod.client.schnozzle.SchnozzleClient;
import com.jheinz.mymod.client.skullcrane.SkullcraneClient;
import com.jheinz.mymod.client.werewolf.WerewolfClient;
import com.jheinz.mymod.client.soul.SoulVacuumClient;
import com.jheinz.mymod.client.silver.SilverClient;
import com.jheinz.mymod.client.industry.IndustryClient;
import com.jheinz.mymod.client.launcher.LauncherClient;
import com.jheinz.mymod.client.lmg.M249Animation;
import com.jheinz.mymod.client.minigun.MinigunClient;
import com.jheinz.mymod.client.napalm.NapalmClient;
import com.jheinz.mymod.client.shotgun.ShotgunAnimation;
import com.jheinz.mymod.client.mech.MechClient;
import com.jheinz.mymod.client.powerarmor.PowerArmorClient;
import com.jheinz.mymod.client.tank.TankClient;
import com.jheinz.mymod.client.uranium.UraniumClient;
import com.jheinz.mymod.client.meteorite.MeteorRenderer;
import com.jheinz.mymod.meteorite.Meteorites;
import com.jheinz.mymod.station.ModMenus;

public class MyModClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(MyMod.id("weapons"));
	private static final KeyMapping FIRE_MODE_KEY = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.mymod.fire_mode", InputConstants.Type.KEYBOARD, InputConstants.KEY_B, CATEGORY));
	// Shares F with vanilla's swap-hands key on purpose: guns can't go in the off hand, so with one held F inspects instead.
	private static final KeyMapping INSPECT_KEY = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.mymod.inspect", InputConstants.Type.KEYBOARD, InputConstants.KEY_F, CATEGORY));

	@Override
	public void onInitializeClient() {
		// Runs before vanilla handles keys this tick: drop swap-hands presses while holding a gun.
		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			if (client.player != null && client.player.getMainHandItem().getItem() instanceof GunItem) {
				while (client.options.keySwapOffhand.consumeClick()) {
				}
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			GunRecoil.tick(client.player);
			GunAnimation.tick(client.player);
			ShotgunAnimation.tick(client.player);
			M249Animation.tick(client.player);
			LaserSights.tick(client);
			OreScanner.tick(client);
			NeuralTrigger.tick(client);
			while (FIRE_MODE_KEY.consumeClick()) {
				if (client.player != null && client.player.getMainHandItem().getItem() instanceof GunItem) {
					ClientPlayNetworking.send(ModNetworking.CycleFireMode.INSTANCE);
				}
			}
			while (INSPECT_KEY.consumeClick()) {
				GunAnimation.inspect(client.player);
			}
		});
		MenuScreens.register(ModMenus.WEAPON_STATION, WeaponStationScreen::new);
		MenuScreens.register(ModMenus.NEURALINK, NeuralinkScreen::new);
		MenuScreens.register(ModMenus.EXOSKELETON, ExoskeletonScreen::new);
		ExoskeletonRenderer.initialize();
		MenuScreens.register(ModMenus.FOUNDRY, FoundryScreen::new);
		EntityRendererRegistry.register(ModEntities.SHOPPING_CART, ShoppingCartRenderer::new);
		ModelLayerRegistry.registerModelLayer(ShoppingCartModel.LAYER, ShoppingCartModel::createBodyLayer);
		EntityRendererRegistry.register(ModEntities.VAMPIRE, VampireRenderer::new);
		ModelLayerRegistry.registerModelLayer(VampireModel.LAYER, VampireModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(VampireCloakModel.LAYER, VampireCloakModel::createBodyLayer);
		EntityRendererRegistry.register(ModEntities.LIGHT_ORB, LightOrbRenderer::new);
		EntityRendererRegistry.register(Meteorites.METEOR, MeteorRenderer::new);
		ClientPlayNetworking.registerGlobalReceiver(ModNetworking.LightBeam.TYPE, (payload, context) -> HolyBeams.receive(payload));
		LevelRenderEvents.COLLECT_SUBMITS.register(HolyBeams::submit);
		ParticleProviderRegistry.getInstance().register(ModParticles.LASER_DOT, LaserDotParticle.Provider::new);
		ParticleProviderRegistry.getInstance().register(ModParticles.GREEN_LASER_DOT, LaserDotParticle.Provider::new);
		LevelRenderEvents.COLLECT_SUBMITS.register(LaserBeams::submit);
		LevelRenderEvents.COLLECT_SUBMITS.register(NeuralinkVision::submit);
		LevelRenderEvents.COLLECT_SUBMITS.register(NeuralBeams::submit);
		NeuralTrigger.initialize();
		NeuralClient.register();
		RangeSelectItemModelProperties.ID_MAPPER.put(MyMod.id("gun_animation"), GunAnimationProperty.MAP_CODEC);
		GunModelProperties.register();
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, MyMod.id("ammo"), AmmoHud::extract);
		// Screen-wide Neuralink effects go under everything else; world-anchored markers under the crosshair.
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, MyMod.id("neuralink_screen"), NeuralHud::extractScreen);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR, MyMod.id("neuralink_senses"), NeuralSenses::extract);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, MyMod.id("neuralink"), NeuralHud::extract);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR, MyMod.id("scope"), GunScope::extract);
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (graphics, delta) -> {
			if (!GunCrosshair.extract(graphics, delta)) {
				vanilla.extractRenderState(graphics, delta);
			}
		});
		TankClient.initialize();
		LauncherClient.initialize();
		BankClient.initialize();
		PowerArmorClient.initialize();
		MechClient.initialize();
		IndustryClient.initialize();
		UraniumClient.initialize();
		DrillClient.initialize();
		HogfireClient.initialize();
		SchnozzleClient.initialize();
		SkullcraneClient.initialize();
		CathodeWalkerClient.initialize();
		WerewolfClient.initialize();
		SoulVacuumClient.initialize();
		SilverClient.initialize();
		NapalmClient.initialize();
		MinigunClient.initialize();
		FaeClient.initialize();
		DragonscaleClient.initialize();
	}
}
