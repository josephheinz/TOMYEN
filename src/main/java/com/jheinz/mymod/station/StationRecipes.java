package com.jheinz.mymod.station;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import com.jheinz.mymod.ModItems;
import com.jheinz.mymod.bloomery.BloomSteelRecipes;
import com.jheinz.mymod.blueprint.BlueprintRecipes;
import com.jheinz.mymod.energy.EnergyWeapons;
import com.jheinz.mymod.fishgun.FishGuns;
import com.jheinz.mymod.leafblower.LeafBlowers;
import com.jheinz.mymod.gunsmith.Gunsmith;
import com.jheinz.mymod.industry.Industry;
import com.jheinz.mymod.launcher.Launchers;
import com.jheinz.mymod.mech.MechRecipes;
import com.jheinz.mymod.minigun.Miniguns;
import com.jheinz.mymod.napalm.Napalm;
import com.jheinz.mymod.powerarmor.PowerArmorRecipes;
import com.jheinz.mymod.silver.Silver;
import com.jheinz.mymod.skullcrane.Skullcranes;
import com.jheinz.mymod.station.StationRecipe.Cost;
import com.jheinz.mymod.tungsten.Tungsten;
import com.jheinz.mymod.uranium.Uranium;

/** Everything the Weapon Station can make. Built from code, so client and server always agree. */
public final class StationRecipes {
	public static final List<StationRecipe> ALL = Stream.concat(Stream.of(
			recipe(ModItems.BULLET_CASING_556, 8, cost(Items.COPPER_NUGGET, 2), cost(Items.GOLD_NUGGET, 1)),
			recipe(ModItems.BULLET_556_FMJ, 4,
					cost(ModItems.BULLET_CASING_556, 4), cost(Items.GUNPOWDER, 1), cost(Items.COPPER_NUGGET, 1)),
			recipe(ModItems.BULLET_556_GREEN_TIP, 4,
					cost(ModItems.BULLET_CASING_556, 4), cost(Items.GUNPOWDER, 1), cost(Items.IRON_NUGGET, 1)),
			// A hardened steel penetrator core (tungsten carbide is the step up, below).
			recipe(ModItems.BULLET_556_AP, 8,
					cost(ModItems.BULLET_CASING_556, 8), cost(Items.GUNPOWDER, 2), cost(Industry.STEEL_INGOT, 1)),
			// Soft gold for the expanding tip.
			recipe(ModItems.BULLET_556_HOLLOW_POINT, 4,
					cost(ModItems.BULLET_CASING_556, 4), cost(Items.GUNPOWDER, 1), cost(Items.GOLD_NUGGET, 1)),
			// Silver for the evil; holy silver too, or consecrate silver rounds by dropping them in crucifix light.
			recipe(ModItems.BULLET_556_SILVER, 4,
					cost(ModItems.BULLET_CASING_556, 4), cost(Items.GUNPOWDER, 1), cost(Silver.SILVER_NUGGET, 1)),
			recipe(ModItems.BULLET_556_HOLY_SILVER, 4,
					cost(ModItems.BULLET_CASING_556, 4), cost(Items.GUNPOWDER, 1), cost(Silver.HOLY_SILVER_NUGGET, 1)),
			// A real tungsten carbide penetrator, and a depleted uranium one.
			recipe(ModItems.BULLET_556_TUNGSTEN, 8,
					cost(ModItems.BULLET_CASING_556, 8), cost(Items.GUNPOWDER, 2), cost(Tungsten.TUNGSTEN_CARBIDE, 1)),
			recipe(ModItems.BULLET_556_DEPLETED_URANIUM, 8,
					cost(ModItems.BULLET_CASING_556, 8), cost(Items.GUNPOWDER, 2), cost(Uranium.DEPLETED_URANIUM, 1)),
			// 9mm: smaller cases, less powder per round.
			recipe(ModItems.BULLET_CASING_9MM, 12, cost(Items.COPPER_INGOT, 1), cost(Items.GOLD_NUGGET, 1)),
			recipe(ModItems.BULLET_9MM_FMJ, 8,
					cost(ModItems.BULLET_CASING_9MM, 8), cost(Items.GUNPOWDER, 1), cost(Items.COPPER_NUGGET, 1)),
			recipe(ModItems.BULLET_9MM_HOLLOW_POINT, 8,
					cost(ModItems.BULLET_CASING_9MM, 8), cost(Items.GUNPOWDER, 1), cost(Items.GOLD_NUGGET, 1)),
			// 7.62x39: copper-washed steel cases, like surplus Russian ammo.
			recipe(ModItems.BULLET_CASING_762, 8, cost(Items.IRON_NUGGET, 2), cost(Items.COPPER_NUGGET, 1)),
			recipe(ModItems.BULLET_762_FMJ, 4,
					cost(ModItems.BULLET_CASING_762, 4), cost(Items.GUNPOWDER, 1), cost(Items.COPPER_NUGGET, 1)),
			recipe(ModItems.BULLET_762_AP, 8,
					cost(ModItems.BULLET_CASING_762, 8), cost(Items.GUNPOWDER, 2), cost(Industry.STEEL_INGOT, 1)),
			// .45 ACP: fat straight brass.
			recipe(ModItems.BULLET_CASING_45, 10,
					cost(Items.COPPER_INGOT, 1), cost(Items.GOLD_NUGGET, 1), cost(Items.IRON_NUGGET, 1)),
			recipe(ModItems.BULLET_45_FMJ, 8,
					cost(ModItems.BULLET_CASING_45, 8), cost(Items.GUNPOWDER, 1), cost(Items.COPPER_NUGGET, 1)),
			recipe(ModItems.BULLET_45_HOLLOW_POINT, 8,
					cost(ModItems.BULLET_CASING_45, 8), cost(Items.GUNPOWDER, 1), cost(Items.GOLD_NUGGET, 1)),
			// .357 Magnum: long revolver brass, more powder per round.
			recipe(ModItems.BULLET_CASING_357, 8, cost(Items.COPPER_INGOT, 1), cost(Items.COPPER_NUGGET, 2)),
			recipe(ModItems.BULLET_357_FMJ, 6,
					cost(ModItems.BULLET_CASING_357, 6), cost(Items.GUNPOWDER, 1), cost(Items.COPPER_NUGGET, 1)),
			recipe(ModItems.BULLET_357_HOLLOW_POINT, 6,
					cost(ModItems.BULLET_CASING_357, 6), cost(Items.GUNPOWDER, 1), cost(Items.GOLD_NUGGET, 1)),
			// .338 Lapua Magnum: big, expensive, and powder-hungry.
			recipe(ModItems.BULLET_CASING_338, 6, cost(Items.COPPER_INGOT, 1), cost(Items.GOLD_INGOT, 1)),
			recipe(ModItems.BULLET_338_FMJ, 4,
					cost(ModItems.BULLET_CASING_338, 4), cost(Items.GUNPOWDER, 2), cost(Items.COPPER_NUGGET, 1)),
			recipe(ModItems.BULLET_338_AP, 4,
					cost(ModItems.BULLET_CASING_338, 4), cost(Items.GUNPOWDER, 2), cost(Industry.STEEL_INGOT, 1)),
			recipe(ModItems.STANAG_MAGAZINE, 1, cost(Items.IRON_NUGGET, 7)),
			recipe(ModItems.GLOCK_MAGAZINE, 1, cost(Items.IRON_NUGGET, 3), cost(Items.INK_SAC, 1)),
			recipe(ModItems.AK_MAGAZINE, 1, cost(Items.IRON_INGOT, 1), cost(Items.IRON_NUGGET, 4)),
			recipe(ModItems.GLOCK_DRUM_MAGAZINE, 1,
					cost(Items.IRON_INGOT, 2), cost(Items.IRON_NUGGET, 4), cost(Items.INK_SAC, 1)),
			// Copper for the drum's wound feed spring.
			recipe(ModItems.AK_DRUM_MAGAZINE, 1,
					cost(Items.IRON_INGOT, 3), cost(Items.IRON_NUGGET, 4), cost(Items.COPPER_INGOT, 1)),
			recipe(ModItems.AWP_MAGAZINE, 1, cost(Items.IRON_INGOT, 2), cost(Items.STRING, 1)),
			recipe(ModItems.GALIL_MAGAZINE, 1, cost(Items.IRON_NUGGET, 6), cost(Items.STICK, 1)),
			recipe(ModItems.SPEEDLOADER_357, 1, cost(Items.IRON_NUGGET, 3), cost(Items.LEATHER, 1)),
			recipe(ModItems.MP5_MAGAZINE, 1, cost(Items.IRON_NUGGET, 6), cost(Items.TRIPWIRE_HOOK, 1)),
			recipe(ModItems.MAC10_MAGAZINE, 1, cost(Items.IRON_NUGGET, 6), cost(Items.COAL, 1)),
			recipe(ModItems.M1911_MAGAZINE, 1, cost(Items.IRON_NUGGET, 3), cost(Items.GOLD_NUGGET, 1)),
			// Modern guns are Foundry-tier: steel for barrels and actions, stamped steel plate for receivers, duralumin sheet
			// for the light alloy parts, and a computer chip in every full-auto (or burst) trigger group. Pistols cost a few
			// steel ingots, rifles and shotguns about twice that, the M249 and the RPG-7 the most.
			// M4: forged aluminium receivers (duralumin), a steel barrel; the lower holds the full-auto group.
			recipe(ModItems.M4_UPPER_RECEIVER, 1, cost(Industry.DURALUMIN_SHEET, 2), cost(Industry.STEEL_INGOT, 3)),
			recipe(ModItems.M4_LOWER_RECEIVER, 1, cost(Industry.DURALUMIN_SHEET, 1), cost(ModItems.COMPUTER_CHIP, 1), cost(Items.TRIPWIRE_HOOK, 1)),
			recipe(ModItems.M4, 1, cost(ModItems.M4_UPPER_RECEIVER, 1), cost(ModItems.M4_LOWER_RECEIVER, 1)),
			// Ink sacs stand in for the Glock's black polymer and nitride finish; its burst group is mechanical.
			recipe(ModItems.GLOCK_SLIDE, 1, cost(Industry.STEEL_INGOT, 2), cost(Items.INK_SAC, 1)),
			recipe(ModItems.GLOCK_FRAME, 1, cost(Items.INK_SAC, 2), cost(Items.TRIPWIRE_HOOK, 1), cost(Industry.STEEL_INGOT, 1)),
			recipe(ModItems.GLOCK, 1, cost(ModItems.GLOCK_SLIDE, 1), cost(ModItems.GLOCK_FRAME, 1)),
			// AKM: a stamped steel receiver around the full-auto group; sticks for the wood furniture.
			recipe(ModItems.AK_RECEIVER, 1, cost(Industry.STEEL_PLATE, 2), cost(ModItems.COMPUTER_CHIP, 1), cost(Items.STICK, 2)),
			recipe(ModItems.AK_BARREL, 1, cost(Industry.STEEL_INGOT, 3), cost(Items.STICK, 2)),
			recipe(ModItems.AKM, 1, cost(ModItems.AK_RECEIVER, 1), cost(ModItems.AK_BARREL, 1)),
			// AWP: a Skullcrane's optic lens becomes the scope; a duralumin skeleton and slime-ball polymer make the stock.
			recipe(ModItems.AWP_ACTION, 1, cost(Industry.STEEL_INGOT, 5), cost(Skullcranes.OPTIC_LENS, 1), cost(Items.TRIPWIRE_HOOK, 1)),
			recipe(ModItems.AWP_STOCK, 1, cost(Industry.DURALUMIN_SHEET, 2), cost(Items.SLIME_BALL, 2)),
			recipe(ModItems.AWP, 1, cost(ModItems.AWP_ACTION, 1), cost(ModItems.AWP_STOCK, 1)),
			recipe(ModItems.GALIL_RECEIVER, 1, cost(Industry.STEEL_INGOT, 3), cost(ModItems.COMPUTER_CHIP, 1), cost(Items.TRIPWIRE_HOOK, 1)),
			recipe(ModItems.GALIL_BARREL, 1, cost(Industry.STEEL_INGOT, 2), cost(Industry.STEEL_PLATE, 1), cost(Items.STICK, 1)),
			recipe(ModItems.GALIL, 1, cost(ModItems.GALIL_RECEIVER, 1), cost(ModItems.GALIL_BARREL, 1)),
			// Revolver: forged steel frame and cylinder, leather-wrapped grip. No electronics in a double-action.
			recipe(ModItems.REVOLVER_FRAME, 1, cost(Industry.STEEL_INGOT, 2), cost(Items.TRIPWIRE_HOOK, 1), cost(Items.LEATHER, 1)),
			recipe(ModItems.REVOLVER_CYLINDER, 1, cost(Industry.STEEL_INGOT, 2), cost(Items.COPPER_NUGGET, 3)),
			recipe(ModItems.REVOLVER_357, 1, cost(ModItems.REVOLVER_FRAME, 1), cost(ModItems.REVOLVER_CYLINDER, 1)),
			// MP5: stamped steel receiver; the trigger pack (full-auto and burst) carries the chip.
			recipe(ModItems.MP5_RECEIVER, 1, cost(Industry.STEEL_PLATE, 2), cost(Items.TRIPWIRE_HOOK, 1), cost(Items.INK_SAC, 1)),
			recipe(ModItems.MP5_LOWER, 1, cost(Industry.STEEL_INGOT, 2), cost(ModItems.COMPUTER_CHIP, 1), cost(Items.COAL, 2)),
			recipe(ModItems.MP5, 1, cost(ModItems.MP5_RECEIVER, 1), cost(ModItems.MP5_LOWER, 1)),
			// MAC-10: a box of stamped steel plate; the 1200 rpm sear lives in the lower.
			recipe(ModItems.MAC10_UPPER, 1, cost(Industry.STEEL_PLATE, 2), cost(Items.COAL, 1)),
			recipe(ModItems.MAC10_LOWER, 1, cost(Industry.STEEL_PLATE, 1), cost(ModItems.COMPUTER_CHIP, 1), cost(Items.TRIPWIRE_HOOK, 1)),
			recipe(ModItems.MAC10, 1, cost(ModItems.MAC10_UPPER, 1), cost(ModItems.MAC10_LOWER, 1)),
			recipe(ModItems.M1911_SLIDE, 1, cost(Industry.STEEL_INGOT, 2), cost(Items.IRON_NUGGET, 1), cost(Items.GOLD_NUGGET, 1)),
			recipe(ModItems.M1911_FRAME, 1, cost(Industry.STEEL_INGOT, 2), cost(Items.TRIPWIRE_HOOK, 1), cost(Items.COPPER_NUGGET, 1)),
			recipe(ModItems.M1911, 1, cost(ModItems.M1911_SLIDE, 1), cost(ModItems.M1911_FRAME, 1)),
			// M249: a stamped steel receiver with a piston for the belt feed's pawls and two chips for the fire control; the
			// barrel group's bipod and carry handle are duralumin, with a rubber grip.
			recipe(ModItems.M249_RECEIVER, 1, cost(Industry.STEEL_PLATE, 4), cost(ModItems.COMPUTER_CHIP, 2), cost(Items.PISTON, 1)),
			recipe(ModItems.M249_BARREL, 1, cost(Industry.STEEL_INGOT, 6), cost(Industry.DURALUMIN_SHEET, 2), cost(Industry.RUBBER, 1)),
			recipe(ModItems.M249, 1, cost(ModItems.M249_RECEIVER, 1), cost(ModItems.M249_BARREL, 1)),
			// An olive drab pouch with a steel frame.
			recipe(ModItems.M249_AMMO_BOX, 1, cost(Items.IRON_INGOT, 1), cost(Items.IRON_NUGGET, 4), cost(Items.DYE.green(), 1)),
			// Remington 870: dark oak for the walnut stock and fore-end.
			recipe(ModItems.REMINGTON_870_RECEIVER, 1, cost(Industry.STEEL_INGOT, 3), cost(Items.TRIPWIRE_HOOK, 1), cost(Items.DARK_OAK_LOG, 1)),
			recipe(ModItems.REMINGTON_870_BARREL, 1, cost(Industry.STEEL_INGOT, 2), cost(Industry.STEEL_PLATE, 1), cost(Items.DARK_OAK_PLANKS, 1)),
			recipe(ModItems.REMINGTON_870, 1, cost(ModItems.REMINGTON_870_RECEIVER, 1), cost(ModItems.REMINGTON_870_BARREL, 1)),
			// SPAS-12: a stamped receiver blackened with dye, and iron bars become the perforated heat shield.
			recipe(ModItems.SPAS12_RECEIVER, 1, cost(Industry.STEEL_PLATE, 3), cost(Items.TRIPWIRE_HOOK, 1), cost(Items.DYE.black(), 2)),
			recipe(ModItems.SPAS12_BARREL, 1, cost(Industry.STEEL_INGOT, 3), cost(Items.IRON_BARS, 2), cost(Items.DYE.black(), 1)),
			recipe(ModItems.SPAS12, 1, cost(ModItems.SPAS12_RECEIVER, 1), cost(ModItems.SPAS12_BARREL, 1)),
			// RPG-7: a rolled steel plate tube with stick-and-plank heat guards; a Skullcrane's optic lens becomes the PGO-7
			// optic, with a rubber eyecup.
			recipe(Launchers.RPG7_LAUNCH_TUBE, 1, cost(Industry.STEEL_PLATE, 6), cost(Items.STICK, 4), cost(Items.OAK_PLANKS, 2)),
			recipe(Launchers.RPG7_TRIGGER_GROUP, 1, cost(Industry.STEEL_INGOT, 4), cost(Skullcranes.OPTIC_LENS, 1), cost(Industry.RUBBER, 1)),
			recipe(Launchers.RPG7, 1, cost(Launchers.RPG7_LAUNCH_TUBE, 1), cost(Launchers.RPG7_TRIGGER_GROUP, 1)),
			// M2 flamethrower: steel plate tanks and their hose from empty canisters and rubber; the wand's igniter is a flint
			// and steel, its valve trigger a tripwire hook.
			recipe(Napalm.FLAMETHROWER_TANKS, 1, cost(Industry.STEEL_PLATE, 4), cost(Industry.EMPTY_CANISTER, 2), cost(Industry.RUBBER, 2)),
			recipe(Napalm.FLAMETHROWER_WAND, 1, cost(Industry.STEEL_INGOT, 3), cost(Items.FLINT_AND_STEEL, 1), cost(Items.TRIPWIRE_HOOK, 1)),
			recipe(Napalm.FLAMETHROWER, 1, cost(Napalm.FLAMETHROWER_TANKS, 1), cost(Napalm.FLAMETHROWER_WAND, 1)),
			// Attachments. Optics need a Skullcrane's optic lens and a computer chip, lasers a chip. Wool packs the
			// suppressor's baffles; ink sacs blacken it.
			recipe(ModItems.SUPPRESSOR, 1, cost(Industry.STEEL_INGOT, 3), cost(Items.WOOL.white(), 1), cost(Items.INK_SAC, 1)),
			recipe(ModItems.LASER_SIGHT, 1, cost(ModItems.COMPUTER_CHIP, 1), cost(Items.IRON_NUGGET, 3), cost(Items.GLASS_PANE, 1)),
			recipe(ModItems.RED_DOT_SIGHT, 1, cost(Industry.STEEL_INGOT, 1), cost(Skullcranes.OPTIC_LENS, 1), cost(ModItems.COMPUTER_CHIP, 1)),
			// An oil filter, as the name says: paper element, slime to seal it to the adapter. Junk, so still plain iron.
			recipe(ModItems.OIL_CAN_SUPPRESSOR, 1, cost(Items.IRON_INGOT, 2), cost(Items.PAPER, 2), cost(Items.SLIME_BALL, 1)),
			recipe(ModItems.MUZZLE_BRAKE, 1, cost(Industry.STEEL_INGOT, 2), cost(Items.FLINT, 1)),
			recipe(ModItems.FLASH_HIDER, 1, cost(Industry.STEEL_PLATE, 1), cost(Items.FLINT, 1)),
			recipe(ModItems.GREEN_LASER_SIGHT, 1, cost(ModItems.COMPUTER_CHIP, 1), cost(Items.EMERALD, 1), cost(Items.IRON_NUGGET, 3)),
			// Glowstone for the hologram; it takes more electronics than a simple red dot.
			recipe(ModItems.HOLOGRAPHIC_SIGHT, 1, cost(ModItems.COMPUTER_CHIP, 2), cost(Skullcranes.OPTIC_LENS, 1), cost(Items.GLOWSTONE_DUST, 1)),
			// Copper housing in place of the red dot's steel.
			recipe(ModItems.KOBRA_SIGHT, 1, cost(ModItems.COMPUTER_CHIP, 1), cost(Skullcranes.OPTIC_LENS, 1), cost(Items.COPPER_INGOT, 1)),
			// Ink sacs blacken the polymer; the angled grip is light duralumin, the stubby one rubber.
			recipe(ModItems.VERTICAL_GRIP, 1, cost(Industry.STEEL_INGOT, 1), cost(Items.STICK, 1), cost(Items.INK_SAC, 1)),
			recipe(ModItems.ANGLED_GRIP, 1, cost(Industry.DURALUMIN_SHEET, 1), cost(Items.INK_SAC, 1), cost(Items.LEATHER, 1)),
			recipe(ModItems.STUBBY_GRIP, 1, cost(Industry.STEEL_INGOT, 1), cost(Industry.RUBBER, 1), cost(Items.IRON_NUGGET, 2)),
			recipe(ModItems.HANDSTOP, 1, cost(Industry.STEEL_PLATE, 1), cost(Items.STICK, 1), cost(Items.LEATHER, 1)),
			// An auto sear needs a timing chip.
			recipe(ModItems.GLOCK_SWITCH, 1, cost(Items.IRON_NUGGET, 4), cost(ModItems.COMPUTER_CHIP, 1), cost(Items.TRIPWIRE_HOOK, 1)),
			// The homing guidance module: a Neuralink Smart-Link's targeting rebuilt as a seeker pod, with diamond optics and
			// a stack of computer chips. Dearer than any gun.
			recipe(Launchers.HOMING_GUIDANCE_MODULE, 1, cost(Items.DIAMOND, 4), cost(ModItems.COMPUTER_CHIP, 4),
					cost(ModItems.SMART_LINK_MODULE, 1)),
			recipe(ModItems.PLATE_CARRIER, 1, cost(Items.LEATHER, 5), cost(Items.STRING, 2), cost(Items.IRON_NUGGET, 2)),
			// Soft panel: woven string standing in for aramid fiber.
			recipe(ModItems.ARMOR_PLATE_LEVEL_1, 1, cost(Items.STRING, 6), cost(Items.LEATHER, 2)),
			// Leather strap backing keeps this from sharing the upper receiver's all-iron inputs.
			recipe(ModItems.ARMOR_PLATE_LEVEL_2, 1, cost(Items.IRON_INGOT, 5), cost(Items.LEATHER, 1)),
			// Ceramic strike face on a steel backer.
			recipe(ModItems.ARMOR_PLATE_LEVEL_3, 1, cost(Items.CLAY_BALL, 4), cost(Items.IRON_INGOT, 2), cost(Items.DIAMOND, 1))),
			// The Mk I power armor torso, the Bulwark Walker's weapons, smoke launcher and autocannon ammunition, the Minigun and
			// its parts, and the energy weapons.
			Stream.of(PowerArmorRecipes.station(), MechRecipes.station(), Miniguns.station(), EnergyWeapons.stationRecipes(),
					Gunsmith.stationRecipes(), BloomSteelRecipes.station(), BlueprintRecipes.station(),
					FishGuns.stationRecipes(), LeafBlowers.stationRecipes())
					.flatMap(List::stream)).toList();

