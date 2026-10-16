package com.jheinz.tomyen.client.fishing;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import com.jheinz.tomyen.fishing.FightParams;
import com.jheinz.tomyen.fishing.FishBehavior;
import com.jheinz.tomyen.fishing.Fishing;
import com.jheinz.tomyen.fishing.FishingNetworking.Outcome;

/**
 * The tension meter (steady and thrashing fish). The fish drags the needle left toward slack, the red end where it
 * shakes the hook; reeling (holding, or tapping) pushes it right, and too far right is the other red end, where the
 * line snaps. While the needle is in the green the catch bar fills; outside it the bar drains. Sitting in a red end
 * a moment too long loses the fish: how long depends on the hook (escape) and the line (snap). A thrasher also jerks
 * the needle either way now and then, after a warning shake. A servo reel nudges the needle back toward the middle.
 * Perfect: the needle never leaves the green.
 */
final class TensionMinigame extends FishingMinigame {
	private static final float RED_END = 0.08f;
	/** The reel's pull on the needle, per tick. */
	private static final float PUSH = 0.012f;
	private static final float TAP = 0.010f;
	private static final float DAMPING = 0.86f;

	private final float half;
	private final float fill;
	private final float slackGrace;
	private final float snapGrace;
	private final boolean thrasher;

	private float needle = 0.5f;
	private float needleBefore = 0.5f;
	private float velocity;
	private float progress;
	private float progressBefore;
	private float pull;
	private float slack;
	private float strain;
	private int jerkIn;
	private int shake;

	TensionMinigame(FightParams params) {
		super(params);
		int d = params.difficulty();
		half = Mth.clamp((0.16f - 0.018f * (d - 1)) * params.zone(), 0.05f, 0.3f);
		fill = params.reel() / FightParams.tensionFillTicks(d, params.length());
		slackGrace = 12.0f + params.escape() * 20.0f;
		snapGrace = 12.0f + params.line() * 20.0f;
		thrasher = params.behavior() == FishBehavior.THRASHER;
		jerkIn = nextJerk();
	}

	private int nextJerk() {
		return Math.max(12, 30 + random.nextInt(40) - 4 * params.difficulty() - Math.round(16 * params.power()));
	}

	@Override
	void play(int presses, boolean held) {
		needleBefore = needle;
		progressBefore = progress;
		int d = params.difficulty();
		float t = (age - FightParams.INTRO_TICKS) * (0.03f + 0.012f * d) * params.speed();
		float surge = noise(t, 0) * 2.0f - 1.0f;
		if (thrasher) {
			surge = Mth.clamp(surge * 0.7f + (noise(t * 2.7f, 1) * 2.0f - 1.0f) * 0.5f, -1.0f, 1.0f);
		}
		pull = Mth.clamp((0.45f + 0.05f * d + 0.12f * params.power() + (0.2f + 0.05f * d) * surge) * effort(), 0.12f, 0.97f);

		float accel = (held ? PUSH : 0.0f) - PUSH * pull;
		if (presses > 0 && !held) {
			velocity += TAP * params.speed();
		}
		accel *= params.speed();
		accel += params.assist() * ((0.5f - needle) * 0.04f - velocity * 0.15f);
		if (thrasher) {
			if (shake > 0 && --shake == 0) {
				velocity += (random.nextBoolean() ? 1 : -1) * (0.035f + 0.01f * d) * (1.0f + 0.4f * params.power()) * effort()
						* params.speed();
				jerkIn = nextJerk();
				FishingClient.sound(Fishing.LINE_CREAK, 0.7f, 0.8f);
			} else if (shake == 0 && --jerkIn <= 0) {
				shake = 8;
			}
		}
		velocity = (velocity + accel) * DAMPING;
		needle += velocity;
		if (needle < 0.0f || needle > 1.0f) {
			needle = Mth.clamp(needle, 0.0f, 1.0f);
			velocity = 0.0f;
		}

		if (Math.abs(needle - 0.5f) <= half) {
			progress += fill;
		} else {
			progress -= fill * 0.6f;
			perfect = false;
		}
		progress = Mth.clamp(progress, 0.0f, 1.0f);

		slack = needle < RED_END ? slack + 1.0f : Math.max(0.0f, slack - 0.5f);
		strain = needle > 1.0f - RED_END ? strain + 1.0f : Math.max(0.0f, strain - 0.5f);

		if (held && age % 3 == 0) {
			FishingClient.sound(Fishing.REEL_CLICK, 0.25f, 1.1f + 0.5f * progress);
		} else if (presses > 0) {
			FishingClient.sound(Fishing.REEL_CLICK, 0.3f, 1.2f + 0.4f * progress);
		}
		if (needle > 0.5f + half && age % 7 == 0) {
			FishingClient.sound(Fishing.LINE_CREAK, 0.25f + 0.5f * (needle - 0.5f), 0.7f + needle * 0.5f);
		}

		if (progress >= 1.0f) {
			end(Outcome.CAUGHT);
		} else if (slack > slackGrace) {
			end(Outcome.ESCAPED);
		} else if (strain > snapGrace) {
			end(Outcome.SNAPPED);
		}
	}

	@Override
	float tug() {
		return pull;
	}

	@Override
	float progress() {
		return progress;
	}

	@Override
	int bodyHeight() {
		return 22;
	}

	@Override
	Component hint(Component keys) {
		return Component.translatable("hud.tomyen.fishing.hint.tension", keys);
	}

	@Override
	void extract(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, float partial, float alpha) {
		int x1 = x + width;
		int barTop = y + 3;
		int barBottom = y + 12;
		track(graphics, x, barTop, x1, barBottom, alpha);

		// Red ends, glowing brighter the longer the needle sits in them.
		int redWidth = Math.round(width * RED_END);
		graphics.fill(x, barTop, x + redWidth, barBottom, alpha(mix(RED, RED_LIGHT, slack / slackGrace), alpha));
		graphics.fill(x1 - redWidth, barTop, x1, barBottom, alpha(mix(RED, RED_LIGHT, strain / snapGrace), alpha));
		// The green band, with a lighter core.
		float g0 = x + width * (0.5f - half);
		float g1 = x + width * (0.5f + half);
		fillSmooth(graphics, g0, barTop, g1, barBottom, alpha(GREEN, alpha));
		fillSmooth(graphics, g0 + 1, barTop + 1, g1 - 1, barTop + 3, alpha(GREEN_LIGHT, alpha * 0.55f));

		// The needle: eased between ticks, shaking before a thrasher's jerk.
		float n = Mth.lerp(partial, needleBefore, needle);
		float jitter = shake > 0 ? Mth.sin((age + partial) * 2.6f) * 1.2f : 0.0f;
		float nx = x + width * n + jitter;
		boolean danger = n < RED_END || n > 1.0f - RED_END;
		int needleColor = alpha(danger ? RED_LIGHT : WHITE, alpha);
		fillSmooth(graphics, nx - 1.0f, barTop - 3, nx + 1.0f, barBottom + 3, needleColor);
		fillSmooth(graphics, nx - 2.0f, barTop - 4, nx + 2.0f, barTop - 2, needleColor);

		// The catch bar.
		int py = y + 17;
		track(graphics, x, py, x1, py + 3, alpha);
		float p = Mth.lerp(partial, progressBefore, progress);
		fillSmooth(graphics, x, py, x + width * p, py + 3, alpha(PROGRESS, alpha));
	}
}
