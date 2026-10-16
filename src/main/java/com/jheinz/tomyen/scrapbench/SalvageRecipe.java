package com.jheinz.tomyen.scrapbench;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * One piece of junk the Scrap Bench breaks down, and what comes out. Worn tools and armor give back less, in proportion to
 * the durability they have left; ingots that come up short are paid out in nuggets.
 */
public record SalvageRecipe(Item input, List<Output> outputs) {
	public record Output(Item item, int count) {
	}

	private static final Map<Item, Item> NUGGETS = Map.of(
			Items.IRON_INGOT, Items.IRON_NUGGET,
			Items.COPPER_INGOT, Items.COPPER_NUGGET,
			Items.GOLD_INGOT, Items.GOLD_NUGGET);

	/** What breaking down one of {@code stack} gives. */
	public List<ItemStack> yield(ItemStack stack) {
		float left = stack.isDamageableItem() && stack.getMaxDamage() > 0
				? 1.0f - (float) stack.getDamageValue() / stack.getMaxDamage() : 1.0f;
		List<ItemStack> out = new ArrayList<>();
		for (Output output : outputs) {
			float exact = output.count() * left;
			int whole = Mth.floor(exact + 1.0e-4f);
			add(out, output.item(), whole);
			Item nugget = NUGGETS.get(output.item());
			if (nugget != null) {
				add(out, nugget, Mth.floor((exact - whole) * 9.0f + 1.0e-4f));
			}
		}
		if (out.isEmpty()) {
			// Even a ruined tool is worth a scrap.
			Output first = outputs.getFirst();
			add(out, NUGGETS.getOrDefault(first.item(), first.item()), 1);
		}
		return out;
	}

	private static void add(List<ItemStack> out, Item item, int count) {
		if (count <= 0) {
			return;
		}
		for (ItemStack stack : out) {
			if (stack.is(item)) {
				stack.grow(count);
				return;
			}
		}
		out.add(new ItemStack(item, count));
	}
}
