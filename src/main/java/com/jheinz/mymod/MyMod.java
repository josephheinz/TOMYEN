package com.jheinz.mymod;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jheinz.mymod.bank.Banking;
import com.jheinz.mymod.hogfire.Hogfires;
import com.jheinz.mymod.industry.Industry;
import com.jheinz.mymod.neuralink.NeuralCore;
import com.jheinz.mymod.neuralink.NeuralinkEffects;
import com.jheinz.mymod.exoskeleton.ExoskeletonEffects;
import com.jheinz.mymod.launcher.Launchers;
import com.jheinz.mymod.lmg.MachineGuns;
import com.jheinz.mymod.shotgun.Shotguns;
import com.jheinz.mymod.powerarmor.PowerArmors;
import com.jheinz.mymod.schnozzle.Schnozzles;
import com.jheinz.mymod.tank.Tanks;
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
		Hogfires.initialize();
		ModEntities.initialize();
		ModMenus.initialize();
		ModNetworking.initialize();
		BurstFire.initialize();
		GunOffhand.initialize();
		NeuralinkEffects.initialize();
		ExoskeletonEffects.initialize();
		Tanks.initialize();
		Launchers.initialize();
		Shotguns.initialize();
		MachineGuns.initialize();
		Banking.initialize();
		NeuralCore.initialize();
		PowerArmors.initialize();
		Schnozzles.initialize();
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
