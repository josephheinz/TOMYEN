package com.jheinz.mymod.fishing;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** On a mod fishing rod: the part fitted in each {@link PartSlot}. An empty slot means the rod's stock part. */
public record RodParts(Optional<Holder<Item>> line, Optional<Holder<Item>> hook, Optional<Holder<Item>> reel,
		Optional<Holder<Item>> lure) {
	public static final RodParts EMPTY = new RodParts(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

	public static final Codec<RodParts> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Item.CODEC.optionalFieldOf("line").forGetter(RodParts::line),
			Item.CODEC.optionalFieldOf("hook").forGetter(RodParts::hook),
			Item.CODEC.optionalFieldOf("reel").forGetter(RodParts::reel),
			Item.CODEC.optionalFieldOf("lure").forGetter(RodParts::lure)).apply(instance, RodParts::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, RodParts> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.optional(Item.STREAM_CODEC), RodParts::line,
			ByteBufCodecs.optional(Item.STREAM_CODEC), RodParts::hook,
			ByteBufCodecs.optional(Item.STREAM_CODEC), RodParts::reel,
			ByteBufCodecs.optional(Item.STREAM_CODEC), RodParts::lure,
			RodParts::new);

	public static RodParts of(ItemStack rod) {
		RodParts parts = rod.get(Fishing.ROD_PARTS);
		return parts == null ? EMPTY : parts;
	}

	public Optional<Holder<Item>> get(PartSlot slot) {
		return switch (slot) {
			case LINE -> line;
			case HOOK -> hook;
			case REEL -> reel;
			case LURE -> lure;
		};
	}

	/** The fitted part as a one-item stack, or empty. */
	public ItemStack stack(PartSlot slot) {
		return get(slot).map(ItemStack::new).orElse(ItemStack.EMPTY);
	}

	public RodParts with(PartSlot slot, ItemStack part) {
		Optional<Holder<Item>> holder = part.isEmpty() ? Optional.empty() : Optional.of(part.typeHolder());
		return new RodParts(slot == PartSlot.LINE ? holder : line, slot == PartSlot.HOOK ? holder : hook,
				slot == PartSlot.REEL ? holder : reel, slot == PartSlot.LURE ? holder : lure);
	}

	public boolean isEmpty() {
		return line.isEmpty() && hook.isEmpty() && reel.isEmpty() && lure.isEmpty();
	}
}
