package com.jheinz.mymod;

import net.fabricmc.api.ModInitializer;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jheinz.mymod.bank.Banking;
import com.jheinz.mymod.hogfire.Hogfires;
import com.jheinz.mymod.industry.Industry;
import com.jheinz.mymod.neuralink.NeuralCore;
import com.jheinz.mymod.neuralink.NeuralinkEffects;
import com.jheinz.mymod.exoskeleton.ExoSockets;
import com.jheinz.mymod.exoskeleton.ExoskeletonEffects;
import com.jheinz.mymod.launcher.Launchers;
import com.jheinz.mymod.lmg.MachineGuns;
import com.jheinz.mymod.napalm.Napalm;
import com.jheinz.mymod.longsword.LongSwords;
import com.jheinz.mymod.magmasteel.Magmasteel;
import com.jheinz.mymod.shotgun.Shotguns;
import com.jheinz.mymod.powerarmor.PowerArmors;
import com.jheinz.mymod.schnozzle.Schnozzles;
import com.jheinz.mymod.skullcrane.Skullcranes;
import com.jheinz.mymod.soul.Souls;
import com.jheinz.mymod.silver.Silver;
import com.jheinz.mymod.tank.Tanks;
import com.jheinz.mymod.tungsten.Tungsten;
import com.jheinz.mymod.uranium.Uranium;
import com.jheinz.mymod.station.ModMenus;

public class MyMod implements ModInitializer {
	public static final String MOD_ID = "mymod";

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
		Souls.initialize();
		Silver.initialize();
		Tungsten.initialize();
		Uranium.initialize();
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
		MachineGuns.initialize();
		Napalm.initialize();
		LongSwords.initialize();
		Banking.initialize();
		NeuralCore.initialize();
		PowerArmors.initialize();
		Schnozzles.initialize();
		Skullcranes.initialize();
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
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
