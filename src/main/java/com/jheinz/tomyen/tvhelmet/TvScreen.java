package com.jheinz.tomyen.tvhelmet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A player's TV Helmet settings and what its screen is doing, synced to everyone so every client draws the same
 * picture: the emote they picked, whether its static night vision is switched on, and the screen's mode.
 *
 * @param emote       a {@link TvEmote} ordinal
 * @param nightVision whether the wearer has night vision switched on (it only works while the set does)
 * @param mode        {@link #NORMAL}, {@link #IDLE} (colour bars), {@link #RAIN} (flickering) or {@link #SHORTED}
 */
public record TvScreen(int emote, boolean nightVision, int mode) {
	/** Showing the emote. */
	public static final int NORMAL = 0;
	/** The wearer has been idle a long while: colour bars, like a station gone off the air. */
	public static final int IDLE = 1;
	/** Out in the rain: the picture keeps breaking up into static, and night vision cuts out. */
	public static final int RAIN = 2;
	/** Shorted out in water, and drying out after: pure static, no night vision. */
	public static final int SHORTED = 3;

	public static final TvScreen DEFAULT = new TvScreen(TvEmote.HAPPY.ordinal(), false, NORMAL);

	public static final Codec<TvScreen> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("emote", 0).forGetter(TvScreen::emote),
			Codec.BOOL.optionalFieldOf("night_vision", false).forGetter(TvScreen::nightVision),
			Codec.INT.optionalFieldOf("mode", NORMAL).forGetter(TvScreen::mode))
			.apply(instance, TvScreen::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, TvScreen> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, TvScreen::emote,
			ByteBufCodecs.BOOL, TvScreen::nightVision,
			ByteBufCodecs.VAR_INT, TvScreen::mode,
			TvScreen::new);

	public TvEmote emoteType() {
		return TvEmote.byId(emote);
	}

	public TvScreen withEmote(TvEmote emote) {
		return new TvScreen(emote.ordinal(), nightVision, mode);
	}

	public TvScreen withNightVision(boolean on) {
		return new TvScreen(emote, on, mode);
	}

	public TvScreen withMode(int mode) {
		return new TvScreen(emote, nightVision, mode);
	}

	/** Whether night vision is working: switched on, and the set neither shorted nor flickering in the rain. */
	public boolean seeing() {
		return nightVision && (mode == NORMAL || mode == IDLE);
	}
}
