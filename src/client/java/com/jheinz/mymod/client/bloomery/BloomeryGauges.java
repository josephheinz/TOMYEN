package com.jheinz.mymod.client.bloomery;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import com.jheinz.mymod.bloomery.BloomeryBlockEntity;
import com.jheinz.mymod.bloomery.BloomeryBlockEntity.Fuel;
import com.jheinz.mymod.bloomery.BloomeryBlockEntity.Stroke;

/**
 * The Bloomery's two gauges, drawn the same on its screen and on the HUD. The thermometer: a column from dull red to
 * white-hot, marks at a bloom heat (amber) and a white heat (white), and a grey tick at the most the burning fuel
 * allows. The bellows gauge: the bag filling after a stroke (grey), the window for a good stroke (green, and the bar
 * goes green while you're in it), and late (amber).
 */
public final class BloomeryGauges {
	/** Ticks the bellows gauge spans: the refill, the window, and a little of late. */
	public static final int SPAN = BloomeryBlockEntity.REFILL_TICKS + BloomeryBlockEntity.WINDOW_TICKS + 8;
	/** Ticks a stroke's verdict shows for. */
	private static final int VERDICT_TICKS = 16;

	private static final int FRAME = 0xFF2A211C;
	private static final int GLASS = 0xFF1C1714;
	private static final int FILLING = 0xFF8B95A1;
	private static final int WINDOW_DIM = 0xFF294A2A;
	private static final int WINDOW = 0xFF6EE06A;
	private static final int LATE = 0xFFE8A33C;
	private static final int EARLY = 0xFFFF5E57;
	private static final int BLOOM_MARK = 0xFFFFB43A;
	private static final int WHITE_MARK = 0xFFFFFFFF;
	private static final int CEILING_MARK = 0xFFB0A8A0;

	private BloomeryGauges() {
	}

	/** The colour of the hot column at a temperature: dull red, orange, yellow, white. */
	public static int heatColour(float temperature) {
		float t = Mth.clamp((temperature - 400.0f) / (BloomeryBlockEntity.SCALE_TEMPERATURE - 400.0f), 0.0f, 1.0f);
		int[] stops = {0xFF6E1A0E, 0xFFC2360C, 0xFFF07A1E, 0xFFFFC94A, 0xFFFFF6D8};
		float scaled = t * (stops.length - 1);
		int i = Math.min(stops.length - 2, (int) scaled);
		return lerp(stops[i], stops[i + 1], scaled - i);
	}

	private static int lerp(int a, int b, float t) {
		int r = (int) Mth.lerp(t, a >> 16 & 255, b >> 16 & 255);
		int g = (int) Mth.lerp(t, a >> 8 & 255, b >> 8 & 255);
		int bl = (int) Mth.lerp(t, a & 255, b & 255);
		return 0xFF000000 | r << 16 | g << 8 | bl;
	}

	private static int level(int y0, int y1, float temperature) {
		return y1 - Math.round(Mth.clamp(temperature / BloomeryBlockEntity.SCALE_TEMPERATURE, 0.0f, 1.0f) * (y1 - y0));
	}

	/** A vertical thermometer in (x0, y0)-(x1, y1). */
	public static void thermometer(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, float temperature, Fuel fuel) {
		graphics.fill(x0, y0, x1, y1, GLASS);
		int top = level(y0, y1, temperature);
		if (top < y1) {
			graphics.fillGradient(x0, top, x1, y1, heatColour(temperature), 0xFF6E1A0E);
		}
		mark(graphics, x0, x1, level(y0, y1, BloomeryBlockEntity.BLOOM_TEMPERATURE), BLOOM_MARK);
		mark(graphics, x0, x1, level(y0, y1, BloomeryBlockEntity.WHITE_HEAT), WHITE_MARK);
		if (fuel != Fuel.NONE) {
			int y = level(y0, y1, fuel.ceiling);
			graphics.fill(x1 + 1, y, x1 + 3, y + 1, CEILING_MARK);
		}
	}

	private static void mark(GuiGraphicsExtractor graphics, int x0, int x1, int y, int colour) {
		graphics.fill(x0 - 3, y, x0 - 1, y + 1, colour);
		graphics.fill(x0, y, x1, y + 1, colour & 0x80FFFFFF);
	}

