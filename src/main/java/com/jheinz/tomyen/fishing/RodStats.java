package com.jheinz.tomyen.fishing;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;

import com.jheinz.tomyen.starforge.InfusionEffects;

/**
 * A rod's totals: its tier, whether it fishes lava, its own modifiers plus every fitted part's (and its bait's, while
 * there's bait that works on this rod; its own conditional bonus where that holds; never a reel part on a rod with no
 * reel), and the bait itself (null if none).
 */
public record RodStats(int tier, boolean lava, RodModifiers mods, @Nullable BaitItem bait) {
	/** What the test command uses when the player holds no mod rod. */
	public static final RodStats BASELINE = new RodStats(0, false, RodModifiers.NONE, null);
	/** The weight roll's push per rod tier: a star steel rod (tier 4) lands heavier fish than bamboo. */
	private static final float SIZE_PER_TIER = 0.06f;
	private static final float SIZE_PER_LUCK_OF_THE_SEA = 0.06f;
	private static final float SIZE_PER_LUCK = 0.04f;

	/** The rod's totals anywhere: without its conditional bonus (the Tackle Bench shows that as a tag). */
	public static RodStats of(ItemStack rod) {
		return of(rod, false);
	}

	/** The rod's totals for a cast from {@code pos}: its own bonus added if its condition holds there. */
	public static RodStats of(ItemStack rod, Level level, BlockPos pos) {
		return of(rod, rod.getItem() instanceof RodItem item && item.condition().holds(level, pos));
	}

	private static RodStats of(ItemStack rod, boolean bonus) {
		if (!(rod.getItem() instanceof RodItem item)) {
			return BASELINE;
		}
		RodModifiers total = item.modifiers();
		if (bonus) {
			total = total.plus(item.bonus());
		}
		RodParts parts = RodParts.of(rod);
		for (PartSlot slot : PartSlot.values()) {
			if (slot == PartSlot.REEL && !item.hasReel()) {
				continue;
			}
			if (parts.get(slot).map(holder -> holder.value()).orElse(null) instanceof RodPartItem part) {
				total = total.plus(part.modifiers());
			}
		}
		// A Star Forge infusion (Starlit, Fae-Kissed, Featherweight) works like one more part.
		total = total.plus(InfusionEffects.rodModifiers(rod));
		RodBait loaded = RodBait.of(rod);
		BaitItem bait = loaded == null ? null : loaded.bait();
		if (bait != null && bait.works(item.fishesLava())) {
			total = total.plus(bait.effect().modifiers());
		}
		return new RodStats(item.tier(), item.fishesLava(), total, bait);
	}

	/**
	 * The weight roll's bonus for {@code species} (see {@link FishSize#roll}): the rod's tier, the bait, Luck of the
	 * Sea and the angler's luck.
	 */
	public float sizeBonus(FishingSpecies species, boolean inLava, int luckOfTheSea, int luck) {
		float bonus = SIZE_PER_TIER * tier + SIZE_PER_LUCK_OF_THE_SEA * luckOfTheSea + SIZE_PER_LUCK * Math.max(0, luck);
		if (bait != null) {
			bonus += bait.sizeBonus(species, inLava);
		}
		return Mth.clamp(bonus, 0.0f, FishSize.MAX_BONUS);
	}

	/** The weight roll's bonus from the rod's tier and bait alone, for an ordinary species: the Tackle Bench shows it. */
	public float baseSizeBonus() {
		float bonus = SIZE_PER_TIER * tier;
		if (bait != null && bait.works(lava)) {
			bonus += bait.effect().size();
		}
		return Mth.clamp(bonus, 0.0f, FishSize.MAX_BONUS);
	}

	/** How much likelier the bait (and a predator lure) makes {@code species} to bite. */
	public double attraction(FishingSpecies species, boolean inLava) {
		double attraction = bait == null ? 1.0 : bait.attraction(species, inLava);
		if (mods.predator() != 0.0f && species.stack().is(Fishing.PREDATOR_FISH)) {
			attraction *= Math.max(0.0, 1.0 + mods.predator());
		}
		return attraction;
	}

	/** Ticks off the bite wait: always, plus at night, plus in rain or murky water ({@code wet}). */
	public int biteTicks(boolean night, boolean wet) {
		return mods.biteTicks() + (night ? mods.nightBiteTicks() : 0) + (wet ? mods.rainBiteTicks() : 0);
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
