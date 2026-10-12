package com.jheinz.mymod.bank;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Whose vault a debit card spends from (see {@link Banking#CARD_HOLDER}).
 *
 * @param id   the holder's player id, which finds their vault
 * @param name their name when the card was linked, for the tooltip
 */
public record CardHolder(UUID id, String name) {
	public static final Codec<CardHolder> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(CardHolder::id),
			Codec.STRING.fieldOf("name").forGetter(CardHolder::name))
			.apply(instance, CardHolder::new));
	public static final StreamCodec<ByteBuf, CardHolder> STREAM_CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, CardHolder::id,
			ByteBufCodecs.STRING_UTF8, CardHolder::name,
			CardHolder::new);
}
