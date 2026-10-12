package com.jheinz.mymod.industry;

import java.util.List;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import com.jheinz.mymod.foundry.FoundryRecipe;
import com.jheinz.mymod.foundry.FoundryRecipe.Cost;

/**
 * The industry's Foundry recipes. Steel is iron carburised with coal; aluminium has to be reduced from bauxite at
 * a white heat, so it waits until the Foundry is up to {@link #ALUMINIUM_TEMPERATURE}; duralumin is aluminium
 * alloyed with a little copper; and oil shale retorted in a canister gives off enough crude to fill it.
 */
public final class IndustryRecipes {
	public static final int STEEL_TEMPERATURE = 800;
	public static final int ALUMINIUM_TEMPERATURE = 1400;
	public static final int DURALUMIN_TEMPERATURE = 1000;
	public static final int RETORT_TEMPERATURE = 400;

	private IndustryRecipes() {
	}

	public static List<FoundryRecipe> foundry() {
		return List.of(
				recipe(STEEL_TEMPERATURE, Industry.STEEL_INGOT, 1, 200, cost(Items.IRON_INGOT, 1), cost(Items.COAL, 1)),
				recipe(STEEL_TEMPERATURE, Industry.STEEL_INGOT, 1, 200, cost(Items.IRON_INGOT, 1), cost(Items.CHARCOAL, 1)),
				recipe(ALUMINIUM_TEMPERATURE, Industry.ALUMINIUM_INGOT, 1, 300, cost(Industry.BAUXITE, 2)),
				recipe(DURALUMIN_TEMPERATURE, Industry.DURALUMIN_INGOT, 4, 400, cost(Industry.ALUMINIUM_INGOT, 3), cost(Items.COPPER_INGOT, 1)),
				recipe(RETORT_TEMPERATURE, Industry.CRUDE_OIL_CANISTER, 1, 400, cost(Industry.SHALE_CHUNK, 4), cost(Industry.EMPTY_CANISTER, 1)));
	}

	private static FoundryRecipe recipe(int minTemperature, Item result, int count, int cookTicks, Cost... costs) {
		return new FoundryRecipe(result, count, cookTicks, List.of(costs)).withMinTemperature(minTemperature);
	}

	private static Cost cost(Item item, int count) {
		return new Cost(item, count);
	}
}
