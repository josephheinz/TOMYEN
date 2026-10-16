package com.jheinz.tomyen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Rounds held by a magazine, all of one ammo type. */
public record MagazineContents(Holder<Item> ammo, int rounds) {
	public static final Codec<MagazineContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Item.CODEC.fieldOf("ammo").forGetter(MagazineContents::ammo),
			Codec.INT.fieldOf("rounds").forGetter(MagazineContents::rounds)
	).apply(instance, MagazineContents::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, MagazineContents> STREAM_CODEC = StreamCodec.composite(
			Item.STREAM_CODEC, MagazineContents::ammo,
			ByteBufCodecs.VAR_INT, MagazineContents::rounds,
			MagazineContents::new);

	public MagazineContents withRounds(int rounds) {
		return new MagazineContents(ammo, rounds);
	}

	public boolean isEmpty() {
		return rounds <= 0;
	}

	public boolean holds(ItemStack stack) {
		return stack.is(ammo);
	}
}
