package com.jheinz.mymod.client.tvhelmet;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.tvhelmet.TvHelmets;
import com.jheinz.mymod.tvhelmet.TvScreen;

/**
 * What the TV Helmet's wearer sees through it, in first person:
 * <ul>
 * <li>night vision on: a faint phosphor tint, scanlines, a whisper of snow and the screen's darkened rounded corners;
 * <li>in the rain: now and then the picture breaks up into snow;
 * <li>shorted out: thick snow with a dark band rolling down it, until it dries out.
 * </ul>
 * The snow is the Cathode Walker's Static Burst tiles (textures/misc/tv_static_*.png), shuffled every tick.
 */
public final class TvVisionOverlay {
	private static final Identifier[] SNOW = {MyMod.id("textures/misc/tv_static_0.png"), MyMod.id("textures/misc/tv_static_1.png"),
			MyMod.id("textures/misc/tv_static_2.png"), MyMod.id("textures/misc/tv_static_3.png")};
	private static final int TILE_TEXELS = 64;
	private static final int TEXEL = 2;
	/** Rows between scanlines. */
	private static final int SCANLINE = 3;

	private TvVisionOverlay() {
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || !minecraft.options.getCameraType().isFirstPerson() || !TvHelmets.isWearing(player)) {
			return;
		}
		TvScreen screen = TvHelmets.screen(player);
		long millis = Util.getMillis();
		int frame = (int) (millis / 50);
		int width = graphics.guiWidth(), height = graphics.guiHeight();
		if (screen.mode() == TvScreen.SHORTED) {
			snow(graphics, width, height, frame, 0.75f);
			int band = height / 5;
			int bandY = (int) (millis * height / 1100L % (height + band)) - band;
			graphics.fill(0, bandY, width, bandY + band, 0x50000000);
			return;
		}
		if (screen.mode() == TvScreen.RAIN && TvHelmetRenderer.rainGlitch(frame + player.getId() * 13)) {
			snow(graphics, width, height, frame, 0.45f);
		}
		if (!screen.seeing()) {
			return;
		}
		// The phosphor tint and a whisper of snow.
		graphics.fill(0, 0, width, height, 0x0C60D0FF);
		snow(graphics, width, height, frame, 0.05f);
		// Scanlines, drifting slowly down.
		int offset = (int) (millis / 120L % SCANLINE);
		for (int y = offset; y < height; y += SCANLINE) {
			graphics.fill(0, y, width, y + 1, 0x16000000);
		}
		// The tube's darkened corners: rings of shade closing in.
		for (int i = 0; i < 6; i++) {
			int inset = i * 3;
			int alpha = 0x28 - i * 6;
			if (alpha <= 0) {
				break;
			}
			graphics.outline(inset, inset, width - inset * 2, height - inset * 2, ARGB.color(alpha, 0x000000));
			graphics.outline(inset + 1, inset + 1, width - inset * 2 - 2, height - inset * 2 - 2, ARGB.color(alpha, 0x000000));
			graphics.outline(inset + 2, inset + 2, width - inset * 2 - 4, height - inset * 2 - 4, ARGB.color(alpha, 0x000000));
		}
	}

	private static void snow(GuiGraphicsExtractor graphics, int width, int height, int frame, float alpha) {
		int color = ARGB.white(alpha);
		int tile = TILE_TEXELS * TEXEL;
		for (int ty = 0; ty * tile < height; ty++) {
			for (int tx = 0; tx * tile < width; tx++) {
				Identifier snow = SNOW[Math.floorMod(frame + tx * 3 + ty * 7 + tx * ty, SNOW.length)];
				graphics.blit(RenderPipelines.GUI_TEXTURED, snow, tx * tile, ty * tile, 0.0f, 0.0f, tile, tile, TILE_TEXELS, TILE_TEXELS,
						TILE_TEXELS, TILE_TEXELS, color);
			}
		}
	}
}
