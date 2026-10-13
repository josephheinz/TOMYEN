package com.jheinz.mymod.meteorite;

import java.util.List;

import net.minecraft.world.item.Item;

import com.jheinz.mymod.foundry.FoundryRecipe;
import com.jheinz.mymod.foundry.FoundryRecipe.Cost;

/**
 * The meteorite Foundry recipes. Meteoric iron is nickel-rich and only melts at a white heat,
 * {@link #METEORIC_IRON_TEMPERATURE}, from raw lumps or the ore itself; Star Steel is meteoric iron fused with star
 * crystal at the Foundry's hottest, {@link #STAR_STEEL_TEMPERATURE}.
 */
public final class MeteoriteRecipes {
	public static final int METEORIC_IRON_TEMPERATURE = 1300;
	public static final int STAR_STEEL_TEMPERATURE = 1500;

	private MeteoriteRecipes() {
	}

	public static List<FoundryRecipe> foundry() {
		return List.of(
				recipe(METEORIC_IRON_TEMPERATURE, Meteorites.METEORIC_IRON_INGOT, 1, 300, cost(Meteorites.RAW_METEORIC_IRON, 1)),
				recipe(METEORIC_IRON_TEMPERATURE, Meteorites.METEORIC_IRON_INGOT, 1, 300, cost(Meteorites.METEORIC_IRON_ORE.asItem(), 1)),
				recipe(STAR_STEEL_TEMPERATURE, Meteorites.STAR_STEEL_INGOT, 1, 400, cost(Meteorites.METEORIC_IRON_INGOT, 1),
						cost(Meteorites.STAR_CRYSTAL_SHARD, 2)));
	}

	private static FoundryRecipe recipe(int minTemperature, Item result, int count, int cookTicks, Cost... costs) {
		return new FoundryRecipe(result, count, cookTicks, List.of(costs)).withMinTemperature(minTemperature);
	}

	private static Cost cost(Item item, int count) {
		return new Cost(item, count);
	}
}
