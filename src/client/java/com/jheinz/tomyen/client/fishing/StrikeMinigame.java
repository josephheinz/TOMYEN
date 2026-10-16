package com.jheinz.tomyen.client.fishing;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import com.jheinz.tomyen.fishing.FightParams;
import com.jheinz.tomyen.fishing.Fishing;
import com.jheinz.tomyen.fishing.FishingNetworking.Outcome;

/**
 * Timing strikes (darting fish). A marker sweeps back and forth along the bar; press reel while it's inside the
 * zone to strike. Every hit, the fish darts: the zone jumps somewhere else (and from difficulty 3, shrinks). From
 * difficulty 2 the zone also drifts while you wait. N hits before M misses lands it; a barbed hook allows more
 * misses, a strong line forgives near misses, and a servo reel slows the marker as it crosses the zone.
 * Perfect: no misses.
 */
final class StrikeMinigame extends FishingMinigame {
	private final float speed;
	private final int hitsNeeded;
	private final int missesAllowed;
	private final float tolerance;

	private float marker;
	private float markerBefore;
	private int direction = 1;
	private float zoneBase;
	private float zone;
	private float zoneBefore;
	private float width;
	private int hits;
	private int misses;
	private int lockout;
	private int hitFlash;
	private int missFlash;
	private float lastStrike = -1.0f;

	StrikeMinigame(FightParams params) {
		super(params);
		int d = params.difficulty();
		speed = (0.022f + 0.006f * d) * params.speed();
		width = Mth.clamp((0.10f - 0.012f * (d - 1)) * params.zone(), 0.03f, 0.2f);
		hitsNeeded = FightParams.strikeHits(d, params.length());
		missesAllowed = FightParams.strikeMisses(d, params.escape(), params.length());
		tolerance = 0.012f * params.line();
		marker = random.nextFloat() * 0.2f;
		markerBefore = marker;
		zoneBase = 0.55f + random.nextFloat() * 0.3f;
		zone = zoneBase;
		zoneBefore = zone;
	}

	private boolean inZone() {
		return Math.abs(marker - zone) <= width + tolerance;
	}

	@Override
	void play(int presses, boolean held) {
		markerBefore = marker;
		zoneBefore = zone;
		int d = params.difficulty();
		float step = speed * effort() * (Math.abs(marker - zone) <= width ? 1.0f - 0.5f * params.assist() : 1.0f);
		marker += direction * step;
		if (marker > 1.0f) {
			marker = 2.0f - marker;
			direction = -1;
		} else if (marker < 0.0f) {
			marker = -marker;
			direction = 1;
		}
		if (d >= 2) {
			float drift = (noise((age - FightParams.INTRO_TICKS) * 0.035f * params.speed(), 2) * 2.0f - 1.0f) * 0.035f * (d - 1) * (1.0f + params.power());
			zone = Mth.clamp(zoneBase + drift, width + 0.02f, 1.0f - width - 0.02f);
		}
		hitFlash = Math.max(0, hitFlash - 1);
		missFlash = Math.max(0, missFlash - 1);
		if (lockout > 0) {
			lockout--;
			return;
		}
		if (presses <= 0) {
			return;
		}
		lastStrike = marker;
		if (inZone()) {
			hits++;
			hitFlash = 8;
			lockout = FightParams.STRIKE_LOCKOUT;
			FishingClient.sound(Fishing.STRIKE_HIT, 0.7f, 0.9f + 0.12f * hits);
			FishingClient.sound(Fishing.REEL_CLICK, 0.4f, 1.4f);
			if (hits >= hitsNeeded) {
				end(Outcome.CAUGHT);
				return;
			}
			// The fish darts: somewhere at least a quarter of the bar from the marker.
			float next = zone;
			for (int tries = 0; tries < 24 && Math.abs(next - marker) < 0.25f; tries++) {
				next = width + 0.02f + random.nextFloat() * (1.0f - 2.0f * (width + 0.02f));
			}
			zoneBase = next;
			zone = next;
			zoneBefore = next;
			if (d >= 3) {
				// It shrinks by the same total over a long fight as a short one, spread across more hits.
				width = Math.max(0.03f, width * (float) Math.pow(0.92, 6.0 / Math.max(6, hitsNeeded)));
			}
		} else {
			misses++;
			missFlash = 8;
			perfect = false;
			lockout = FightParams.MISS_LOCKOUT;
			FishingClient.sound(Fishing.STRIKE_MISS, 0.7f, 0.8f);
			if (misses >= missesAllowed) {
				end(Outcome.ESCAPED);
			}
		}
	}

