package com.jheinz.mymod.client.neuralink;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/** The Neuralink's look, shared by its interface and its HUD: cyan glass, bright corner brackets, magenta glitches. */
public final class NeuralStyle {
	public static final int CYAN = 0x00E5FF;
	public static final int GLITCH = 0xFF2A6D;
	public static final int HOT = 0xFF5A1F;
	public static final int TEXT = 0xFFE6FCFF;
	public static final int DIM = 0xFF4FA8B8;
	public static final int DARK = 0xFF2A5560;
	/** The dark glass behind panels. */
	public static final int GLASS_TOP = 0xC80A1420;
	public static final int GLASS_BOTTOM = 0xC805090F;

	private NeuralStyle() {
	}

	public static int argb(int alpha, int rgb) {
		return (Mth.clamp(alpha, 0, 255) << 24) | (rgb & 0xFFFFFF);
	}

	/** 0 to 1 and back, once per {@code periodMillis}. */
	public static float wave(long now, float periodMillis, float phase) {
		return 0.5f + 0.5f * Mth.sin((float) (now % 100000L) / periodMillis * Mth.TWO_PI + phase);
	}

	public static int mix(int from, int to, float amount) {
		int r = (int) Mth.lerp(amount, (from >> 16) & 255, (to >> 16) & 255);
		int g = (int) Mth.lerp(amount, (from >> 8) & 255, (to >> 8) & 255);
		int b = (int) Mth.lerp(amount, from & 255, to & 255);
		return (r << 16) | (g << 8) | b;
	}

	/** L-shaped marks in the four corners of a box. */
	public static void brackets(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int length, int thickness, int color) {
		graphics.fill(x0, y0, x0 + length, y0 + thickness, color);
		graphics.fill(x0, y0, x0 + thickness, y0 + length, color);
		graphics.fill(x1 - length, y0, x1, y0 + thickness, color);
		graphics.fill(x1 - thickness, y0, x1, y0 + length, color);
		graphics.fill(x0, y1 - thickness, x0 + length, y1, color);
		graphics.fill(x0, y1 - length, x0 + thickness, y1, color);
		graphics.fill(x1 - length, y1 - thickness, x1, y1, color);
		graphics.fill(x1 - thickness, y1 - length, x1, y1, color);
	}

	/** A small HUD panel: dark glass, a faint outline and corner brackets in the accent colour. */
	public static void panel(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int accent) {
		graphics.fillGradient(x0, y0, x1, y1, GLASS_TOP, GLASS_BOTTOM);
		graphics.outline(x0, y0, x1 - x0, y1 - y0, argb(0x50, accent));
		brackets(graphics, x0, y0, x1, y1, 5, 1, argb(0xFF, accent));
	}

	/** A one-pixel line between two points, drawn as a run of pixels. */
	public static void line(GuiGraphicsExtractor graphics, float x0, float y0, float x1, float y1, int color) {
		int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0))));
		for (int i = 0; i <= steps; i++) {
			int x = Math.round(Mth.lerp(i / (float) steps, x0, x1));
			int y = Math.round(Mth.lerp(i / (float) steps, y0, y1));
			graphics.fill(x, y, x + 1, y + 1, color);
		}
	}

	/** A ring of pixels, optionally only the part from {@code startTurn} to {@code endTurn} (0 is straight up, clockwise). */
	public static void ring(GuiGraphicsExtractor graphics, float cx, float cy, float radius, float startTurn, float endTurn, int color) {
		int steps = Math.max(8, (int) (radius * Mth.TWO_PI * (endTurn - startTurn)));
		for (int i = 0; i <= steps; i++) {
			float angle = Mth.TWO_PI * Mth.lerp(i / (float) steps, startTurn, endTurn);
			int x = Math.round(cx + Mth.sin(angle) * radius);
			int y = Math.round(cy - Mth.cos(angle) * radius);
			graphics.fill(x, y, x + 1, y + 1, color);
		}
	}
}
