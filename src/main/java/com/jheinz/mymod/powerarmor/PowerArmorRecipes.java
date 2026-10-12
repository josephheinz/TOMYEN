package com.jheinz.mymod.powerarmor;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import com.jheinz.mymod.ModItems;
import com.jheinz.mymod.foundry.FoundryRecipe;
import com.jheinz.mymod.station.StationRecipe;

/**
 * The power armor recipes that live in code. The station, Mk I helmet, arms and legs, fusion core and plating
 * kits are crafting-table recipes (data/mymod/recipe); the rest:
 * <ul>
 * <li>Weapon Station: the Mk I torso, from iron blocks, tech scrap and pistons.
 * <li>Foundry: each upgrade, a piece of the mark below and plating kits (two for a torso, one otherwise) into
 * the next mark. The piece keeps its wear and, for a helmet, its Neuralink modules.
 * <li>Foundry: recharging a fusion core (any charge) with two redstone blocks.
 * </ul>
 */
public final class PowerArmorRecipes {
	private static final int MK2_COOK_TICKS = 600;
	private static final int MK3_COOK_TICKS = 900;
	private static final int RECHARGE_TICKS = 400;

	private PowerArmorRecipes() {
	}

	public static List<FoundryRecipe> foundry() {
		List<FoundryRecipe> recipes = new ArrayList<>();
		for (PowerArmorPiece piece : PowerArmorPiece.values()) {
			int kits = piece == PowerArmorPiece.TORSO ? 2 : 1;
			recipes.add(upgrade(piece, PowerArmorTier.MK1, PowerArmorTier.MK2, PowerArmors.MK2_PLATING_KIT, kits, MK2_COOK_TICKS));
			recipes.add(upgrade(piece, PowerArmorTier.MK2, PowerArmorTier.MK3, PowerArmors.MK3_PLATING_KIT, kits, MK3_COOK_TICKS));
		}
		recipes.add(new FoundryRecipe(PowerArmors.FUSION_CORE, 1, RECHARGE_TICKS,
				List.of(new FoundryRecipe.Cost(PowerArmors.FUSION_CORE, 1), new FoundryRecipe.Cost(Items.REDSTONE_BLOCK, 2))));
		return recipes;
	}

	private static FoundryRecipe upgrade(PowerArmorPiece piece, PowerArmorTier from, PowerArmorTier to, Item kit,
			int kits, int cookTicks) {
		return new FoundryRecipe(PowerArmors.piece(piece, to), 1, cookTicks,
				List.of(new FoundryRecipe.Cost(PowerArmors.piece(piece, from), 1), new FoundryRecipe.Cost(kit, kits)), PowerArmors.piece(piece, from));
	}

	public static List<StationRecipe> station() {
		return List.of(new StationRecipe(PowerArmors.piece(PowerArmorPiece.TORSO, PowerArmorTier.MK1), 1,
				List.of(new StationRecipe.Cost(Items.IRON_BLOCK, 4), new StationRecipe.Cost(ModItems.TECH_SCRAP, 4), new StationRecipe.Cost(Items.PISTON, 2))));
	}
}
