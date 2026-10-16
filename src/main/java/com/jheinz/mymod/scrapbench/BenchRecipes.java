package com.jheinz.mymod.scrapbench;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import com.jheinz.mymod.ModItems;
import com.jheinz.mymod.blueprint.Blueprint;
import com.jheinz.mymod.blueprint.Blueprints;
import com.jheinz.mymod.detector.Detectors;
import com.jheinz.mymod.scrapbench.BenchRecipe.Cost;
import com.jheinz.mymod.shoddy.ShoddyGuns;
import com.jheinz.mymod.skullcrane.Skullcranes;
import com.jheinz.mymod.survival.Survival;

/**
 * Everything the Scrap Bench assembles, in list order. Built from code, so client and server always agree. The homemade
 * guns and a few survival-kit pieces live here rather than on the crafting table: the bench is cheap (one iron ingot and a
 * crafting table), so nothing is stranded behind it. The last four are blueprint recipes: Weapon Station guns, a tier early.
 */
public final class BenchRecipes {
	public static final List<BenchRecipe> ALL = List.of(
			// Components: springs wound round a stick, screws cut from nuggets (salvaging junk gives both for free).
			recipe(ScrapBench.SPRING, 2, cost(Items.IRON_NUGGET, 4), cost(Items.STICK, 1)),
			recipe(ScrapBench.SCREWS, 4, cost(Items.IRON_NUGGET, 2)),
			// The homemade guns. A spring for the pistol's striker, screws to hold the rifle's bolt, a Skullcrane servo
			// arm to cycle the repeater.
			recipe(ShoddyGuns.PIPE_PISTOL, 1, cost(Items.IRON_INGOT, 2), cost(ScrapBench.SPRING, 1), cost(Items.FLINT, 1),
					cost(ItemTags.PLANKS, 1)),
			recipe(ShoddyGuns.PIPE_RIFLE, 1, cost(Items.IRON_INGOT, 3), cost(ScrapBench.SCREWS, 2), cost(ModItems.TECH_SCRAP, 1),
					cost(ItemTags.PLANKS, 1)),
			recipe(ShoddyGuns.BLUNDERBUSS, 1, cost(Items.COPPER_INGOT, 1), cost(Items.IRON_INGOT, 1), cost(Items.FLINT, 1),
					cost(ItemTags.PLANKS, 2)),
			recipe(ShoddyGuns.JUNK_REPEATER, 1, cost(Skullcranes.SERVO_ARM, 1), cost(Items.IRON_INGOT, 2), cost(ScrapBench.SPRING, 2),
					cost(ModItems.TECH_SCRAP, 1)),
			// Gadgets and the survival kit.
			recipe(Detectors.METAL_DETECTOR, 1, cost(Items.COPPER_INGOT, 3), cost(Items.REDSTONE, 1), cost(ModItems.TECH_SCRAP, 1),
					cost(ScrapBench.SCREWS, 2)),
			recipe(blockItem(Survival.TRIPWIRE_ALARM), 1, cost(Items.COPPER_INGOT, 1), cost(Items.TRIPWIRE_HOOK, 1),
					cost(ScrapBench.SPRING, 1), cost(ItemTags.WOODEN_SLABS, 3)),
			recipe(blockItem(Survival.BEAR_TRAP), 1, cost(Items.IRON_INGOT, 2), cost(ScrapBench.SPRING, 2), cost(Items.IRON_CHAIN, 1),
					cost(Items.IRON_NUGGET, 2)),
			recipe(Survival.PIPE_BOMB_ITEM, 2, cost(Items.IRON_INGOT, 1), cost(Items.GUNPOWDER, 2), cost(Items.STRING, 1),
					cost(ScrapBench.SCREWS, 2)),
			// Scrap armor: junk bolted onto leather.
			recipe(Survival.SCRAP_HELMET, 1, cost(Items.LEATHER_HELMET, 1), cost(ModItems.TECH_SCRAP, 2), cost(ScrapBench.SCREWS, 2)),
			recipe(Survival.SCRAP_CHESTPLATE, 1, cost(Items.LEATHER_CHESTPLATE, 1), cost(ModItems.TECH_SCRAP, 3),
					cost(ScrapBench.SCREWS, 4)),
			recipe(Survival.SCRAP_LEGGINGS, 1, cost(Items.LEATHER_LEGGINGS, 1), cost(ModItems.TECH_SCRAP, 2), cost(ScrapBench.SCREWS, 3)),
			recipe(Survival.SCRAP_BOOTS, 1, cost(Items.LEATHER_BOOTS, 1), cost(ModItems.TECH_SCRAP, 1), cost(ScrapBench.SCREWS, 1)),
			// Blueprints: Workshop guns from bench parts. Each still has its Weapon Station recipe.
			locked(Blueprints.FLARE_GUN, cost(Items.COPPER_INGOT, 2), cost(ScrapBench.SPRING, 1), cost(Items.DYE.orange(), 1)),
			locked(Blueprints.NAIL_GUN, cost(Skullcranes.SERVO_ARM, 1), cost(Items.IRON_INGOT, 2), cost(ScrapBench.SPRING, 1),
					cost(ScrapBench.SCREWS, 4)),
			locked(Blueprints.SNUB_REVOLVER, cost(Items.IRON_INGOT, 3), cost(ScrapBench.SPRING, 1), cost(ScrapBench.SCREWS, 2),
					cost(Items.LEATHER, 1)),
			locked(Blueprints.SAWED_OFF, cost(Items.IRON_INGOT, 4), cost(ScrapBench.SPRING, 2), cost(ScrapBench.SCREWS, 2),
					cost(ItemTags.PLANKS, 2)));