	@Override
	float tug() {
		return Math.min(1.0f, (inZone() ? 0.85f : 0.35f + 0.2f * Math.abs(Mth.sin(age * 0.3f))) * effort());
	}

	@Override
	float progress() {
		return hits / (float) hitsNeeded;
	}

	@Override
	int bodyHeight() {
		return 24;
	}

	@Override
	Component hint(Component keys) {
		return Component.translatable("hud.tomyen.fishing.hint.strikes", keys);
	}

	@Override
	void extract(GuiGraphicsExtractor graphics, Font font, int x, int y, int barWidth, float partial, float alpha) {
		int x1 = x + barWidth;
		int barTop = y + 3;
		int barBottom = y + 12;
		track(graphics, x, barTop, x1, barBottom, alpha);
		if (missFlash > 0) {
			graphics.fill(x, barTop, x1, barBottom, alpha(RED, alpha * missFlash / 16.0f));
		}

		float z = Mth.lerp(partial, zoneBefore, zone);
		int zoneColor = mix(GREEN, GREEN_LIGHT, hitFlash / 8.0f);
		fillSmooth(graphics, x + barWidth * (z - width), barTop, x + barWidth * (z + width), barBottom, alpha(zoneColor, alpha));
		fillSmooth(graphics, x + barWidth * (z - width) + 1, barTop + 1, x + barWidth * (z + width) - 1, barTop + 3,
				alpha(GREEN_LIGHT, alpha * 0.5f));

		// Where the last strike landed, fading.
		if (lastStrike >= 0.0f && (hitFlash > 0 || missFlash > 0)) {
			float sx = x + barWidth * lastStrike;
			int color = hitFlash > 0 ? GREEN_LIGHT : RED_LIGHT;
			fillSmooth(graphics, sx - 0.5f, barTop - 2, sx + 0.5f, barBottom + 2, alpha(color, alpha * Math.max(hitFlash, missFlash) / 8.0f));
		}

		float m = Mth.lerp(partial, markerBefore, marker);
		float mx = x + barWidth * m;
		int markerColor = alpha(lockout > 0 && missFlash > 0 ? RED_LIGHT : WHITE, alpha);
		fillSmooth(graphics, mx - 1.0f, barTop - 3, mx + 1.0f, barBottom + 3, markerColor);
		fillSmooth(graphics, mx - 2.0f, barBottom + 2, mx + 2.0f, barBottom + 4, markerColor);

		// Hits to land on the left, misses left on the right.
		// A long fight's many hits squeeze their pips narrower to fit beside the misses.
		int py = y + 18;
		float pitch = Math.min(9.0f, (barWidth - missesAllowed * 9 - 6) / (float) hitsNeeded);
		for (int i = 0; i < hitsNeeded; i++) {
			float px = x + i * pitch;
			fillSmooth(graphics, px, py, px + pitch - Math.min(2.0f, pitch * 0.25f), py + 4, alpha(i < hits ? PROGRESS : TRACK_EDGE, alpha));
		}
		for (int i = 0; i < missesAllowed; i++) {
			int px = x1 - 7 - i * 9;
			graphics.fill(px, py, px + 7, py + 4, alpha(i < misses ? RED : TRACK_EDGE, alpha));
		}
	}
}
