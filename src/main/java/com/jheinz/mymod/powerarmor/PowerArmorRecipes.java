package com.jheinz.mymod.powerarmor;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import com.jheinz.mymod.ModItems;
import com.jheinz.mymod.alien.Aliens;
import com.jheinz.mymod.foundry.FoundryRecipe;
import com.jheinz.mymod.station.StationRecipe;
import com.jheinz.mymod.uranium.Uranium;

/**
 * The power armor recipes that live in code. The station, Mk I helmet, arms and legs, fusion core and plating
 * kits are crafting-table recipes (data/mymod/recipe); the rest:
 * <ul>
 * <li>Weapon Station: the Mk I torso, from iron blocks, tech scrap and pistons.
 * <li>Foundry: each upgrade, a piece of the mark below and plating kits (two for a torso, one otherwise) into
 * the next mark. The piece keeps its wear and, for a helmet, its Neuralink modules.
 * <li>Foundry: recharging a fusion core: two redstone blocks put back {@link #REDSTONE_RECHARGE} charge (a third of
 * a full core), a piece of enriched uranium fills it right up.
 * <li>Foundry: an Alien Reactor from a downed UFO and four computer chips to run it make two fully charged fusion cores,
 * no redstone, glowstone or blaze rods needed.
 * </ul>
 */
public final class PowerArmorRecipes {
	private static final int MK2_COOK_TICKS = 600;
	private static final int MK3_COOK_TICKS = 900;
	private static final int RECHARGE_TICKS = 400;
	private static final int REACTOR_TICKS = 600;
	public static final int REDSTONE_RECHARGE = FusionCoreItem.MAX_CHARGE / 3;

	private PowerArmorRecipes() {
	}

	public static List<FoundryRecipe> foundry() {
		List<FoundryRecipe> recipes = new ArrayList<>();
		for (PowerArmorPiece piece : PowerArmorPiece.values()) {
			int kits = piece == PowerArmorPiece.TORSO ? 2 : 1;
			recipes.add(upgrade(piece, PowerArmorTier.MK1, PowerArmorTier.MK2, PowerArmors.MK2_PLATING_KIT, kits, MK2_COOK_TICKS));
			recipes.add(upgrade(piece, PowerArmorTier.MK2, PowerArmorTier.MK3, PowerArmors.MK3_PLATING_KIT, kits, MK3_COOK_TICKS));
		}
		recipes.add(recharge(Items.REDSTONE_BLOCK, 2, REDSTONE_RECHARGE));
		recipes.add(recharge(Uranium.ENRICHED_URANIUM, 1, FusionCoreItem.MAX_CHARGE));
		recipes.add(new FoundryRecipe(PowerArmors.FUSION_CORE, 2, REACTOR_TICKS,
				List.of(new FoundryRecipe.Cost(Aliens.ALIEN_REACTOR, 1), new FoundryRecipe.Cost(ModItems.COMPUTER_CHIP, 4))));
		return recipes;
	}

	/** A core (it keeps its charge) and {@code count} of {@code fuel} into the same core with {@code charge} more. */
	private static FoundryRecipe recharge(Item fuel, int count, int charge) {
		return new FoundryRecipe(PowerArmors.FUSION_CORE, 1, RECHARGE_TICKS,
				List.of(new FoundryRecipe.Cost(PowerArmors.FUSION_CORE, 1), new FoundryRecipe.Cost(fuel, count)), PowerArmors.FUSION_CORE)
				.withFinish(core -> {
					FusionCoreItem.setCharge(core, FusionCoreItem.charge(core) + charge);
					return core;
				});
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
