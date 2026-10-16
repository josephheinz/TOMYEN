package com.jheinz.mymod.fishgun;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;

/**
 * What a held Fish Gun will fire next, worked out on the server each tick ({@link FishAmmo#select}) and synced so the HUD
 * can show it: the fish, its weight in lb (a trophy's own, an ordinary fish its species' typical weight), whether it's a
 * trophy, and how many of that kind the holder carries.
 */
public record LoadedFish(Holder<Item> fish, float weight, boolean trophy, int count) {
	public static final StreamCodec<RegistryFriendlyByteBuf, LoadedFish> STREAM_CODEC = StreamCodec.composite(
			Item.STREAM_CODEC, LoadedFish::fish,
			ByteBufCodecs.FLOAT, LoadedFish::weight,
			ByteBufCodecs.BOOL, LoadedFish::trophy,
			ByteBufCodecs.VAR_INT, LoadedFish::count,
			LoadedFish::new);

	/**
	 * The kind of fish the fire-mode key last picked: a species, and whether its trophies (which are never fired unless
	 * picked) or its ordinary catches.
	 */
	public record Choice(Holder<Item> fish, boolean trophy) {
		public static final Codec<Choice> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Item.CODEC.fieldOf("fish").forGetter(Choice::fish),
				Codec.BOOL.fieldOf("trophy").forGetter(Choice::trophy)).apply(instance, Choice::new));
		public static final StreamCodec<RegistryFriendlyByteBuf, Choice> STREAM_CODEC = StreamCodec.composite(
				Item.STREAM_CODEC, Choice::fish,
				ByteBufCodecs.BOOL, Choice::trophy,
				Choice::new);
	}
}
