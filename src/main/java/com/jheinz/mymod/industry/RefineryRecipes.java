package com.jheinz.mymod.industry;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.jheinz.mymod.industry.RefineryRecipe.Cost;
import com.jheinz.mymod.industry.RefineryRecipe.Output;

/** Everything the Refinery makes. Built from code, so client and server always agree. */
public final class RefineryRecipes {
	/** Fractional distillation: a canister of crude into kerosene, with rubber and tar left in the bottoms. */
	public static final RefineryRecipe DISTIL = new RefineryRecipe("distil", Industry.KEROSENE_CANISTER, 1,
			List.of(new Output(Industry.RUBBER, 1), new Output(Industry.TAR, 1)), 300, List.of(new Cost(Industry.CRUDE_OIL_CANISTER, 1)));
	/** Kerosene dosed with blaze powder and gunpowder as the oxidiser: rocket fuel. */
	public static final RefineryRecipe BLEND = new RefineryRecipe("blend", Industry.ROCKET_FUEL_CANISTER, 1, List.of(), 200,
			List.of(new Cost(Industry.KEROSENE_CANISTER, 1), new Cost(Items.BLAZE_POWDER, 1), new Cost(Items.GUNPOWDER, 2)));
	/** Kerosene jellied with rubber and tar into sticky napalm. */
	public static final RefineryRecipe THICKEN = new RefineryRecipe("thicken", Industry.NAPALM_CANISTER, 1, List.of(), 240,
			List.of(new Cost(Industry.KEROSENE_CANISTER, 1), new Cost(Industry.RUBBER, 2), new Cost(Industry.TAR, 1)));
	public static final List<RefineryRecipe> ALL = List.of(DISTIL, BLEND, THICKEN);

	private RefineryRecipes() {
	}

	public static @Nullable RefineryRecipe find(Container inputs) {
		for (RefineryRecipe recipe : ALL) {
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
}
