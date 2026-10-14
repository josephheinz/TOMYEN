package com.jheinz.mymod.client.fishing;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import com.jheinz.mymod.fishing.FightParams;
import com.jheinz.mymod.fishing.Fishing;
import com.jheinz.mymod.fishing.FishingNetworking.Outcome;

/**
 * One quick-time fight, played on the client. Each game tick it gets the reel presses and whether the reel is held;
 * it moves its fish (from the server's seed, so the fish is the same fish every time it's replayed), checks for a
 * win or a loss, and draws its widget into the HUD panel ({@link FishingHud}). The server only checks the result.
 * How long and hard the fish fights comes from {@link FightParams#length} and {@link FightParams#power} (its weight and
 * rarity), and a big one tires and surges ({@link #effort}).
 */
abstract class FishingMinigame {
	/** Shared bar colours, from the HUD's palette. */
	static final int TRACK = 0xFF16202A;
	static final int TRACK_EDGE = 0xFF2C3B49;
	static final int GREEN = 0xFF3FB56F;
	static final int GREEN_LIGHT = 0xFF7FE0A3;
	static final int RED = 0xFFC2453F;
	static final int RED_LIGHT = 0xFFFF7A70;
	static final int AMBER = 0xFFE8B74A;
	static final int WHITE = 0xFFF4F7FA;
	static final int PROGRESS = 0xFF6CCBFF;

	final FightParams params;
	final RandomSource random;
	private final long seed;
	/** Ticks since the fight started; the fish only fights once the intro is over. */
	int age;
	private Outcome outcome;
	private static final float TIRE_AT = 0.42f;
	private static final float SURGE_AT = 0.78f;
	private static final int TIRED_TICKS = 50;
	private static final int SURGE_TICKS = 45;
	private static final float TIRED_EFFORT = 0.55f;
	private static final float SURGE_EFFORT = 1.35f;
	private int tired;
	private int surge;
	private boolean hasTired;
	private boolean hasSurged;
	boolean perfect = true;

	FishingMinigame(FightParams params) {
		this.params = params;
		this.seed = params.seed();
		this.random = RandomSource.create(params.seed());
	}

	static FishingMinigame create(FightParams params) {
		return switch (params.game()) {
			case TENSION -> new TensionMinigame(params);
			case STRIKES -> new StrikeMinigame(params);
			case MASH -> new MashMinigame(params);
		};
	}

	final void tick(int presses, boolean held) {
		age++;
		if (outcome != null || age <= FightParams.INTRO_TICKS) {
			return;
		}
		play(presses, held);
		stamina();
		if (outcome == null && age > params.maxTicks()) {
			end(Outcome.ESCAPED);
		}
	}

	/** One tick of the fight proper. */
	abstract void play(int presses, boolean held);

	/** How far through the fight the player is, 0 to 1: the stamina phases key off it. */
	abstract float progress();

	/**
	 * A big fish's stamina ({@link FightParams#stamina}): a little under halfway it tires and its pull slackens for a
	 * couple of seconds; most of the way in it makes one last run, pulling harder than ever.
	 */
	private void stamina() {
		if (!params.stamina() || outcome != null) {
			return;
		}
		if (tired > 0) {
			tired--;
		}
		if (surge > 0) {
			surge--;
		}
		float p = progress();
		if (!hasTired && p >= TIRE_AT) {
			hasTired = true;
			tired = TIRED_TICKS;
			FishingClient.sound(Fishing.REEL_CLICK, 0.6f, 0.6f);
		} else if (!hasSurged && p >= SURGE_AT) {
			hasSurged = true;
			surge = SURGE_TICKS;
			FishingClient.sound(Fishing.LINE_CREAK, 0.9f, 0.7f);
		}
	}

	/** The fish's effort right now: 1 normally, less while it's tired, more in its last run. */
	final float effort() {
		return tired > 0 ? TIRED_EFFORT : surge > 0 ? SURGE_EFFORT : 1.0f;
	}

	/** The HUD's line for a stamina phase, or null when there's none on. */
	final Component phaseHint() {
		if (tired > 0) {
			return Component.translatable("hud.mymod.fishing.tiring");
		}
		return surge > 0 ? Component.translatable("hud.mymod.fishing.surge") : null;
	}

	final boolean surging() {
		return surge > 0;
	}

	/** How hard the fish is pulling right now, 0 to 1: the bobber tugs this hard. */
	abstract float tug();

	/** The widget, in the panel's body: {@code width} wide, {@link #bodyHeight()} tall, from (x, y). */
	abstract void extract(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, float partial, float alpha);

	abstract int bodyHeight();

	/** The line under the widget telling the player what to do. */
	abstract Component hint(Component keys);

	final void end(Outcome result) {
		if (outcome == null) {
			outcome = result;
			if (result != Outcome.CAUGHT) {
				perfect = false;
			}
		}
	}

	final Outcome outcome() {
		return outcome;
	}

	final boolean running() {
		return outcome == null;
	}

	/** How far through the intro, 0 to 1. */
	final float introProgress(float partial) {
		return Mth.clamp((age + partial) / FightParams.INTRO_TICKS, 0.0f, 1.0f);
	}

	/**
	 * Smooth value noise in 0..1 over {@code t}: random values at whole numbers, eased between with a cosine.
	 * {@code channel} picks an independent stream.
	 */
	final float noise(float t, int channel) {
		int i = Mth.floor(t);
		float f = t - i;
		float ease = (1.0f - Mth.cos(f * Mth.PI)) * 0.5f;
		return Mth.lerp(ease, hash(i, channel), hash(i + 1, channel));
	}

	private float hash(int i, int channel) {
		long h = seed ^ (i * 0x9E3779B97F4A7C15L) ^ (channel * 0xC2B2AE3D27D4EB4FL);
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		return (h >>> 40) / (float) (1L << 24);
	}

	static int alpha(int argb, float alpha) {
		int a = Math.round(((argb >>> 24) & 0xFF) * Mth.clamp(alpha, 0.0f, 1.0f));
		return (a << 24) | (argb & 0xFFFFFF);
	}

	static int mix(int a, int b, float t) {
		t = Mth.clamp(t, 0.0f, 1.0f);
		int out = 0;
		for (int shift = 0; shift <= 24; shift += 8) {
			int ca = (a >>> shift) & 0xFF;
			int cb = (b >>> shift) & 0xFF;
			out |= Math.round(ca + (cb - ca) * t) << shift;
		}
		return out;
	}

	/** A bar's track: a dark groove with a lighter rim. */
	static void track(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, float alpha) {
		graphics.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, alpha(TRACK_EDGE, alpha));
		graphics.fill(x0, y0, x1, y1, alpha(TRACK, alpha));
	}

	/** A fill with sub-pixel ends, so things sliding along a bar move smoothly rather than a pixel at a time. */
	static void fillSmooth(GuiGraphicsExtractor graphics, float x0, int y0, float x1, int y1, int color) {
		if (x1 <= x0) {
			return;
		}
		graphics.pose().pushMatrix();
		graphics.pose().translate(x0, 0.0f);
		graphics.pose().scale((x1 - x0) / 64.0f, 1.0f);
		graphics.fill(0, y0, 64, y1, color);
		graphics.pose().popMatrix();
	}
}
