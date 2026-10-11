package com.jheinz.mymod.station;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

import com.jheinz.mymod.MyMod;

public final class ModMenus {
	public static final MenuType<WeaponStationMenu> WEAPON_STATION = Registry.register(BuiltInRegistries.MENU,
			MyMod.id("weapon_station"), new MenuType<>(WeaponStationMenu::new, FeatureFlags.VANILLA_SET));

	private ModMenus() {
	}

	public static void initialize() {
		StationRecipes.initialize();
	}
}
