package com.jheinz.tomyen.tank;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.jspecify.annotations.Nullable;

/**
 * A tank's ammo rack: {@link #SIZE} slots that take only 120mm rounds and gunpowder (for the smoke launchers).
 * The loader takes rounds from it one at a time, and the crew readouts count what's in it.
 */
public class TankRack extends SimpleContainer {
	public static final int SIZE = 18;

	private final @Nullable Tank tank;

	public TankRack(@Nullable Tank tank) {
		super(SIZE);
		this.tank = tank;
	}

	public static boolean accepts(ItemStack stack) {
		return ShellType.of(stack.getItem()) != null || stack.is(Items.GUNPOWDER);
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return accepts(stack);
	}

	/** How many of an item are in the rack. */
	public int count(Item item) {
		int total = 0;
		for (ItemStack stack : getItems()) {
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	/** Takes up to {@code amount} of an item out of the rack; returns how many it took. */
	public int take(Item item, int amount) {
		int taken = 0;
		for (int i = 0; i < getContainerSize() && taken < amount; i++) {
			ItemStack stack = getItem(i);
			if (stack.is(item)) {
				int n = Math.min(stack.getCount(), amount - taken);
				stack.shrink(n);
				taken += n;
			}
		}
		if (taken > 0) {
			setChanged();
		}
		return taken;
	}

	@Override
	public void setChanged() {
		super.setChanged();
		if (tank != null) {
			tank.gunnery().rackChanged();
		}
	}
}
