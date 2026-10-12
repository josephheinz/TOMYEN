package com.jheinz.mymod.soul;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The souls in a Soul Vacuum's canister. A hostile mob gives a whole soul; anything else (animals, villagers, golems)
 * a weak one, and it takes two weak souls to make a Soul Charge. Both count the same toward the canister's
 * {@link #CAPACITY}.
 */
public record SoulCanister(int souls, int weakSouls) {
	public static final int CAPACITY = 16;
	public static final SoulCanister EMPTY = new SoulCanister(0, 0);

	public static final Codec<SoulCanister> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("souls", 0).forGetter(SoulCanister::souls),
			Codec.INT.optionalFieldOf("weak_souls", 0).forGetter(SoulCanister::weakSouls)
	).apply(instance, SoulCanister::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, SoulCanister> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SoulCanister::souls,
			ByteBufCodecs.VAR_INT, SoulCanister::weakSouls,
			SoulCanister::new);

	public int total() {
		return souls + weakSouls;
	}

	public boolean isFull() {
		return total() >= CAPACITY;
	}

	public float fill() {
		return Math.min(1.0f, (float) total() / CAPACITY);
	}

	public SoulCanister with(boolean weak) {
		return weak ? new SoulCanister(souls, weakSouls + 1) : new SoulCanister(souls + 1, weakSouls);
	}

	/** How many Soul Charges emptying the canister makes. */
	public int charges() {
		return souls + weakSouls / 2;
	}

	/** What's left after emptying it: an odd weak soul with no partner. */
	public SoulCanister afterRelease() {
		return new SoulCanister(0, weakSouls % 2);
	}
}
