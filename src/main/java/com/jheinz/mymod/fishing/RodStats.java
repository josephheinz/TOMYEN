package com.jheinz.mymod.fishing;

import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/** A rod's totals: its tier, whether it fishes lava, and its own modifiers plus every fitted part's. */
public record RodStats(int tier, boolean lava, RodModifiers mods) {
	/** What the test command uses when the player holds no mod rod. */
	public static final RodStats BASELINE = new RodStats(0, false, RodModifiers.NONE);

	public static RodStats of(ItemStack rod) {
		if (!(rod.getItem() instanceof RodItem item)) {
			return BASELINE;
		}
		RodModifiers total = item.modifiers();
		RodParts parts = RodParts.of(rod);
		for (PartSlot slot : PartSlot.values()) {
			if (parts.get(slot).map(holder -> holder.value()).orElse(null) instanceof RodPartItem part) {
				total = total.plus(part.modifiers());
			}
		}
		return new RodStats(item.tier(), item.fishesLava(), total);
	}

	public int biteTicks(boolean night) {
		return mods.biteTicks() + (night ? mods.nightBiteTicks() : 0);
	}

	/** How much the species roll leans toward rare species (0 none, 0.75 at most): parts, night lures and luck. */
	public float rarityBias(boolean night, int luck) {
		return Mth.clamp(mods.rarity() + (night ? mods.nightRarity() : 0.0f) + 0.08f * luck, 0.0f, 0.75f);
	}

	public float zone() {
		return Math.max(0.6f, 1.0f + mods.zone());
	}

	public float speed() {
		return Mth.clamp(1.0f + mods.speed(), 0.5f, 1.6f);
	}

	public float reel() {
		return Mth.clamp(1.0f + mods.reel(), 0.5f, 2.0f);
	}

	public float escape() {
		return Mth.clamp(mods.escape(), 0.0f, 1.0f);
	}

	public float line() {
		return Mth.clamp(mods.line(), 0.0f, 1.0f);
	}

	public float assist() {
		return Mth.clamp(mods.assist(), 0.0f, 1.0f);
	}
}
