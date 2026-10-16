package com.jheinz.tomyen.bloomery;

import java.util.List;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import com.jheinz.tomyen.ModItems;
import com.jheinz.tomyen.foundry.FoundryRecipe;
import com.jheinz.tomyen.gunsmith.Gunsmith;
import com.jheinz.tomyen.industry.Industry;
import com.jheinz.tomyen.industry.IndustryRecipes;
import com.jheinz.tomyen.station.StationRecipe;

/**
 * Where bloom steel stands in for real steel, and how the Foundry finishes it.
 * <p>
 * <b>The rule:</b> a bloom steel version of a steel recipe makes the same thing, but takes {@link #premium} bloom steel
 * for the steel: half as much again, rounded up, and always at least one ingot more (1 becomes 2, 2 becomes 3, 3
 * becomes 5). Everything else in the recipe is unchanged. Only the simple, chip-free steel work gets a version: the
 * revolver, the M1911 and the Glock (frame and slide or cylinder), four attachments, and, as crafting recipes
 * (data/tomyen/recipe, tools/gen_bloomery.py), the steel drill bit and the steel fishing rod. Anything that needs steel
 * plate, duralumin, chips or rubber stays the Foundry's.
 * <p>
 * The Weapon Station matches by ingredient set, so each version's set swaps steel for bloom steel and is new: no
 * steel recipe takes bloom steel. StationRecipes' startup check would catch a clash.
 */
public final class BloomSteelRecipes {
	/** Reworking bloom steel in the Foundry: it's already been through a fire, so it's quicker than iron. */
	public static final int REFINE_TICKS = 100;

	private BloomSteelRecipes() {
	}

	/** How much bloom steel stands in for this much steel. */
	public static int premium(int steel) {
		return Math.max(steel + 1, (steel * 3 + 1) / 2);
	}

	public static List<StationRecipe> station() {
		return List.of(
				// .357 revolver: frame and cylinder.
				recipe(ModItems.REVOLVER_FRAME, steel(2), cost(Items.TRIPWIRE_HOOK, 1), cost(Items.LEATHER, 1)),
				recipe(ModItems.REVOLVER_CYLINDER, steel(2), cost(Items.COPPER_NUGGET, 3)),
				// M1911: slide and frame.
				recipe(ModItems.M1911_SLIDE, steel(2), cost(Items.IRON_NUGGET, 1), cost(Items.GOLD_NUGGET, 1)),
				recipe(ModItems.M1911_FRAME, steel(2), cost(Items.TRIPWIRE_HOOK, 1), cost(Items.COPPER_NUGGET, 1)),
				// Glock: its burst group is mechanical, so no chip.
				recipe(ModItems.GLOCK_SLIDE, steel(2), cost(Items.INK_SAC, 1)),
				recipe(ModItems.GLOCK_FRAME, cost(Items.INK_SAC, 2), cost(Items.TRIPWIRE_HOOK, 1), steel(1)),
				// Attachments turned from bar stock.
				recipe(ModItems.MUZZLE_BRAKE, steel(2), cost(Items.FLINT, 1)),
				recipe(ModItems.SUPPRESSOR, steel(3), cost(Items.WOOL.white(), 1), cost(Items.INK_SAC, 1)),
				recipe(ModItems.VERTICAL_GRIP, steel(1), cost(Items.STICK, 1), cost(Items.INK_SAC, 1)),
				recipe(Gunsmith.SHOTGUN_CHOKE, steel(1), cost(Items.IRON_NUGGET, 3)));
	}

	/** Bloom steel and coal or charcoal in a Foundry: real steel, at steel's temperature and in half the time. */
	public static List<FoundryRecipe> foundry() {
		return List.of(refine(Items.COAL), refine(Items.CHARCOAL));
	}

	private static FoundryRecipe refine(Item carbon) {
		return new FoundryRecipe(Industry.STEEL_INGOT, 1, REFINE_TICKS,
				List.of(new FoundryRecipe.Cost(Bloomery.SET.ingot, 1), new FoundryRecipe.Cost(carbon, 1)))
				.withMinTemperature(IndustryRecipes.STEEL_TEMPERATURE);
	}

	/** Bloom steel standing in for this much steel. */
	private static StationRecipe.Cost steel(int steel) {
		return cost(Bloomery.SET.ingot, premium(steel));
	}

	private static StationRecipe recipe(Item result, StationRecipe.Cost... costs) {
		return new StationRecipe(result, 1, List.of(costs));
	}

	private static StationRecipe.Cost cost(Item item, int count) {
		return new StationRecipe.Cost(item, count);
	}
}
