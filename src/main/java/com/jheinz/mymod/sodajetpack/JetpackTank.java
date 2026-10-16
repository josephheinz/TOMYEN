package com.jheinz.mymod.sodajetpack;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What's in a player's jetpack: fuel and fizz. Kept on the player (saved, and synced to them alone for the gauge and
 * so their client knows whether it may thrust).
 *
 * @param sugar    ticks of sugar left to burn
 * @param slime    ticks of slime left to burn
 * @param pressure how much fizz is left in the bottles, 0 to 1
 * @param flat     ran out of pressure: it gives nothing more until it's shaken back up
 */
public record JetpackTank(int sugar, int slime, float pressure, boolean flat) {
	/** Most of either fuel it holds. */
	public static final int CAPACITY = 300;
	public static final JetpackTank FRESH = new JetpackTank(0, 0, 1.0f, false);

	public static final Codec<JetpackTank> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("sugar", 0).forGetter(JetpackTank::sugar),
			Codec.INT.optionalFieldOf("slime", 0).forGetter(JetpackTank::slime),
			Codec.FLOAT.optionalFieldOf("pressure", 1.0f).forGetter(JetpackTank::pressure),
			Codec.BOOL.optionalFieldOf("flat", false).forGetter(JetpackTank::flat))
			.apply(instance, JetpackTank::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, JetpackTank> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, JetpackTank::sugar,
			ByteBufCodecs.VAR_INT, JetpackTank::slime,
			ByteBufCodecs.FLOAT, JetpackTank::pressure,
			ByteBufCodecs.BOOL, JetpackTank::flat,
			JetpackTank::new);

	public JetpackMode mode() {
		return JetpackMode.of(sugar > 0, slime > 0);
	}

	public JetpackTank add(int moreSugar, int moreSlime) {
		return new JetpackTank(Math.min(CAPACITY, sugar + moreSugar), Math.min(CAPACITY, slime + moreSlime), pressure, flat);
	}

	/** One tick's burn: a tick of each fuel there is. */
	public JetpackTank burn() {
		return new JetpackTank(Math.max(0, sugar - 1), Math.max(0, slime - 1), pressure, flat);
	}

	public JetpackTank withPressure(float pressure) {
		return new JetpackTank(sugar, slime, Math.clamp(pressure, 0.0f, 1.0f), flat);
	}

	public JetpackTank withFlat(boolean flat) {
		return new JetpackTank(sugar, slime, pressure, flat);
	}

	/** Whether it can thrust at all: pressure in the bottles, and not gone flat. */
	public boolean pressurised() {
		return !flat && pressure > 0.0f;
	}
}
