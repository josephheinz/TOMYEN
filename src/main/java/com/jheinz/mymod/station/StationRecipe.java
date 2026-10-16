package com.jheinz.mymod.station;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.jheinz.mymod.blueprint.Blueprints;

/**
 * Something the Weapon Station makes. Shapeless with amounts: the input slots must hold at least
 * {@code count} of every cost (spread across slots in any order) and nothing else. A recipe with a {@code blueprint}
 * is locked until the player has read that blueprint (see {@link Blueprints}).
 */
public record StationRecipe(Item resultItem, int resultCount, List<Cost> costs, @Nullable Identifier blueprint) {
	public record Cost(Item item, int count) {
	}

	public StationRecipe(Item resultItem, int resultCount, List<Cost> costs) {
		this(resultItem, resultCount, costs, null);
	}

	public boolean unlockedFor(Player player) {
		return blueprint == null || Blueprints.knows(player, blueprint);
	}

	/** A fresh result stack. Built on demand: stacks can't exist before item components are bound at startup. */
	public ItemStack result() {
		return new ItemStack(resultItem, resultCount);
	}

	private static int count(Container inputs, Item item) {
		int total = 0;
		for (int i = 0; i < inputs.getContainerSize(); i++) {
			ItemStack stack = inputs.getItem(i);
			if (stack.is(item)) {
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
			if (costs.stream().noneMatch(cost -> stack.is(cost.item()))) {
				return false;
			}
		}
		return anyInput && costs.stream().allMatch(cost -> count(inputs, cost.item()) >= cost.count());
	}

	/** Removes one craft's worth of each cost from the inputs; callers check {@link #matches} first. */
	public void consume(Container inputs) {
		for (Cost cost : costs) {
			int remaining = cost.count();
			for (int i = 0; i < inputs.getContainerSize() && remaining > 0; i++) {
				ItemStack stack = inputs.getItem(i);
				if (stack.is(cost.item())) {
					int taken = Math.min(remaining, stack.getCount());
					inputs.removeItem(i, taken);
					remaining -= taken;
				}
			}
		}
	}
}
