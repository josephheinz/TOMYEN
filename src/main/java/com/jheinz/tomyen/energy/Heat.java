package com.jheinz.tomyen.energy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * How hot an energy weapon was at a moment of game time, and whether it had overheated. It cools from there by itself
 * (see {@link EnergyWeaponItem#heat}), so it only has to change when the weapon fires: both sides work out the
 * current heat from the same numbers.
 *
 * @param level      0 to {@link EnergyWeaponItem#MAX_HEAT}
 * @param time       the game time it was that hot
 * @param overheated locked out until it has cooled right down
 */
public record Heat(float level, long time, boolean overheated) {
	public static final Codec<Heat> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.FLOAT.fieldOf("level").forGetter(Heat::level),
			Codec.LONG.fieldOf("time").forGetter(Heat::time),
			Codec.BOOL.optionalFieldOf("overheated", false).forGetter(Heat::overheated)).apply(instance, Heat::new));
	public static final StreamCodec<ByteBuf, Heat> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, Heat::level,
			ByteBufCodecs.VAR_LONG, Heat::time,
			ByteBufCodecs.BOOL, Heat::overheated,
			Heat::new);
}
