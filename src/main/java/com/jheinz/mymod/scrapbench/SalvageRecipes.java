package com.jheinz.mymod.scrapbench;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.jheinz.mymod.ModItems;
import com.jheinz.mymod.cathode.CathodeWalkers;
import com.jheinz.mymod.fishing.fish.Fishes;
import com.jheinz.mymod.scrapbench.SalvageRecipe.Output;
import com.jheinz.mymod.skullcrane.Skullcranes;
import com.jheinz.mymod.sodajetpack.SodaJetpacks;
import com.jheinz.mymod.tvhelmet.TvHelmets;

/**
 * What the Scrap Bench's salvage mode breaks down, in list order: fished-up junk and machine parts first (the bench's
 * reason to exist), then worn-out iron and copper gear and old hardware. Metal comes back as somewhat less than went in,
 * plus the springs and screws that held it together.
 */
public final class SalvageRecipes {
	public static final List<SalvageRecipe> ALL = List.of(
			// Junk and machine parts.
			salvage(Fishes.RUSTY_SCRAP, out(Items.IRON_NUGGET, 2), out(ScrapBench.SCREWS, 1)),
			salvage(Fishes.OLD_BOOT, out(Items.LEATHER, 1), out(ScrapBench.SCREWS, 1)),
			salvage(ModItems.TECH_SCRAP, out(ScrapBench.SPRING, 1), out(ScrapBench.SCREWS, 2), out(Items.COPPER_NUGGET, 1)),
			salvage(Skullcranes.SERVO_ARM, out(ScrapBench.SPRING, 2), out(ScrapBench.SCREWS, 2), out(ModItems.TECH_SCRAP, 1)),
			salvage(Skullcranes.OPTIC_LENS, out(Items.GLASS_PANE, 1), out(ScrapBench.SCREWS, 1), out(ModItems.TECH_SCRAP, 1)),
			salvage(CathodeWalkers.CATHODE_TUBE, out(Items.GLASS_PANE, 1), out(Items.COPPER_NUGGET, 3), out(ModItems.TECH_SCRAP, 1)),
			salvage(TvHelmets.TV_HELMET, out(CathodeWalkers.CATHODE_TUBE, 1), out(Items.COPPER_NUGGET, 4), out(ScrapBench.SCREWS, 1)),
			salvage(SodaJetpacks.SODA_JETPACK, out(Items.GLASS_BOTTLE, 2), out(Items.STRING, 2), out(Items.LEATHER, 1)),
			// Hardware.
			salvage(Items.RAIL, out(Items.IRON_NUGGET, 3)),
			salvage(Items.IRON_CHAIN, out(Items.IRON_NUGGET, 8)),
			salvage(Items.BUCKET, out(Items.IRON_INGOT, 2)),
			salvage(Items.MINECART, out(Items.IRON_INGOT, 3), out(ScrapBench.SCREWS, 2)),
			salvage(Items.TRIPWIRE_HOOK, out(Items.IRON_NUGGET, 2), out(ScrapBench.SPRING, 1)),
			salvage(Items.SHEARS, out(Items.IRON_NUGGET, 9), out(ScrapBench.SPRING, 1)),
			salvage(Items.FLINT_AND_STEEL, out(Items.IRON_NUGGET, 6), out(Items.FLINT, 1)),
			salvage(Items.PISTON, out(Items.IRON_NUGGET, 4), out(Items.REDSTONE, 1), out(ScrapBench.SPRING, 1)),
			salvage(Items.COMPASS, out(Items.IRON_INGOT, 3), out(Items.REDSTONE, 1)),
			salvage(Items.IRON_HORSE_ARMOR, out(Items.IRON_INGOT, 4), out(ScrapBench.SCREWS, 2)),
			// Iron tools and armor.
			salvage(Items.IRON_SWORD, out(Items.IRON_NUGGET, 12)),
			salvage(Items.IRON_SHOVEL, out(Items.IRON_NUGGET, 6)),
			salvage(Items.IRON_PICKAXE, out(Items.IRON_NUGGET, 18)),
			salvage(Items.IRON_AXE, out(Items.IRON_NUGGET, 18)),
			salvage(Items.IRON_HOE, out(Items.IRON_NUGGET, 12)),
			salvage(Items.IRON_HELMET, out(Items.IRON_INGOT, 3), out(ScrapBench.SCREWS, 1)),
			salvage(Items.IRON_CHESTPLATE, out(Items.IRON_INGOT, 5), out(ScrapBench.SCREWS, 2)),
			salvage(Items.IRON_LEGGINGS, out(Items.IRON_INGOT, 4), out(ScrapBench.SCREWS, 2)),
			salvage(Items.IRON_BOOTS, out(Items.IRON_INGOT, 2), out(ScrapBench.SCREWS, 1)),
			salvage(Items.CHAINMAIL_HELMET, out(Items.IRON_NUGGET, 10)),
			salvage(Items.CHAINMAIL_CHESTPLATE, out(Items.IRON_NUGGET, 16), out(ScrapBench.SPRING, 1)),
			salvage(Items.CHAINMAIL_LEGGINGS, out(Items.IRON_NUGGET, 14)),
			salvage(Items.CHAINMAIL_BOOTS, out(Items.IRON_NUGGET, 8)),
			// Copper tools and armor.
			salvage(Items.COPPER_SWORD, out(Items.COPPER_NUGGET, 12)),
			salvage(Items.COPPER_SHOVEL, out(Items.COPPER_NUGGET, 6)),
			salvage(Items.COPPER_PICKAXE, out(Items.COPPER_NUGGET, 18)),
			salvage(Items.COPPER_AXE, out(Items.COPPER_NUGGET, 18)),
			salvage(Items.COPPER_HOE, out(Items.COPPER_NUGGET, 12)),
			salvage(Items.COPPER_HELMET, out(Items.COPPER_INGOT, 3), out(ScrapBench.SCREWS, 1)),
			salvage(Items.COPPER_CHESTPLATE, out(Items.COPPER_INGOT, 5), out(ScrapBench.SCREWS, 2)),
			salvage(Items.COPPER_LEGGINGS, out(Items.COPPER_INGOT, 4), out(ScrapBench.SCREWS, 2)),
			salvage(Items.COPPER_BOOTS, out(Items.COPPER_INGOT, 2), out(ScrapBench.SCREWS, 1)));

	private static final Map<Item, SalvageRecipe> BY_INPUT = new HashMap<>();

	static {
		for (SalvageRecipe recipe : ALL) {
			if (BY_INPUT.put(recipe.input(), recipe) != null) {
				throw new IllegalStateException("Two salvage recipes for " + recipe.input());
			}
			if (recipe.outputs().size() > ScrapBenchMenu.OUTPUT_COUNT) {
				throw new IllegalStateException("Salvaging " + recipe.input() + " gives more stacks than the bench has slots");
			}
		}
	}

	private SalvageRecipes() {
	}

	public static void initialize() {
	}

	public static @Nullable SalvageRecipe find(ItemStack stack) {
		return stack.isEmpty() ? null : BY_INPUT.get(stack.getItem());
	}

	private static SalvageRecipe salvage(Item input, Output... outputs) {
		return new SalvageRecipe(input, List.of(outputs));
	}

	private static Output out(Item item, int count) {
		return new Output(item, count);
	}
}
