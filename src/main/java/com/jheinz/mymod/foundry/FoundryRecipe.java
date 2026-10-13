package com.jheinz.mymod.foundry;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Something the Foundry smelts: up to two ingredients with amounts, in either input slot, and nothing else in
 * the inputs. An upgrade recipe turns its {@code upgrades} ingredient into the result, keeping that item's data
 * (a Neuralink's installed modules, say). A recipe with a {@code minTemperature} (in °C) only makes progress once
 * the Foundry has heated up that far (see {@link FoundryBlockEntity#MAX_TEMPERATURE}); most have none. A cold recipe
 * (a quench) has a {@code maxTemperature} instead: the Foundry holds at that while it works, and a hotter one has to
 * cool down to it before it starts.
 */
public record FoundryRecipe(Item resultItem, int resultCount, int cookTicks, List<Cost> costs, @Nullable Item upgrades, int minTemperature,
		int maxTemperature) {
	/** A recipe's {@code maxTemperature} when it has none. */
	public static final int NO_MAXIMUM = 0;

	public record Cost(Item item, int count) {
	}

	public FoundryRecipe(Item resultItem, int resultCount, int cookTicks, List<Cost> costs, @Nullable Item upgrades) {
		this(resultItem, resultCount, cookTicks, costs, upgrades, 0, NO_MAXIMUM);
	}

	public FoundryRecipe(Item resultItem, int resultCount, int cookTicks, List<Cost> costs) {
		this(resultItem, resultCount, cookTicks, costs, null);
	}

	/** This recipe, needing the Foundry at least this hot. */
	public FoundryRecipe withMinTemperature(int temperature) {
		return new FoundryRecipe(resultItem, resultCount, cookTicks, costs, upgrades, temperature, maxTemperature);
	}

	/** This recipe, needing the Foundry no hotter than this. */
	public FoundryRecipe withMaxTemperature(int temperature) {
		return new FoundryRecipe(resultItem, resultCount, cookTicks, costs, upgrades, minTemperature, temperature);
	}

	public boolean hasMaxTemperature() {
		return maxTemperature != NO_MAXIMUM;
	}

	/** What smelting these inputs gives. */
	public ItemStack result(Container container) {
		if (upgrades != null) {
			for (int slot = 0; slot < FoundryBlockEntity.INPUT_COUNT; slot++) {
				if (container.getItem(slot).is(upgrades)) {
					return container.getItem(slot).transmuteCopy(resultItem, resultCount);
				}
			}
		}
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
