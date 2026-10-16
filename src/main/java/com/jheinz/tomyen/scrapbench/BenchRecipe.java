package com.jheinz.tomyen.scrapbench;

import java.util.List;
import java.util.stream.StreamSupport;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.jheinz.tomyen.blueprint.Blueprints;

/**
 * Something the Scrap Bench assembles. Shapeless with amounts, like the Weapon Station's recipes: the input slots must
 * hold at least {@code count} of every cost (spread across slots in any order) and nothing else. A cost is an item or an
 * item tag (planks, slabs). A recipe with a {@code blueprint} is locked until the player has read that blueprint.
 */
public record BenchRecipe(Item resultItem, int resultCount, List<Cost> costs, @Nullable Identifier blueprint) {
	public record Cost(@Nullable Item item, @Nullable TagKey<Item> tag, int count) {
		public boolean test(ItemStack stack) {
			return item != null ? stack.is(item) : stack.is(tag);
		}

		/** What this cost can be, for icons and tooltips (a tag's members once tags are loaded). */
		public List<Item> options() {
			if (item != null) {
				return List.of(item);
			}
			return StreamSupport.stream(BuiltInRegistries.ITEM.getTagOrEmpty(tag).spliterator(), false).map(Holder::value).toList();
		}

		/** Identifies the ingredient for the duplicate-set check. */
		String key() {
			return item != null ? BuiltInRegistries.ITEM.getKey(item).toString() : "#" + tag.location();
		}
	}

	public ItemStack result() {
		return new ItemStack(resultItem, resultCount);
	}

	public boolean unlockedFor(Player player) {
		return blueprint == null || Blueprints.knows(player, blueprint);
	}

	private int count(Container inputs, Cost cost) {
		int total = 0;
		for (int i = 0; i < inputs.getContainerSize(); i++) {
			ItemStack stack = inputs.getItem(i);
			if (cost.test(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	public boolean matches(Container inputs) {
		boolean anyInput = false;
		for (int i = 0; i < inputs.getContainerSize(); i++) {
			ItemStack stack = inputs.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			anyInput = true;
			if (costs.stream().noneMatch(cost -> cost.test(stack))) {
				return false;
			}
		}
		return anyInput && costs.stream().allMatch(cost -> count(inputs, cost) >= cost.count());
	}

	/** Removes one craft's worth of each cost from the inputs; callers check {@link #matches} first. */
	public void consume(Container inputs) {
		for (Cost cost : costs) {
			int remaining = cost.count();
			for (int i = 0; i < inputs.getContainerSize() && remaining > 0; i++) {
				ItemStack stack = inputs.getItem(i);
				if (cost.test(stack)) {
					int taken = Math.min(remaining, stack.getCount());
					inputs.removeItem(i, taken);
					remaining -= taken;
				}
			}
		}
	}
}
