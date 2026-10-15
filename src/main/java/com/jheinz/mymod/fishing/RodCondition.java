package com.jheinz.mymod.fishing;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;

import com.jheinz.mymod.MyMod;

/**
 * Where a rod's own bonus ({@link RodItem#bonus()}) applies, checked where the angler stands when they cast: a
 * Froststeel Rod comes into its own in the cold, a Fae Rod in the Feywild. (Night and rain have their own modifiers, so
 * parts can use them too: see {@link RodModifiers}.)
 */
public enum RodCondition {
	/** No bonus. */
	NONE("none"),
	/** Anywhere cold enough to snow: snowy and frozen biomes, high mountains. */
	COLD("cold"),
	/** In the Feywild. */
	FEYWILD("feywild");

	private static final Identifier FEYWILD_DIMENSION = MyMod.id("feywild");

	private final String id;

	RodCondition(String id) {
		this.id = id;
	}

	public boolean holds(Level level, BlockPos pos) {
		return switch (this) {
			case NONE -> false;
			case COLD -> level.getBiome(pos).value().coldEnoughToSnow(pos, level.getSeaLevel());
			case FEYWILD -> level.dimension().identifier().equals(FEYWILD_DIMENSION);
		};
	}

	/** The tooltip's heading over the bonus lines ("In the cold:"). */
	public Component label() {
		return Component.translatable("tooltip.mymod.rod.condition." + id);
	}

	/** The Tackle Bench's tag for a rod with this bonus. */
	public String tagKey() {
		return "container.mymod.tackle_bench.tag." + id;
	}

	/** Murky water, where rattles carry and the copper rod's rain bonus also counts. */
	public static boolean murky(Level level, BlockPos pos) {
		var biome = level.getBiome(pos);
		return biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP);
	}
}
