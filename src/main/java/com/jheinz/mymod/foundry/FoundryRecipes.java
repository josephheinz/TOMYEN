package com.jheinz.mymod.foundry;

import java.util.List;

import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.jheinz.mymod.ModItems;
import com.jheinz.mymod.foundry.FoundryRecipe.Cost;

/** Everything the Foundry smelts. Built from code, so client and server always agree. */
public final class FoundryRecipes {
	public static final List<FoundryRecipe> ALL = List.of(
			// Silica from quartz, fluxed with sand.
			recipe(ModItems.SILICON, 1, 200, cost(Items.QUARTZ, 1), cost(Items.SAND, 1)));

	private FoundryRecipes() {
	}

	public static FoundryRecipe find(Container inputs) {
		for (FoundryRecipe recipe : ALL) {
			if (recipe.matches(inputs)) {
				return recipe;
			}
		}
		return null;
	}

	/** Whether any recipe uses this item, so shift-clicking it goes to the inputs. */
	public static boolean isIngredient(ItemStack stack) {
		return ALL.stream().anyMatch(recipe -> recipe.costs().stream().anyMatch(cost -> stack.is(cost.item())));
	}

	private static FoundryRecipe recipe(Item result, int count, int cookTicks, Cost... costs) {
		return new FoundryRecipe(result, count, cookTicks, List.of(costs));
	}

	private static Cost cost(Item item, int count) {
		return new Cost(item, count);
	}
}
