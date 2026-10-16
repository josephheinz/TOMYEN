package com.jheinz.tomyen.blueprint;

import java.util.List;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import com.jheinz.tomyen.ModItems;
import com.jheinz.tomyen.scrapbench.ScrapBench;
import com.jheinz.tomyen.station.StationRecipe;
import com.jheinz.tomyen.station.StationRecipe.Cost;

/**
 * The Weapon Station's blueprint recipes: Foundry guns and attachments out of iron, springs and screws instead of steel,
 * plate and chips. Locked (and shown greyed out) until the blueprint is read; the normal recipes stay as they are. The
 * Scrap Bench's blueprint recipes live in {@link com.jheinz.tomyen.scrapbench.BenchRecipes}.
 */
public final class BlueprintRecipes {
	private BlueprintRecipes() {
	}

	public static List<StationRecipe> station() {
		return List.of(
				// Whole guns in one go: no separate slide and frame when you're working from someone else's drawings.
				locked(Blueprints.M1911, cost(Items.IRON_INGOT, 6), cost(ScrapBench.SPRING, 2), cost(ScrapBench.SCREWS, 4)),
				locked(Blueprints.REVOLVER_357, cost(Items.IRON_INGOT, 6), cost(ScrapBench.SPRING, 1), cost(Items.LEATHER, 1),
						cost(Items.COPPER_NUGGET, 3)),
				// Tech scrap's salvaged electronics stand in for a computer chip and an optic lens.
				locked(Blueprints.RED_DOT_SIGHT, cost(ModItems.TECH_SCRAP, 2), cost(Items.GLASS_PANE, 2), cost(Items.REDSTONE, 2)),
				locked(Blueprints.SUPPRESSOR, cost(Items.IRON_INGOT, 3), cost(Items.WOOL.white(), 1), cost(ScrapBench.SCREWS, 2)),
				locked(Blueprints.MUZZLE_BRAKE, cost(Items.IRON_INGOT, 2), cost(Items.FLINT, 1), cost(ScrapBench.SCREWS, 2)),
				locked(Blueprints.VERTICAL_GRIP, cost(Items.IRON_INGOT, 1), cost(Items.STICK, 1), cost(ScrapBench.SCREWS, 1)));
	}

	private static StationRecipe locked(Blueprint blueprint, Cost... costs) {
		return new StationRecipe(blueprint.result(), 1, List.of(costs), blueprint.id());
	}

	private static Cost cost(Item item, int count) {
		return new Cost(item, count);
	}
}
