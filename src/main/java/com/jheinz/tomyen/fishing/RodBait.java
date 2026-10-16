package com.jheinz.tomyen.fishing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

/** On a mod rod: the bait loaded at the Tackle Bench, and how much is left (one is eaten per fish landed). */
public record RodBait(Holder<Item> item, int count) {
	public static final Codec<RodBait> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Item.CODEC.fieldOf("item").forGetter(RodBait::item),
			ExtraCodecs.POSITIVE_INT.fieldOf("count").forGetter(RodBait::count)).apply(instance, RodBait::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, RodBait> STREAM_CODEC = StreamCodec.composite(
			Item.STREAM_CODEC, RodBait::item,
			ByteBufCodecs.VAR_INT, RodBait::count,
			RodBait::new);

	public static @Nullable RodBait of(ItemStack rod) {
		return rod.get(Fishing.ROD_BAIT);
	}

	/** The bait on the rod as a stack (empty if none). */
	public static ItemStack stack(ItemStack rod) {
		RodBait bait = of(rod);
		return bait == null ? ItemStack.EMPTY : new ItemStack(bait.item, bait.count);
	}

	/** Loads {@code bait} onto the rod (an empty stack takes it off). */
	public static void set(ItemStack rod, ItemStack bait) {
		if (bait.isEmpty()) {
			rod.remove(Fishing.ROD_BAIT);
		} else {
			rod.set(Fishing.ROD_BAIT, new RodBait(bait.typeHolder(), bait.getCount()));
		}
	}

	/** The bait's item, if it's bait. */
	public @Nullable BaitItem bait() {
		return item.value() instanceof BaitItem bait ? bait : null;
	}

	/** One fish landed: one bait eaten. */
	public static void eat(ItemStack rod) {
		RodBait bait = of(rod);
		if (bait != null) {
			set(rod, new ItemStack(bait.item, bait.count - 1));
		}
	}
}
