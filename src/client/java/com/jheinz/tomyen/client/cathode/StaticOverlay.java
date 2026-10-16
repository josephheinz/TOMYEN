package com.jheinz.tomyen.client.cathode;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;

import com.jheinz.tomyen.MyMod;

/**
 * The Static Burst's dazzle: TV snow over the whole screen, thick at first and thinning out to nothing, with a white
 * flash as it starts and a darker band rolling down it like a set losing its hold. The snow is four tiles of greyscale
 * noise (textures/misc/tv_static_*.png, from tools/gen_cathode_walker.py) stretched to chunky pixels and shuffled
 * every tick, so it never sits still.
 */
public final class StaticOverlay {
	private static final Identifier[] SNOW = {MyMod.id("textures/misc/tv_static_0.png"), MyMod.id("textures/misc/tv_static_1.png"),
			MyMod.id("textures/misc/tv_static_2.png"), MyMod.id("textures/misc/tv_static_3.png")};
	private static final int TILE_TEXELS = 64;
	/** GUI pixels per texel of snow. */
	private static final int TEXEL = 2;
	private static final long FLASH_MILLIS = 150;
	private static final float PEAK_ALPHA = 0.85f;

	private static long startMillis;
	private static long durationMillis;

	private StaticOverlay() {
	}

	/** A burst caught this player looking: snow for {@code ticks}. */
	static void start(int ticks) {
		startMillis = Util.getMillis();
		durationMillis = ticks * 50L;
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		long elapsed = Util.getMillis() - startMillis;
		if (durationMillis <= 0 || elapsed >= durationMillis) {
			return;
		}
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		float fade = 1.0f - elapsed / (float) durationMillis;
		// Holds near full for the first third, then thins away.
		float alpha = PEAK_ALPHA * Math.min(1.0f, fade * 1.5f);
		int color = ARGB.white(alpha);
		int tile = TILE_TEXELS * TEXEL;
		int frame = (int) (elapsed / 50);
		for (int ty = 0; ty * tile < height; ty++) {
			for (int tx = 0; tx * tile < width; tx++) {
				Identifier snow = SNOW[Math.floorMod(frame + tx * 3 + ty * 7 + tx * ty, SNOW.length)];
				graphics.blit(RenderPipelines.GUI_TEXTURED, snow, tx * tile, ty * tile, 0.0f, 0.0f, tile, tile, TILE_TEXELS, TILE_TEXELS,
						TILE_TEXELS, TILE_TEXELS, color);
			}
		}
		// A darker band rolling down the screen.
		int band = height / 6;
		int bandY = (int) (elapsed * height / 900L % (height + band)) - band;
		graphics.fill(0, bandY, width, bandY + band, ARGB.color((int) (0x50 * fade), 0x000000));
		if (elapsed < FLASH_MILLIS) {
			graphics.fill(0, 0, width, height, ARGB.white(0.8f * (1.0f - elapsed / (float) FLASH_MILLIS)));
		}
	}
}
