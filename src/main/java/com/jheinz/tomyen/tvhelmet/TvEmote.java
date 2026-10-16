package com.jheinz.tomyen.tvhelmet;

import net.minecraft.network.chat.Component;

/**
 * What a TV Helmet's screen can show, in the emote wheel's order (clockwise from the top). Each has one or more frames,
 * textures/entity/tv_helmet/screen_{frame}.png (painted by tools/gen_tv_jetpack.py), and a way of playing them:
 * <ul>
 * <li>{@link Style#BLINK}: the first frame, with the second flashed for a few ticks now and then (a blink);
 * <li>{@link Style#LOOP}: every frame in turn, {@code period} ticks each (a laugh's bounce, a tear falling, a heartbeat);
 * <li>{@link Style#STILL}: the one frame.
 * </ul>
 */
public enum TvEmote {
	HAPPY("happy", Style.BLINK, 0, "happy", "happy_blink"),
	LAUGH("laugh", Style.LOOP, 4, "laugh", "laugh_1"),
	ANGRY("angry", Style.BLINK, 0, "angry", "angry_blink"),
	SAD("sad", Style.LOOP, 12, "sad", "sad_1"),
	SURPRISED("surprised", Style.BLINK, 0, "surprised", "surprised_blink"),
	CAT("cat", Style.BLINK, 0, "cat", "cat_blink"),
	COOL("cool", Style.LOOP, 20, "cool", "cool_1"),
	SKULL("skull", Style.LOOP, 5, "skull", "skull_1"),
	HEART("heart", Style.LOOP, 8, "heart", "heart_1"),
	QUESTION("question", Style.LOOP, 10, "question", "question_1"),
	BARS("bars", Style.STILL, 0, "bars"),
	STATIC("static", Style.LOOP, 2, "static_0", "static_1", "static_2", "static_3");

	public enum Style {
		BLINK, LOOP, STILL
	}

	private static final TvEmote[] VALUES = values();

	private final String id;
	private final Style style;
	private final int period;
	private final String[] frames;

	TvEmote(String id, Style style, int period, String... frames) {
		this.id = id;
		this.style = style;
		this.period = period;
		this.frames = frames;
	}

	public String id() {
		return id;
	}

	public Style style() {
		return style;
	}

	/** Ticks each frame of a {@link Style#LOOP} shows for. */
	public int period() {
		return period;
	}

	/** The frames' names, in the order they play. */
	public String[] frames() {
		return frames;
	}

	public Component displayName() {
		return Component.translatable("tv_helmet.tomyen.emote." + id);
	}

	/** The emote with this ordinal, or {@link #HAPPY} for anything out of range (from an old save or a bad packet). */
	public static TvEmote byId(int id) {
		return id >= 0 && id < VALUES.length ? VALUES[id] : HAPPY;
	}

	public static int count() {
		return VALUES.length;
	}

	public TvEmote next() {
		return VALUES[(ordinal() + 1) % VALUES.length];
	}
}
