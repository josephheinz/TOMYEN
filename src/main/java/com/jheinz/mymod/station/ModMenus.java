package com.jheinz.mymod.station;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.exoskeleton.ExoskeletonMenu;
import com.jheinz.mymod.foundry.FoundryMenu;
import com.jheinz.mymod.neuralink.NeuralinkMenu;

public final class ModMenus {
	public static final MenuType<WeaponStationMenu> WEAPON_STATION = Registry.register(BuiltInRegistries.MENU,
			MyMod.id("weapon_station"), new MenuType<>(WeaponStationMenu::new, FeatureFlags.VANILLA_SET));

	public static final MenuType<NeuralinkMenu> NEURALINK = Registry.register(BuiltInRegistries.MENU,
			MyMod.id("neuralink"), new MenuType<>(NeuralinkMenu::new, FeatureFlags.VANILLA_SET));

	public static final MenuType<ExoskeletonMenu> EXOSKELETON = Registry.register(BuiltInRegistries.MENU,
			MyMod.id("exoskeleton"), new MenuType<>(ExoskeletonMenu::new, FeatureFlags.VANILLA_SET));

	public static final MenuType<FoundryMenu> FOUNDRY = Registry.register(BuiltInRegistries.MENU,
			MyMod.id("foundry"), new MenuType<>(FoundryMenu::new, FeatureFlags.VANILLA_SET));

	private ModMenus() {
	}

	public static void initialize() {
		StationRecipes.initialize();
	}
}
