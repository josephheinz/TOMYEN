package com.jheinz.tomyen;

import net.fabricmc.api.ModInitializer;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jheinz.tomyen.alien.Aliens;
import com.jheinz.tomyen.bank.Banking;
import com.jheinz.tomyen.bigredboots.BigRedBoots;
import com.jheinz.tomyen.bloomery.Bloomery;
import com.jheinz.tomyen.cathode.CathodeWalkers;
import com.jheinz.tomyen.detector.Detectors;
import com.jheinz.tomyen.fae.Fae;
import com.jheinz.tomyen.fishgun.FishGuns;
import com.jheinz.tomyen.leafblower.LeafBlowers;
import com.jheinz.tomyen.fishing.Fishing;
import com.jheinz.tomyen.herbalism.Herbalism;
import com.jheinz.tomyen.dragonscale.Dragonscales;
import com.jheinz.tomyen.fishing.fish.FishEntities;
import com.jheinz.tomyen.fishing.fish.Fishes;
import com.jheinz.tomyen.drill.Drills;
import com.jheinz.tomyen.hogfire.Hogfires;
import com.jheinz.tomyen.industry.Industry;
import com.jheinz.tomyen.neuralink.NeuralCore;
import com.jheinz.tomyen.neuralink.NeuralinkEffects;
import com.jheinz.tomyen.energy.EnergyWeapons;
import com.jheinz.tomyen.exoskeleton.ExoSockets;
import com.jheinz.tomyen.exoskeleton.ExoskeletonEffects;
import com.jheinz.tomyen.froststeel.Froststeel;
import com.jheinz.tomyen.launcher.Launchers;
import com.jheinz.tomyen.meteorite.Meteorites;
import com.jheinz.tomyen.lmg.MachineGuns;
import com.jheinz.tomyen.napalm.Napalm;
import com.jheinz.tomyen.minigun.Miniguns;
import com.jheinz.tomyen.longsword.LongSwords;
import com.jheinz.tomyen.magmasteel.Magmasteel;
import com.jheinz.tomyen.mech.Mechs;
import com.jheinz.tomyen.shotgun.Shotguns;
import com.jheinz.tomyen.shoddy.ShoddyGuns;
import com.jheinz.tomyen.gunsmith.Gunsmith;
import com.jheinz.tomyen.powerarmor.PowerArmors;
import com.jheinz.tomyen.schnozzle.Schnozzles;
import com.jheinz.tomyen.skullcrane.Skullcranes;
import com.jheinz.tomyen.soul.Souls;
import com.jheinz.tomyen.silver.Silver;
import com.jheinz.tomyen.tank.Tanks;
import com.jheinz.tomyen.tungsten.Tungsten;
import com.jheinz.tomyen.uranium.Uranium;
import com.jheinz.tomyen.voidsteel.Voidsteel;
import com.jheinz.tomyen.werewolf.Werewolves;
import com.jheinz.tomyen.banana.Bananas;
import com.jheinz.tomyen.station.ModMenus;
import com.jheinz.tomyen.scarecrow.Scarecrows;
import com.jheinz.tomyen.survival.Survival;
import com.jheinz.tomyen.scrapbench.ScrapBench;
import com.jheinz.tomyen.blueprint.Blueprints;
import com.jheinz.tomyen.starforge.StarForge;
import com.jheinz.tomyen.sodajetpack.SodaJetpacks;
import com.jheinz.tomyen.tvhelmet.TvHelmets;
import com.jheinz.tomyen.trombone.Trombones;

public class MyMod implements ModInitializer {
	public static final String MOD_ID = "tomyen";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		ModComponents.initialize();
		ModSounds.initialize();
		ModParticles.initialize();
		ModEffects.initialize();
		ModItems.initialize();
		ModCreativeTabs.initialize();
		ModBlocks.initialize();
		Industry.initialize();
		Magmasteel.initialize();
		Bloomery.initialize();
		Souls.initialize();
		Silver.initialize();
		Tungsten.initialize();
		Uranium.initialize();
		Dragonscales.initialize();
		Meteorites.initialize();
		Drills.initialize();
		Froststeel.initialize();
		Voidsteel.initialize();
		Hogfires.initialize();
		ModEntities.initialize();
		ModMenus.initialize();
		ModNetworking.initialize();
		BurstFire.initialize();
		GunOffhand.initialize();
		NeuralinkEffects.initialize();
		ExoskeletonEffects.initialize();
		ExoSockets.initialize();
		Tanks.initialize();
		Launchers.initialize();
		Shotguns.initialize();
		ShoddyGuns.initialize();
		Gunsmith.initialize();
		MachineGuns.initialize();
		Napalm.initialize();
		Miniguns.initialize();
		LongSwords.initialize();
		Banking.initialize();
		NeuralCore.initialize();
		PowerArmors.initialize();
		Mechs.initialize();
		Schnozzles.initialize();
		Skullcranes.initialize();
		CathodeWalkers.initialize();
		Werewolves.initialize();
		Bananas.initialize();
		BigRedBoots.initialize();
		TvHelmets.initialize();
		SodaJetpacks.initialize();
		EnergyWeapons.initialize();
		Aliens.initialize();
		Fishes.initialize();
		FishEntities.initialize();
		Fishing.initialize();
		Detectors.initialize();
		Survival.initialize();
		Scarecrows.initialize();
		ScrapBench.initialize();
		Blueprints.initialize();
		StarForge.initialize();
		Herbalism.initialize();
		FishGuns.initialize();
		LeafBlowers.initialize();
		Trombones.initialize();
		linkBlockItems();
	}

	/**
	 * Registering a {@link BlockItem} straight into the item registry skips vanilla's block-to-item link, which
	 * leaves {@code block.asItem()} as air (breaking pick-block and creative tab entries). Link every mod block item
	 * that's missing one.
	 */
	private static void linkBlockItems() {
		for (Item item : BuiltInRegistries.ITEM) {
			if (item instanceof BlockItem blockItem && MOD_ID.equals(BuiltInRegistries.ITEM.getKey(item).getNamespace())
					&& !Item.BY_BLOCK.containsKey(blockItem.getBlock())) {
				blockItem.registerBlocks(Item.BY_BLOCK, item);
			}
		}
		Fae.initialize();
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
