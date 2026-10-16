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
 * The mash meter (sinkers and floaters). The meter keeps falling as the fish dives; every tap of reel kicks it back
 * up. Keep it above the line until the timer runs out. Too long under the line and the fish is gone (a barbed hook
 * gives longer); hammer it into the red cap at the top for too long and the line snaps (a strong line gives longer).
 * A floater's line drifts up and down, and it bobs the meter about. A geared reel kicks harder; a servo reel helps
 * when the meter nears the line. Perfect: never under the line.
 */
final class MashMinigame extends FishingMinigame {
	private static final float TOP_CAP = 0.94f;
	private static final float DAMPING = 0.9f;

	private final float gravity;
	private final float kick;
	private final float base;
	private final float slipGrace;
	private final float snapGrace;
	private final boolean floater;
	private final int duration;

	private float level = 0.62f;
	private float levelBefore = 0.62f;
	private float velocity;
	private float line;
	private float lineBefore;
	private float slip;
	private float strain;
	private int remaining;
	private int tapFlash;

	MashMinigame(FightParams params) {
		super(params);
		int d = params.difficulty();
		gravity = (0.0016f + 0.0004f * d) * params.speed();
		kick = 0.012f * params.reel();
		base = Mth.clamp(0.35f + 0.04f * (d - 1) - (params.zone() - 1.0f) * 0.25f, 0.2f, 0.6f);
		slipGrace = 14.0f + params.escape() * 20.0f;
		snapGrace = 10.0f + params.line() * 20.0f;
		floater = params.behavior() == FishBehavior.FLOATER;
		duration = FightParams.mashTicks(d, params.length());
		remaining = duration;
		line = base;
		lineBefore = base;
	}

	@Override
	void play(int presses, boolean held) {
		levelBefore = level;
		lineBefore = line;
		float t = (age - FightParams.INTRO_TICKS) * 0.04f * params.speed();
		if (floater) {
			line = Mth.clamp(base + 0.14f * (noise(t, 3) * 2.0f - 1.0f), 0.15f, 0.75f);
			velocity += (noise(t * 1.7f, 4) * 2.0f - 1.0f) * 0.0018f * params.speed();
		}
		velocity -= gravity * (1.0f + 0.3f * params.power()) * effort();
		if (presses > 0) {
			velocity += kick * Math.min(presses, 2);
			tapFlash = 4;
			FishingClient.sound(Fishing.REEL_CLICK, 0.3f, 0.9f + 0.7f * level);
		}
		if (level < line + 0.06f) {
			velocity += params.assist() * 0.0012f;
		}
		velocity *= DAMPING;
		level += velocity;
		if (level > 1.0f) {
			level = 1.0f;
			velocity = Math.min(velocity, 0.0f);
		} else if (level < 0.0f) {
			level = 0.0f;
			velocity = Math.max(velocity, 0.0f);
		}
		tapFlash = Math.max(0, tapFlash - 1);

		if (level < line) {
			slip += 1.0f;
			perfect = false;
		} else {
			slip = Math.max(0.0f, slip - 0.5f);
		}
		strain = level > TOP_CAP ? strain + 1.0f : Math.max(0.0f, strain - 0.5f);
		if (strain > 0 && age % 5 == 0) {
			FishingClient.sound(Fishing.LINE_CREAK, 0.5f, 0.8f + strain / snapGrace * 0.5f);
		}

		remaining--;
		if (slip > slipGrace) {
			end(Outcome.ESCAPED);
		} else if (strain > snapGrace) {
			end(Outcome.SNAPPED);
		} else if (remaining <= 0) {
			end(Outcome.CAUGHT);
		}
	}

	@Override
	float progress() {
		return 1.0f - remaining / (float) duration;
	}

	@Override
	float tug() {
		return Mth.clamp(1.0f - level + 0.2f, 0.0f, 1.0f);
	}

	@Override
	int bodyHeight() {
		return 36;
	}

	@Override
	Component hint(Component keys) {
		return Component.translatable("hud.tomyen.fishing.hint.mash", keys);
	}

	@Override
	void extract(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, float partial, float alpha) {
		// The meter, standing up at the left.
		int mx0 = x;
		int mx1 = x + 12;
		int top = y + 1;
		int bottom = y + 35;
		int height = bottom - top;
		track(graphics, mx0, top, mx1, bottom, alpha);
		int capY = top + Math.round(height * (1.0f - TOP_CAP));
		graphics.fill(mx0, top, mx1, capY, alpha(mix(RED, RED_LIGHT, strain / snapGrace), alpha));

		float lv = Mth.lerp(partial, levelBefore, level);
		float ln = Mth.lerp(partial, lineBefore, line);
		boolean under = lv < ln;
		int fillColor = under ? mix(AMBER, RED_LIGHT, slip / slipGrace) : mix(GREEN, GREEN_LIGHT, tapFlash / 4.0f);
		float fillTop = top + height * (1.0f - lv);
		graphics.pose().pushMatrix();
		graphics.pose().translate(0.0f, fillTop);
		graphics.pose().scale(1.0f, Math.max(0.0f, bottom - fillTop) / 64.0f);
		graphics.fill(mx0 + 1, 0, mx1 - 1, 64, alpha(fillColor, alpha));
		graphics.pose().popMatrix();

		// The line to stay above, run on across the panel as a faint guide.
		float lineY = top + height * (1.0f - ln);
		graphics.pose().pushMatrix();
		graphics.pose().translate(0.0f, lineY - 0.5f);
		graphics.fill(mx0 - 2, 0, mx1 + 2, 1, alpha(WHITE, alpha));
		graphics.fill(mx1 + 2, 0, mx1 + 8, 1, alpha(WHITE, alpha * 0.35f));
		graphics.pose().popMatrix();

		// Right of it: what to do, and the time left.
		int tx = mx1 + 12;
		int textColor = alpha(under ? RED_LIGHT : 0xFFD8E1EA, alpha);
		Component cue = Component.translatable(under ? "hud.tomyen.fishing.mash.under" : "hud.tomyen.fishing.mash.hold");
		if (alpha > 0.1f) {
			graphics.text(font, cue, tx, y + 6, textColor, false);
		}
		float left = Mth.clamp((remaining - partial) / duration, 0.0f, 1.0f);
		int tw = x + width - tx;
		track(graphics, tx, y + 22, tx + tw, y + 25, alpha);
		fillSmooth(graphics, tx, y + 22, tx + tw * (1.0f - left), y + 25, alpha(PROGRESS, alpha));
	}
}
