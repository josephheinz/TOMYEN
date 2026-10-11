package com.jheinz.mymod.foundry;

import java.util.List;

import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Something the Foundry smelts: up to two ingredients with amounts, in either input slot, and nothing else in
 * the inputs.
 */
public record FoundryRecipe(Item resultItem, int resultCount, int cookTicks, List<Cost> costs) {
	public record Cost(Item item, int count) {
	}

	public ItemStack result() {
		return new ItemStack(resultItem, resultCount);
	}

	private int count(Container container, Item item) {
		int total = 0;
		for (int slot = 0; slot < FoundryBlockEntity.INPUT_COUNT; slot++) {
			if (container.getItem(slot).is(item)) {
				total += container.getItem(slot).getCount();
			}
		}
		return total;
	}

	public boolean matches(Container container) {
		boolean anyInput = false;
		for (int slot = 0; slot < FoundryBlockEntity.INPUT_COUNT; slot++) {
			ItemStack stack = container.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			anyInput = true;
			if (costs.stream().noneMatch(cost -> stack.is(cost.item()))) {
				return false;
			}
		}
		return anyInput && costs.stream().allMatch(cost -> count(container, cost.item()) >= cost.count());
	}

	/** Takes one smelt's worth of each ingredient from the inputs; callers check {@link #matches} first. */
	public void consume(Container container) {
		for (Cost cost : costs) {
			int remaining = cost.count();
			for (int slot = 0; slot < FoundryBlockEntity.INPUT_COUNT && remaining > 0; slot++) {
				ItemStack stack = container.getItem(slot);
				if (stack.is(cost.item())) {
					int taken = Math.min(remaining, stack.getCount());
					stack.shrink(taken);
					remaining -= taken;
				}
			}
		}
	}
}
