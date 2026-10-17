package com.jheinz.tomyen.client.bathtub;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import com.jheinz.tomyen.bathtub.BathtubBoat;

/**
 * The Bathtub Boat's dash, for whoever's riding it, centred above the hotbar's status bars: the fuel gauge with the time
 * left at this throttle, the throttle, the speed in knots, and underneath a line saying what it's doing (rowing with an
 * empty tank, waddling ashore) or how to honk.
 */
public final class BathtubHud {
	private static final int WIDTH = 182;
	private static final int HEIGHT = 24;
	/** Clear of the hotbar, the hearts and the action bar line above them (where the dismount hint shows). */
	private static final int BOTTOM_OFFSET = 78;
	/** Where the speedometer's box starts, right of the throttle. */
	private static final int SPEEDO = 124;
	private static final int BACK = 0xA0101418;
	private static final int FRAME = 0xFFC9D2DB;
	private static final int TEXT = 0xFFE6EDF3;
	private static final int DIM = 0xFF8B98A5;
	private static final int FUEL = 0xFFF0A830;
	private static final int THROTTLE = 0xFFE5432C;
	private static final int WARNING = 0xFFFF4A3D;
	private static final int BAR_BACK = 0xFF2A3038;
	private static final int DUCK = 0xFFFFDE59;
	/** Blocks a tick to knots: 20 m/s per block a tick, 1.944 knots a m/s. */
	private static final double KNOTS = 20.0 * 1.944;

	private BathtubHud() {
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || !(minecraft.player.getVehicle() instanceof BathtubBoat boat)) {
			return;
		}
		Font font = minecraft.font;
		boolean blink = Util.getMillis() % 500 < 250;
		int x0 = graphics.guiWidth() / 2 - WIDTH / 2;
		int y1 = graphics.guiHeight() - BOTTOM_OFFSET, y0 = y1 - HEIGHT;
		graphics.fill(x0, y0, x0 + WIDTH, y1, BACK);
		graphics.outline(x0, y0, WIDTH, HEIGHT, FRAME);

		// Fuel, and the time left at this throttle.
		float fuel = (float) boat.fuel() / BathtubBoat.TANK;
		boolean free = minecraft.player.hasInfiniteMaterials();
		boolean low = !free && fuel < 0.1f;
		graphics.text(font, Component.translatable("hud.tomyen.bathtub_boat.fuel"), x0 + 4, y0 + 3, DIM, false);
		bar(graphics, x0 + 4, y0 + 14, 60, free ? 1.0f : fuel, low && blink ? WARNING : FUEL);
		float burn = boat.throttle() * BathtubBoat.BURN + BathtubBoat.IDLE_BURN;
		if (!free && boat.fuel() > 0 && boat.engineRunning()) {
			int seconds = (int) (boat.fuel() / burn / 20.0f);
			String left = seconds >= 6000 ? "--:--" : seconds / 60 + ":" + String.format("%02d", seconds % 60);
			graphics.text(font, left, x0 + 64 - font.width(left), y0 + 3, low ? WARNING : TEXT, false);
		}

		// Throttle.
		graphics.text(font, Component.translatable("hud.tomyen.bathtub_boat.throttle"), x0 + 70, y0 + 3, DIM, false);
		bar(graphics, x0 + 70, y0 + 14, 46, boat.throttle(), THROTTLE);

		// Speed, boxed off on the right so it doesn't read as the throttle's.
		int knots = (int) Math.round(boat.speed() * KNOTS);
		int right = x0 + WIDTH - 5;
		graphics.fill(x0 + SPEEDO, y0 + 3, x0 + SPEEDO + 1, y1 - 3, DIM);
		Component speed = Component.translatable("hud.tomyen.bathtub_boat.speed", knots);
		graphics.text(font, speed, right - font.width(speed), y0 + 3, TEXT, false);
		Component unit = Component.translatable("hud.tomyen.bathtub_boat.kn");
		graphics.text(font, unit, right - font.width(unit), y0 + 13, DIM, false);

		Component status;
		int colour;
		if (!boat.isInWater()) {
			status = Component.translatable("hud.tomyen.bathtub_boat.ashore");
			colour = DUCK;
		} else if (!boat.engineRunning() && boat.getControllingPassenger() == minecraft.player) {
			status = Component.translatable("hud.tomyen.bathtub_boat.rowing");
			colour = blink ? WARNING : FUEL;
		} else {
			status = Component.translatable("hud.tomyen.bathtub_boat.honk", Component.keybind("key.tomyen.honk"));
			colour = DIM;
		}
		graphics.centeredText(font, status, graphics.guiWidth() / 2, y0 - 11, colour);
	}

	private static void bar(GuiGraphicsExtractor graphics, int x, int y, int width, float fill, int colour) {
		graphics.fill(x, y, x + width, y + 5, BAR_BACK);
		int filled = Mth.ceil(Mth.clamp(fill, 0.0f, 1.0f) * width);
		if (filled > 0) {
			graphics.fill(x, y, x + filled, y + 5, colour);
			graphics.fill(x, y, x + filled, y + 1, 0x40FFFFFF);
		}
	}
}
