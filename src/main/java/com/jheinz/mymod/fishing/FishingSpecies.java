package com.jheinz.mymod.fishing;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

import com.jheinz.mymod.fishing.fish.FishKind;

/**
 * One catchable thing from a {@code fishing_species} data file (see {@link FishingSpeciesLoader} for the format):
 * the item, how common and how hard it is, how it fights, where and when it bites, and (for real fish) how much it
 * weighs ({@link FishSize}; null for things that aren't weighed, like the old boot).
 */
public record FishingSpecies(Identifier id, Item item, int weight, int difficulty, FishBehavior behavior, int minRodTier,
		boolean lava, Identifier dimension, List<BiomeMatch> biomes, Time time, Weather weather, int minY, int maxY,
		@Nullable Near near, int minCount, int maxCount, @Nullable FishSize size) {
	private static volatile List<FishingSpecies> all = List.of();

	public enum Time {
		ANY, DAY, NIGHT
	}

	public enum Weather {
		ANY, CLEAR, RAIN, THUNDER
	}

	/** A biome id, or a biome tag when {@code tag} (written "#ns:tag" in the file). */
	public record BiomeMatch(boolean tag, Identifier id) {
		boolean test(Holder<Biome> biome) {
			return tag ? biome.is(TagKey.create(Registries.BIOME, id)) : biome.is(id);
		}

		@Override
		public String toString() {
			return (tag ? "#" : "") + id;
		}
	}

	/** A block (or block tag, written "#ns:tag") that must be within {@code radius} blocks of the hook. */
	public record Near(boolean tag, Identifier id, int radius) {
		boolean test(ServerLevel level, BlockPos pos) {
			TagKey<Block> blocks = tag ? TagKey.create(Registries.BLOCK, id) : null;
			Predicate<BlockState> match = tag ? state -> state.is(blocks) : state -> BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(id);
			return FishKind.nearBlock(level, pos, match, radius);
		}

		@Override
		public String toString() {
			return (tag ? "#" : "") + id + " within " + radius;
		}
	}

	/** Where a hook is fishing: what the species' conditions are checked against. */
	public record Where(ServerLevel level, BlockPos pos, boolean lava, int rodTier) {
	}

	/** Every loaded species, in file order. Server side only: the client never loads them. */
	public static List<FishingSpecies> all() {
		return all;
	}

	static void set(List<FishingSpecies> species) {
		all = List.copyOf(species);
	}

	public boolean eligible(Where where) {
		ServerLevel level = where.level();
		BlockPos pos = where.pos();
		if (lava != where.lava() || where.rodTier() < minRodTier || pos.getY() < minY || pos.getY() > maxY) {
			return false;
		}
		if (dimension != null && !dimension.equals(level.dimension().identifier())) {
			return false;
		}
		boolean timeOk = switch (time) {
			case ANY -> true;
			case DAY -> level.isBrightOutside();
			case NIGHT -> level.isDarkOutside();
		};
		boolean weatherOk = switch (weather) {
			case ANY -> true;
			case CLEAR -> !level.isRaining();
			case RAIN -> level.isRaining();
			case THUNDER -> level.isThundering();
		};
		if (!timeOk || !weatherOk) {
			return false;
		}
		if (!biomes.isEmpty()) {
			Holder<Biome> biome = level.getBiome(pos);
			boolean biomeOk = false;
			for (BiomeMatch match : biomes) {
				if (match.test(biome)) {
					biomeOk = true;
					break;
				}
			}
			if (!biomeOk) {
				return false;
			}
		}
		// Last: it's the one check that looks at blocks.
		return near == null || near.test(level, pos);
	}

	/** The first species that catches {@code item}, or null. */
	public static @Nullable FishingSpecies forItem(Item item) {
		for (FishingSpecies species : all) {
			if (species.item == item) {
				return species;
			}
		}
		return null;
	}

	/** The species with this id, or (for a bare name like "pike") the first whose id path matches; null if none. */
	public static @Nullable FishingSpecies byName(String name) {
		Identifier id = Identifier.tryParse(name);
		for (FishingSpecies species : all) {
			if (species.id.equals(id)) {
				return species;
			}
		}
		for (FishingSpecies species : all) {
			if (species.id.getPath().equals(name) || BuiltInRegistries.ITEM.getKey(species.item).getPath().equals(name)) {
				return species;
			}
		}
		return null;
	}

	public static List<FishingSpecies> eligible(Where where, List<FishingSpecies> from) {
		List<FishingSpecies> out = new ArrayList<>();
		for (FishingSpecies species : from) {
			if (species.eligible(where)) {
				out.add(species);
			}
		}
		return out;
	}

	/**
	 * The weight this species rolls with under a rarity bias: {@code weight^(1 - bias)}. Bias flattens the odds, so rare
	 * (low weight) species gain on common ones while every species keeps its rank.
	 */
	public double biasedWeight(float bias) {
		return Math.pow(weight, 1.0 - bias);
	}

	/** One species picked from {@code eligible} by biased weight, or null if there are none. */
	public static FishingSpecies roll(List<FishingSpecies> eligible, float bias, RandomSource random) {
		return roll(eligible, bias, species -> 1.0, random);
	}

	/**
	 * One species picked from {@code eligible} by biased weight times {@code attraction} (a bait drawing some species
	 * in), or null if there are none.
	 */
	public static FishingSpecies roll(List<FishingSpecies> eligible, float bias, ToDoubleFunction<FishingSpecies> attraction,
			RandomSource random) {
		double total = 0;
		for (FishingSpecies species : eligible) {
			total += species.biasedWeight(bias) * attraction.applyAsDouble(species);
		}
		if (total <= 0) {
			return null;
		}
		double pick = random.nextDouble() * total;
		for (FishingSpecies species : eligible) {
			pick -= species.biasedWeight(bias) * attraction.applyAsDouble(species);
			if (pick < 0) {
				return species;
			}
		}
		return eligible.getLast();
	}

	public int rollCount(RandomSource random) {
		return minCount + random.nextInt(maxCount - minCount + 1);
	}

	public ItemStack stack() {
		return new ItemStack(item);
	}
}