	/** A horizontal heat bar in (x0, y0)-(x1, y1), for the HUD: the same marks as the thermometer. */
	public static void heatBar(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, float temperature, Fuel fuel) {
		graphics.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, FRAME);
		graphics.fill(x0, y0, x1, y1, GLASS);
		int w = x1 - x0;
		int filled = Math.round(Mth.clamp(temperature / BloomeryBlockEntity.SCALE_TEMPERATURE, 0.0f, 1.0f) * w);
		if (filled > 0) {
			graphics.fill(x0, y0, x0 + filled, y1, heatColour(temperature));
		}
		int bloom = x0 + Math.round((float) BloomeryBlockEntity.BLOOM_TEMPERATURE / BloomeryBlockEntity.SCALE_TEMPERATURE * w);
		int white = x0 + Math.round((float) BloomeryBlockEntity.WHITE_HEAT / BloomeryBlockEntity.SCALE_TEMPERATURE * w);
		graphics.fill(bloom, y0 - 2, bloom + 1, y1 + 2, BLOOM_MARK);
		graphics.fill(white, y0 - 2, white + 1, y1 + 2, WHITE_MARK);
		if (fuel != Fuel.NONE) {
			int ceiling = x0 + Math.round((float) fuel.ceiling / BloomeryBlockEntity.SCALE_TEMPERATURE * w);
			graphics.fill(ceiling, y1, ceiling + 1, y1 + 2, CEILING_MARK);
		}
	}

	/** The bellows gauge in (x0, y0)-(x1, y1), {@code since} ticks after the last stroke. */
	public static void breath(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, float since) {
		int w = x1 - x0;
		int windowStart = x0 + Math.round((float) BloomeryBlockEntity.REFILL_TICKS / SPAN * w);
		int windowEnd = x0 + Math.round((float) (BloomeryBlockEntity.REFILL_TICKS + BloomeryBlockEntity.WINDOW_TICKS) / SPAN * w);
		graphics.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, FRAME);
		graphics.fill(x0, y0, x1, y1, GLASS);
		graphics.fill(windowStart, y0, windowEnd, y1, WINDOW_DIM);
		boolean inWindow = since >= BloomeryBlockEntity.REFILL_TICKS && since <= BloomeryBlockEntity.REFILL_TICKS + BloomeryBlockEntity.WINDOW_TICKS;
		int colour = since < BloomeryBlockEntity.REFILL_TICKS ? FILLING : inWindow ? WINDOW : LATE;
		int filled = Math.round(Mth.clamp(since / SPAN, 0.0f, 1.0f) * w);
		if (filled > 0) {
			graphics.fill(x0, y0 + 1, x0 + filled, y1 - 1, colour);
		}
		if (inWindow) {
			// In the green: the frame lights up too.
			graphics.fill(x0 - 1, y0 - 1, x1 + 1, y0, WINDOW);
			graphics.fill(x0 - 1, y1, x1 + 1, y1 + 1, WINDOW);
		}
		graphics.fill(windowStart, y0 - 1, windowStart + 1, y1 + 1, WINDOW);
		graphics.fill(windowEnd - 1, y0 - 1, windowEnd, y1 + 1, WINDOW);
	}

	/** What the last stroke was, for a moment after it: "Too soon!", "Good! x3" or "Late". */
	public static @Nullable Component strokeText(Stroke stroke, int combo, int since) {
		if (since > VERDICT_TICKS) {
			return null;
		}
		return switch (stroke) {
			case EARLY -> Component.translatable("container.mymod.bloomery.stroke.early");
			case GOOD -> combo > 1 ? Component.translatable("container.mymod.bloomery.stroke.combo", combo)
					: Component.translatable("container.mymod.bloomery.stroke.good");
			case LATE -> Component.translatable("container.mymod.bloomery.stroke.late");
			default -> null;
		};
	}

	public static int strokeColour(Stroke stroke) {
		return switch (stroke) {
			case EARLY -> EARLY;
			case GOOD -> 0xFF3FA83A;
			default -> 0xFFC07A10;
		};
	}
}
