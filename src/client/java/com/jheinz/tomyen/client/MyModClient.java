package com.jheinz.tomyen.client;

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

import com.jheinz.tomyen.GunItem;
import com.jheinz.tomyen.GunRecoil;
import com.jheinz.tomyen.ModEntities;
import com.jheinz.tomyen.ModNetworking;
import com.jheinz.tomyen.ModParticles;
import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.client.scarecrow.ScarecrowClient;
import com.jheinz.tomyen.client.gnome.GnomeClient;
import com.jheinz.tomyen.client.survival.SurvivalClient;
import com.jheinz.tomyen.client.alien.AlienClient;
import com.jheinz.tomyen.client.accountant.AccountantClient;
import com.jheinz.tomyen.client.bank.BankClient;
import com.jheinz.tomyen.client.bigredboots.BigRedBootsRenderer;
import com.jheinz.tomyen.client.cathode.CathodeWalkerClient;
import com.jheinz.tomyen.client.cart.ShoppingCartModel;
import com.jheinz.tomyen.client.cart.ShoppingCartRenderer;
import com.jheinz.tomyen.client.holy.HolyBeams;
import com.jheinz.tomyen.client.holy.LightOrbRenderer;
import com.jheinz.tomyen.client.vampire.VampireCloakModel;
import com.jheinz.tomyen.client.vampire.VampireModel;
import com.jheinz.tomyen.client.vampire.VampireRenderer;
import com.jheinz.tomyen.client.neuralink.NeuralBeams;
import com.jheinz.tomyen.client.neuralink.NeuralClient;
import com.jheinz.tomyen.client.neuralink.NeuralHud;
import com.jheinz.tomyen.client.neuralink.NeuralSenses;
import com.jheinz.tomyen.client.neuralink.NeuralTrigger;
import com.jheinz.tomyen.client.neuralink.NeuralinkScreen;
import com.jheinz.tomyen.client.neuralink.NeuralinkVision;
import com.jheinz.tomyen.client.neuralink.OreScanner;
import com.jheinz.tomyen.client.detector.DetectorClient;
import com.jheinz.tomyen.client.dragonscale.DragonscaleClient;
import com.jheinz.tomyen.client.drill.DrillClient;
import com.jheinz.tomyen.client.energy.EnergyClient;
import com.jheinz.tomyen.client.exoskeleton.ExoskeletonRenderer;
import com.jheinz.tomyen.client.exoskeleton.ExoskeletonScreen;
import com.jheinz.tomyen.client.fae.FaeClient;
import com.jheinz.tomyen.client.fishing.FishingClient;
import com.jheinz.tomyen.client.fishgun.FishGunClient;
import com.jheinz.tomyen.client.shrimp.ShrimpClient;
import com.jheinz.tomyen.client.moose.MooseClient;
import com.jheinz.tomyen.client.leafblower.LeafBlowerClient;
import com.jheinz.tomyen.client.fishing.fish.FishEntitiesClient;
import com.jheinz.tomyen.client.gunsmith.GunsmithClient;
import com.jheinz.tomyen.client.hogfire.HogfireClient;
import com.jheinz.tomyen.client.hag.BogHagClient;
import com.jheinz.tomyen.client.schnozzle.SchnozzleClient;
import com.jheinz.tomyen.client.skullcrane.SkullcraneClient;
import com.jheinz.tomyen.client.werewolf.WerewolfClient;
import com.jheinz.tomyen.client.boar.BoarClient;
import com.jheinz.tomyen.client.banana.BananaClient;
import com.jheinz.tomyen.client.banshee.BansheeClient;
import com.jheinz.tomyen.client.soul.SoulVacuumClient;
import com.jheinz.tomyen.client.silver.SilverClient;
import com.jheinz.tomyen.client.industry.IndustryClient;
import com.jheinz.tomyen.client.launcher.LauncherClient;
import com.jheinz.tomyen.client.lmg.M249Animation;
import com.jheinz.tomyen.client.minigun.MinigunClient;
import com.jheinz.tomyen.client.napalm.NapalmClient;
import com.jheinz.tomyen.client.shotgun.ShotgunAnimation;
import com.jheinz.tomyen.client.mech.MechClient;
import com.jheinz.tomyen.client.powerarmor.PowerArmorClient;
import com.jheinz.tomyen.client.tank.TankClient;
import com.jheinz.tomyen.client.uranium.UraniumClient;
import com.jheinz.tomyen.client.starforge.StarForgeClient;
import com.jheinz.tomyen.client.bloomery.BloomeryClient;
import com.jheinz.tomyen.client.meteorite.MeteorRenderer;
import com.jheinz.tomyen.meteorite.Meteorites;
import com.jheinz.tomyen.station.ModMenus;
import com.jheinz.tomyen.client.scrapbench.ScrapBenchClient;
import com.jheinz.tomyen.client.sodajetpack.SodaJetpackClient;
import com.jheinz.tomyen.client.tvhelmet.TvHelmetClient;
import com.jheinz.tomyen.client.trombone.TromboneClient;
import com.jheinz.tomyen.client.gravedigger.GraveDiggerClient;

public class MyModClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(MyMod.id("weapons"));
	private static final KeyMapping FIRE_MODE_KEY = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.tomyen.fire_mode", InputConstants.Type.KEYBOARD, InputConstants.KEY_B, CATEGORY));
	// Shares F with vanilla's swap-hands key on purpose: guns can't go in the off hand, so with one held F inspects instead.
	private static final KeyMapping INSPECT_KEY = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.tomyen.inspect", InputConstants.Type.KEYBOARD, InputConstants.KEY_F, CATEGORY));

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
			GunReloads.tick(client);
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
		ScrapBenchClient.initialize();
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
		AccountantClient.initialize();
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
		BansheeClient.initialize();
		BoarClient.initialize();
		BananaClient.initialize();
		BigRedBootsRenderer.initialize();
		TvHelmetClient.initialize();
		SodaJetpackClient.initialize();
		SoulVacuumClient.initialize();
		SilverClient.initialize();
		NapalmClient.initialize();
		MinigunClient.initialize();
		FaeClient.initialize();
		DragonscaleClient.initialize();
		AlienClient.initialize();
		EnergyClient.initialize();
		FishingClient.initialize();
		FishEntitiesClient.initialize();
		DetectorClient.initialize();
		SurvivalClient.initialize();
		ScarecrowClient.initialize();
		GnomeClient.initialize();
		StarForgeClient.initialize();
		BloomeryClient.initialize();
		GunsmithClient.initialize();
		FishGunClient.initialize();
		LeafBlowerClient.initialize();
		TromboneClient.initialize();
		ShrimpClient.initialize();
		GraveDiggerClient.initialize();
		BogHagClient.initialize();
		MooseClient.initialize();
	}
}