	static {
		// Matching is by ingredient set, so two recipes with the same set would make the later one uncraftable.
		Set<Set<Item>> seen = new HashSet<>();
		for (StationRecipe recipe : ALL) {
			Set<Item> ingredients = recipe.costs().stream().map(StationRecipe.Cost::item).collect(Collectors.toSet());
			if (!seen.add(ingredients)) {
				throw new IllegalStateException("Weapon Station recipes share ingredients " + ingredients
						+ "; " + recipe.resultItem() + " could never be crafted");
			}
		}
	}

	private StationRecipes() {
	}

	/** Loads the list at startup so the duplicate check above fails fast. */
	public static void initialize() {
	}

	/** The recipe the inputs make for this player: blueprint recipes they haven't learned don't count. */
	public static StationRecipe find(Container inputs, Player player) {
		StationRecipe recipe = find(inputs);
		return recipe != null && recipe.unlockedFor(player) ? recipe : null;
	}

	public static StationRecipe find(Container inputs) {
		for (StationRecipe recipe : ALL) {
			if (recipe.matches(inputs)) {
				return recipe;
			}
		}
		return null;
	}

	private static StationRecipe recipe(Item result, int count, Cost... costs) {
		return new StationRecipe(result, count, List.of(costs));
	}

	private static Cost cost(Item item, int count) {
		return new Cost(item, count);
	}
}
