package com.jheinz.tomyen.tungsten;

import java.util.List;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import com.jheinz.tomyen.foundry.FoundryRecipe;
import com.jheinz.tomyen.foundry.FoundryRecipe.Cost;

/**
 * Tungsten's Foundry recipes. It has the highest melting point of any metal, so raw tungsten only gives up its ingot
 * once the Foundry is at {@link #INGOT_TEMPERATURE}, and slowly; carburising an ingot with coal (or charcoal) at
 * {@link #CARBIDE_TEMPERATURE} makes tungsten carbide.
 */
public final class TungstenRecipes {
	public static final int INGOT_TEMPERATURE = 1500;
	public static final int INGOT_TICKS = 600;
	public static final int CARBIDE_TEMPERATURE = 1400;
	public static final int CARBIDE_TICKS = 400;

	private TungstenRecipes() {
	}

	public static List<FoundryRecipe> foundry() {
		return List.of(
				recipe(INGOT_TEMPERATURE, Tungsten.TUNGSTEN_INGOT, 1, INGOT_TICKS, cost(Tungsten.RAW_TUNGSTEN, 1)),
				recipe(CARBIDE_TEMPERATURE, Tungsten.TUNGSTEN_CARBIDE, 1, CARBIDE_TICKS, cost(Tungsten.TUNGSTEN_INGOT, 1), cost(Items.COAL, 2)),
				recipe(CARBIDE_TEMPERATURE, Tungsten.TUNGSTEN_CARBIDE, 1, CARBIDE_TICKS, cost(Tungsten.TUNGSTEN_INGOT, 1), cost(Items.CHARCOAL, 2)));
	}

	private static FoundryRecipe recipe(int minTemperature, Item result, int count, int cookTicks, Cost... costs) {
		return new FoundryRecipe(result, count, cookTicks, List.of(costs)).withMinTemperature(minTemperature);
	}

	private static Cost cost(Item item, int count) {
		return new Cost(item, count);
	}
}
