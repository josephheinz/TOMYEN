package com.jheinz.mymod.cathode;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.jheinz.mymod.MyMod;

/** Sent to a player caught looking at a Cathode Walker's Static Burst: fill their screen with snow for {@code ticks}. */
public record StaticBurstPayload(int ticks) implements CustomPacketPayload {
	public static final Type<StaticBurstPayload> TYPE = new Type<>(MyMod.id("static_burst"));
	public static final StreamCodec<RegistryFriendlyByteBuf, StaticBurstPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, StaticBurstPayload::ticks,
			StaticBurstPayload::new);

	@Override
	public Type<StaticBurstPayload> type() {
		return TYPE;
	}
}