	static {
		// Matching is by ingredient set, so two recipes with the same set would make the later one uncraftable.
		Set<Set<String>> seen = new HashSet<>();
		for (BenchRecipe recipe : ALL) {
			if (recipe.costs().size() > ScrapBenchMenu.INPUT_COUNT) {
				throw new IllegalStateException("Scrap Bench recipe for " + recipe.resultItem() + " needs more slots than the bench has");
			}
			Set<String> ingredients = recipe.costs().stream().map(Cost::key).collect(Collectors.toSet());
			if (!seen.add(ingredients)) {
				throw new IllegalStateException("Scrap Bench recipes share ingredients " + ingredients
						+ "; " + recipe.resultItem() + " could never be crafted");
			}
		}
		for (Blueprint blueprint : Blueprints.ALL) {
			if (blueprint.station() == Blueprint.Station.SCRAP_BENCH && ALL.stream().noneMatch(r -> blueprint.id().equals(r.blueprint()))) {
				throw new IllegalStateException("Blueprint " + blueprint.id() + " has no Scrap Bench recipe");
			}
		}
	}

	private BenchRecipes() {
	}

	/** Loads the list at startup so the checks above fail fast. */
	public static void initialize() {
	}

	/** The recipe the inputs make, unlocked or not (the screen shows locked matches greyed out). */
	public static @Nullable BenchRecipe match(Container inputs) {
		for (BenchRecipe recipe : ALL) {
			if (recipe.matches(inputs)) {
				return recipe;
			}
		}
		return null;
	}

	/** The recipe the inputs make for this player: locked recipes don't count. */
	public static @Nullable BenchRecipe find(Container inputs, Player player) {
		BenchRecipe recipe = match(inputs);
		return recipe != null && recipe.unlockedFor(player) ? recipe : null;
	}

	private static BenchRecipe recipe(Item result, int count, Cost... costs) {
		return new BenchRecipe(result, count, List.of(costs), null);
	}

	private static BenchRecipe locked(Blueprint blueprint, Cost... costs) {
		return new BenchRecipe(blueprint.result(), 1, List.of(costs), blueprint.id());
	}

	/** A block's item by id: the block-to-item link isn't made until the end of startup. */
	private static Item blockItem(Block block) {
		return BuiltInRegistries.ITEM.getValue(BuiltInRegistries.BLOCK.getKey(block));
	}

	private static Cost cost(Item item, int count) {
		return new Cost(item, null, count);
	}

	private static Cost cost(TagKey<Item> tag, int count) {
		return new Cost(null, tag, count);
	}
}
