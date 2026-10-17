package com.jheinz.tomyen.fusion;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

/**
 * One fusion from a {@code fusion} data file (see {@link FusionRecipeLoader} for the format and how to add one): two
 * inputs, in either order, fuse into {@code result} at a {@link Station}. {@code riddle} is the translation key of the
 * cryptic hint villagers and fairies give about it.
 */
public record FusionRecipe(Identifier id, Input first, Input second, Item result, int count, Station station, int tier, String riddle) {
	private static volatile List<FusionRecipe> all = List.of();

	/** Where a fusion can be done. */
	public enum Station {
		/** A fae pond only (the reactor runs these too: it runs everything). */
		POND,
		/** The Fusion Reactor only: a pond strains at it and spits the pair back. */
		REACTOR,
		/** Either. */
		EITHER;

		public boolean pond() {
			return this != REACTOR;
		}
	}

	/** An item id, or an item tag (written "#ns:tag"). */
	public record Input(boolean tag, Identifier id) {
		public boolean test(ItemStack stack) {
			if (stack.isEmpty()) {
				return false;
			}
			return tag ? stack.is(TagKey.create(Registries.ITEM, id)) : BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id);
		}

		@Override
		public String toString() {
			return (tag ? "#" : "") + id;
		}
	}

	/** Whether the two stacks are this fusion's pair, in either order. */
	public boolean matches(ItemStack a, ItemStack b) {
		return first.test(a) && second.test(b) || first.test(b) && second.test(a);
	}

	/** Whether the stack is one half of this fusion. */
	public boolean uses(ItemStack stack) {
		return first.test(stack) || second.test(stack);
	}

	public ItemStack resultStack() {
		return new ItemStack(result, count);
	}

	// ------------------------------------------------------------------------------------------------ lookups

	public static List<FusionRecipe> all() {
		return all;
	}

	static void set(List<FusionRecipe> recipes) {
		all = List.copyOf(recipes);
	}

	/** The fusion the two stacks make, in either order, at any station; null if they make nothing. */
	public static @Nullable FusionRecipe find(ItemStack a, ItemStack b) {
		for (FusionRecipe recipe : all) {
			if (recipe.matches(a, b)) {
				return recipe;
			}
		}
		return null;
	}

	/**
	 * Whether {@code stack} has a fusion partner elsewhere in the inventory, for a fusion this station can do (pond:
	 * pond and either; reactor: everything). A pair of the same item counts if the inventory holds two of it.
	 */
	public static boolean hasPartner(ItemStack stack, Inventory inventory, boolean reactor) {
		for (FusionRecipe recipe : all) {
			if (!reactor && !recipe.station.pond() || !recipe.uses(stack)) {
				continue;
			}
			for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
				ItemStack other = inventory.getItem(slot);
				if (other.isEmpty()) {
					continue;
				}
				boolean same = other == stack;
				if (same && stack.getCount() < 2) {
					continue;
				}
				if (recipe.matches(stack, other)) {
					return true;
				}
			}
		}
		return false;
	}

	/** A riddle to whisper: half the time about something the player carries half of, otherwise any fusion's. */
	public static @Nullable FusionRecipe pickForHint(Inventory inventory, RandomSource random) {
		if (all.isEmpty()) {
			return null;
		}
		if (random.nextBoolean()) {
			List<FusionRecipe> relevant = new ArrayList<>();
			for (FusionRecipe recipe : all) {
				for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
					if (recipe.uses(inventory.getItem(slot))) {
						relevant.add(recipe);
						break;
					}
				}
			}
			if (!relevant.isEmpty()) {
				return relevant.get(random.nextInt(relevant.size()));
			}
		}
		return all.get(random.nextInt(all.size()));
	}
}
