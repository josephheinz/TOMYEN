package com.jheinz.mymod.foundry;

import java.util.List;
import java.util.stream.Stream;

import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.jheinz.mymod.ModItems;
import com.jheinz.mymod.foundry.FoundryRecipe.Cost;
import com.jheinz.mymod.froststeel.Froststeel;
import com.jheinz.mymod.industry.IndustryRecipes;
import com.jheinz.mymod.magmasteel.Magmasteel;
import com.jheinz.mymod.meteorite.MeteoriteRecipes;
import com.jheinz.mymod.powerarmor.PowerArmorRecipes;
import com.jheinz.mymod.soul.SoulSteel;
import com.jheinz.mymod.tungsten.TungstenRecipes;
import com.jheinz.mymod.voidsteel.Voidsteel;

/** Everything the Foundry smelts. Built from code, so client and server always agree. */
public final class FoundryRecipes {
	public static final List<FoundryRecipe> ALL = Stream.of(
			List.of(
					// Silica from quartz, fluxed with sand.
					recipe(ModItems.SILICON, 1, 200, cost(Items.QUARTZ, 1), cost(Items.SAND, 1)),
					// A Neuralink fused with a bank of extra chips: five sockets, and its modules stay installed.
					new FoundryRecipe(ModItems.NEURALINK_MK2, 1, 400, List.of(cost(ModItems.NEURALINK, 1), cost(ModItems.COMPUTER_CHIP, 6)),
							ModItems.NEURALINK)),
			// Power armor upgrades and fusion core recharging.
			PowerArmorRecipes.foundry(),
			// Steel, aluminium, duralumin and shale oil for the oil industry.
			IndustryRecipes.foundry(),
			// Tungsten and its carbide.
			TungstenRecipes.foundry(),
			// Meteoric iron and Star Steel.
			MeteoriteRecipes.foundry(),
			// Magmasteel (iron and a magma block) and soul steel (steel and Soul Charges).
			// Magmasteel (iron and a magma block), soul steel (steel and Soul Charges), froststeel (iron quenched with
			// blue ice, kept cool) and voidsteel (steel and Void Flux).
			Magmasteel.foundry(),
			SoulSteel.foundry(),
			Froststeel.foundry(),
			Voidsteel.foundry()).flatMap(List::stream).toList();

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
