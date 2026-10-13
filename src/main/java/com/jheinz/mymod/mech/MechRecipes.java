package com.jheinz.mymod.mech;

import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import com.jheinz.mymod.ModItems;
import com.jheinz.mymod.foundry.FoundryRecipe;
import com.jheinz.mymod.industry.Industry;
import com.jheinz.mymod.launcher.Launchers;
import com.jheinz.mymod.napalm.Napalm;
import com.jheinz.mymod.powerarmor.PowerArmors;
import com.jheinz.mymod.station.StationRecipe;
import com.jheinz.mymod.tungsten.Tungsten;

/**
 * The Bulwark Walker's recipes that live in code; all of them late-game, and all built from what the rest of the
 * mod makes. The gantry (steel, Servo Arms as its hoists), the actuators (a Skullcrane Servo Arm with a tungsten
 * carbide joint and rubber seals), the legs and arm frames (steel, actuators, carbide joints) and the Cargo Pod
 * (duralumin and rubber) are crafting-table recipes (data/mymod/recipe, written by tools/gen_mech.py); the rest:
 * <ul>
 * <li>Foundry: the cockpit torso (steel blocks and a bank of computer chips) and the power plant (a Fusion Core
 * built into two tungsten blocks), both needing it hot.
 * <li>Weapon Station: the five weapons (from steel and actuators, M249 barrels, carbide knuckles, RPG-7 tubes and a
 * flamethrower), the Smoke Launcher (duralumin and rubber) and the Autocannon Drum (iron and gunpowder).
 * </ul>
 */
public final class MechRecipes {
	private static final int TORSO_TICKS = 1200;
	private static final int POWER_PLANT_TICKS = 900;
	private static final int TEMPERATURE = 1400;

	private MechRecipes() {
	}

	public static List<FoundryRecipe> foundry() {
		return List.of(
				new FoundryRecipe(Mechs.TORSO, 1, TORSO_TICKS,
						List.of(foundry(blockItem(Industry.STEEL_BLOCK), 12), foundry(ModItems.COMPUTER_CHIP, 6))).withMinTemperature(TEMPERATURE),
				new FoundryRecipe(Mechs.POWER_PLANT, 1, POWER_PLANT_TICKS,
						List.of(foundry(PowerArmors.FUSION_CORE, 1), foundry(blockItem(Tungsten.TUNGSTEN_BLOCK), 2))).withMinTemperature(TEMPERATURE));
	}

	public static List<StationRecipe> station() {
		return List.of(
				// Plate-armoured breech and a steel-block barrel, fed by an actuator.
				station(Mechs.AUTOCANNON, 1, cost(blockItem(Industry.STEEL_BLOCK), 3), cost(Industry.STEEL_PLATE, 6), cost(Mechs.ACTUATOR, 1)),
				// Three machine gun barrels turned round a spindle.
				station(Mechs.ROTARY_CANNON, 1, cost(ModItems.M249_BARREL, 3), cost(blockItem(Industry.STEEL_BLOCK), 2), cost(Mechs.ACTUATOR, 1)),
				// Tungsten carbide knuckles on steel, closed by two actuators.
				station(Mechs.POWER_FIST, 1, cost(Tungsten.TUNGSTEN_CARBIDE, 4), cost(blockItem(Industry.STEEL_BLOCK), 4), cost(Mechs.ACTUATOR, 2)),
				// Two RPG-7 tubes cut down into four, with chips for the ripple fire control.
				station(Mechs.ROCKET_POD, 1, cost(Launchers.RPG7_LAUNCH_TUBE, 2), cost(Industry.STEEL_PLATE, 6), cost(ModItems.COMPUTER_CHIP, 2)),
				// A flamethrower's wand and tanks in an armoured housing.
				station(Mechs.FLAMER, 1, cost(Napalm.FLAMETHROWER_WAND, 1), cost(Napalm.FLAMETHROWER_TANKS, 1), cost(Industry.STEEL_PLATE, 4)),
				// Duralumin tubes on rubber mounts.
				station(Mechs.SMOKE_LAUNCHER, 1, cost(Industry.DURALUMIN_SHEET, 4), cost(Items.GUNPOWDER, 8), cost(Industry.RUBBER, 2)),
				// Plain iron shells over gunpowder, thirty to a drum.
				station(Mechs.AUTOCANNON_DRUM, 1, cost(Items.IRON_INGOT, 4), cost(Items.GUNPOWDER, 6)));
	}

	/**
	 * A block's item, straight from the registry: the Weapon Station's list is built during startup, before the
	 * mod's block items are linked to their blocks (so {@code asItem()} would still be air).
	 */
	private static Item blockItem(Block block) {
		return BuiltInRegistries.ITEM.getValue(BuiltInRegistries.BLOCK.getKey(block));
	}

	private static FoundryRecipe.Cost foundry(Item item, int count) {
		return new FoundryRecipe.Cost(item, count);
	}

	private static StationRecipe station(Item result, int count, StationRecipe.Cost... costs) {
		return new StationRecipe(result, count, List.of(costs));
	}

	private static StationRecipe.Cost cost(Item item, int count) {
		return new StationRecipe.Cost(item, count);
	}
}
