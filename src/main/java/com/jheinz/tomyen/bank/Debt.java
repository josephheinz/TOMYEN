package com.jheinz.tomyen.bank;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;

import org.jspecify.annotations.Nullable;

/**
 * A player's credit account, kept on the player (see {@link Banking#DEBT}). It exists only while they owe
 * something: paying it off removes it. Synced to its own player for the HUD and tooltips.
 *
 * @param owed          emeralds charged to credit cards and not yet paid back
 * @param ticksLeft     time left to pay, counted down only while the player is online (like the phantom
 *                      insomnia counter); the hunt starts when it runs out
 * @param hunting       the deadline passed: debt collectors are after them
 * @param wave          collector waves sent so far in this hunt
 * @param nextWaveTicks time until the next wave
 * @param warned        which deadline warnings have been given, as bits of {@link DebtCollection#WARNINGS}
 */
public record Debt(int owed, int ticksLeft, boolean hunting, int wave, int nextWaveTicks, int warned) {
	public static final Codec<Debt> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("owed").forGetter(Debt::owed),
			Codec.INT.fieldOf("ticks_left").forGetter(Debt::ticksLeft),
			Codec.BOOL.optionalFieldOf("hunting", false).forGetter(Debt::hunting),
			Codec.INT.optionalFieldOf("wave", 0).forGetter(Debt::wave),
			Codec.INT.optionalFieldOf("next_wave_ticks", 0).forGetter(Debt::nextWaveTicks),
			Codec.INT.optionalFieldOf("warned", 0).forGetter(Debt::warned))
			.apply(instance, Debt::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, Debt> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Debt::owed,
			ByteBufCodecs.VAR_INT, Debt::ticksLeft,
			ByteBufCodecs.BOOL, Debt::hunting,
			ByteBufCodecs.VAR_INT, Debt::wave,
			ByteBufCodecs.VAR_INT, Debt::nextWaveTicks,
			ByteBufCodecs.VAR_INT, Debt::warned,
			Debt::new);

	/** A fresh balance: the full term to pay it back in. */
	public static Debt opened(int owed) {
		return new Debt(owed, DebtCollection.TERM_TICKS, false, 0, 0, 0);
	}

	public Debt withOwed(int owed) {
		return new Debt(owed, ticksLeft, hunting, wave, nextWaveTicks, warned);
	}

	public Debt withTicksLeft(int ticksLeft) {
		return new Debt(owed, ticksLeft, hunting, wave, nextWaveTicks, warned);
	}

	public Debt withWarned(int warned) {
		return new Debt(owed, ticksLeft, hunting, wave, nextWaveTicks, warned);
	}

	public Debt withHunt(boolean hunting, int wave, int nextWaveTicks) {
		return new Debt(owed, ticksLeft, hunting, wave, nextWaveTicks, warned);
	}

	/** The player's account, or null if they owe nothing. Works on both sides (the client sees its own). */
	public static @Nullable Debt of(Player player) {
		Debt debt = player.getAttached(Banking.DEBT);
		return debt != null && debt.owed() > 0 ? debt : null;
	}

	public static int owed(Player player) {
		Debt debt = of(player);
		return debt == null ? 0 : debt.owed();
	}

	/** Past the deadline: their cards are frozen and collectors are on the way. */
	public static boolean inCollections(Player player) {
		Debt debt = of(player);
		return debt != null && debt.hunting();
	}
}
