package com.jheinz.tomyen.blueprint;

import com.mojang.serialization.Codec;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A schematic that teaches one recipe early: {@code result} becomes craftable at {@code station} out of cheaper
 * materials than its usual route. {@code tier} is the tier the result normally belongs to (and the colour of the paper).
 */
public record Blueprint(Identifier id, Item result, Station station, Tier tier) {
	public Component resultName() {
		return new ItemStack(result).getHoverName();
	}

	/** Where the recipe it teaches is made. */
	public enum Station {
		SCRAP_BENCH("scrap_bench"),
		WEAPON_STATION("weapon_station");

		private final String key;

		Station(String key) {
			this.key = key;
		}

		public Component displayName() {
			return Component.translatable("block.tomyen." + key);
		}
	}

	/** The tier a blueprint skips ahead to: Workshop guns made on the Scrap Bench, Foundry parts made without steel. */
	public enum Tier implements StringRepresentable {
		WORKSHOP("workshop"),
		FOUNDRY("foundry");

		public static final Codec<Tier> CODEC = StringRepresentable.fromEnum(Tier::values);

		private final String name;

		Tier(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}

		public Component displayName() {
			return Component.translatable("blueprint.tomyen.tier." + name);
		}
	}
}
