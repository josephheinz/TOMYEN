package com.jheinz.tomyen.client.hogfire;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import com.jheinz.tomyen.client.minigun.MinigunHud;
import com.jheinz.tomyen.hogfire.Hogfire;
import com.jheinz.tomyen.hogfire.HogfireFlight;
import com.jheinz.tomyen.minigun.MinigunState;
import com.jheinz.tomyen.minigun.RotaryGun;

/**
 * The Hogfire's flight panel, centred above the hotbar's status bars (clear of the Neuralink's panels at the left):
 * fuel gauge with the minutes left at this throttle, throttle bar, airspeed, altitude, and an afterburner light.
 * With a gun mount or Fae Dust on it, a strip along the top: the gun's heat and the 5.56 rounds the pilot carries, and
 * how long it stays fae-light. Above it all, a flashing STALL warning while it's too slow to fly.
 */
public final class HogfireHud {
	private static final int WIDTH = 182;
	private static final int HEIGHT = 24;
	/** Bottom of the panel, up from the bottom of the screen: above health, armour and the mount's hearts. */
	private static final int BOTTOM_OFFSET = 62;
	private static final int BACK = 0xA0101418;
	private static final int FRAME = 0xFF5A6470;
	private static final int TEXT = 0xFFE6EDF3;
	private static final int DIM = 0xFF8B98A5;
	private static final int FUEL = 0xFFFFB13B;
	private static final int THROTTLE = 0xFF59C3FF;
	private static final int WARNING = 0xFFFF4A3D;
	private static final int AFTERBURNER = 0xFF7DA8FF;
	private static final int BAR_BACK = 0xFF2A3038;
	private static final int FAE = 0xFFE7A8FF;
	/** The gun and Fae Dust strip, when there's either. */
	private static final int STRIP_HEIGHT = 12;

	private HogfireHud() {
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null
				|| !(minecraft.player.getVehicle() instanceof Hogfire hogfire) || hogfire.pilot() != minecraft.player) {
			return;
		}
		Font font = minecraft.font;
		boolean blink = Util.getMillis() % 500 < 250;
		int x0 = graphics.guiWidth() / 2 - WIDTH / 2, x1 = x0 + WIDTH;
		int y1 = graphics.guiHeight() - BOTTOM_OFFSET, y0 = y1 - HEIGHT;
		graphics.fill(x0, y0, x1, y1, BACK);
		graphics.outline(x0, y0, WIDTH, HEIGHT, FRAME);

		// Fuel: the bar, and minutes left at the current throttle (or the tank's share when idling).
		float fuel = (float) hogfire.fuel() / HogfireFlight.MAX_FUEL;
		boolean low = fuel < 0.1f;
		graphics.text(font, Component.translatable("hud.tomyen.hogfire.fuel"), x0 + 4, y0 + 3, DIM, false);
		bar(graphics, x0 + 4, y0 + 14, 56, fuel, low && blink ? WARNING : FUEL);
		float burn = (hogfire.throttle() + (hogfire.afterburner() ? HogfireFlight.AFTERBURNER_BURN : 0.0f)) * HogfireFlight.burnFactor(hogfire);
		if (burn > 0.0f && hogfire.fuel() > 0) {
			int seconds = (int) (hogfire.fuel() / burn / 20.0f);
			String left = seconds / 60 + ":" + String.format("%02d", seconds % 60);
			graphics.text(font, left, x0 + 60 - font.width(left), y0 + 3, low ? WARNING : TEXT, false);
		}

		// Throttle.
		graphics.text(font, Component.translatable("hud.tomyen.hogfire.throttle"), x0 + 66, y0 + 3, DIM, false);
		bar(graphics, x0 + 66, y0 + 14, 42, hogfire.throttle(), THROTTLE);
		String percent = Mth.floor(hogfire.throttle() * 100.0f) + "%";
		graphics.text(font, percent, x0 + 108 - font.width(percent), y0 + 3, TEXT, false);

		// Airspeed and altitude.
		int speed = Math.round(hogfire.airspeed() * 20.0f);
		graphics.text(font, Component.translatable("hud.tomyen.hogfire.speed", speed), x0 + 114, y0 + 3, TEXT, false);
		graphics.text(font, Component.translatable("hud.tomyen.hogfire.altitude", Mth.floor(hogfire.getY())), x0 + 114, y0 + 13, TEXT, false);
		if (hogfire.afterburner()) {
			graphics.fill(x1 - 9, y0 + 3, x1 - 4, y0 + 8, AFTERBURNER);
			graphics.fill(x1 - 9, y0 + 3, x1 - 4, y0 + 4, 0xFFFFFFFF);
		}

		if (hogfire.hasGunMount() || hogfire.faeLight()) {
			y0 -= STRIP_HEIGHT;
			strip(graphics, font, minecraft, hogfire, x0, y0, blink);
		}

		boolean stalling = !hogfire.onGround() && !hogfire.isInWater() && hogfire.airspeed() < HogfireFlight.stallSpeed(hogfire);
		if (stalling && blink) {
			Component stall = Component.translatable("hud.tomyen.hogfire.stall");
			int w = font.width(stall) + 8;
			graphics.fill(graphics.guiWidth() / 2 - w / 2, y0 - 14, graphics.guiWidth() / 2 + w / 2, y0 - 2, WARNING);
			graphics.centeredText(font, stall, graphics.guiWidth() / 2, y0 - 12, 0xFFFFFFFF);
		} else if (hogfire.afterburner()) {
			graphics.centeredText(font, Component.translatable("hud.tomyen.hogfire.afterburner"), graphics.guiWidth() / 2, y0 - 11, AFTERBURNER);
		} else if (hogfire.fuel() <= 0) {
			graphics.centeredText(font, Component.translatable("hud.tomyen.hogfire.gliding"), graphics.guiWidth() / 2, y0 - 11, FUEL);
		}
	}

	/**
	 * The strip on top of the panel: the gun mount's heat bar and the rounds the pilot has for it (OVERHEAT flashing
	 * when it's locked out) on the left, and the fae-light time left on the right.
	 */
	private static void strip(GuiGraphicsExtractor graphics, Font font, Minecraft minecraft, Hogfire hogfire, int x0, int y0, boolean blink) {
		graphics.fill(x0, y0, x0 + WIDTH, y0 + STRIP_HEIGHT + 1, BACK);
		graphics.outline(x0, y0, WIDTH, STRIP_HEIGHT + 1, FRAME);
		if (hogfire.hasGunMount()) {
			MinigunState gun = hogfire.gunState();
			graphics.text(font, Component.translatable("hud.tomyen.hogfire.gun"), x0 + 4, y0 + 3, DIM, false);
			MinigunHud.heatBar(graphics, x0 + 26, y0 + 5, 40, gun);
			int rounds = RotaryGun.carried(minecraft.player);
			Component count = gun.overheated() ? Component.translatable("hud.tomyen.minigun.overheated")
					: Component.translatable("hud.tomyen.minigun.rounds", rounds);
			int colour = gun.overheated() ? (blink ? WARNING : DIM) : rounds == 0 && !minecraft.player.hasInfiniteMaterials() ? WARNING : TEXT;
			graphics.text(font, count, x0 + 70, y0 + 3, colour, false);
		}
		if (hogfire.faeLight()) {
			int seconds = hogfire.faeTicks() / 20;
			String left = seconds / 60 + ":" + String.format("%02d", seconds % 60);
			Component fae = Component.translatable("hud.tomyen.hogfire.fae", left);
			graphics.text(font, fae, x0 + WIDTH - 4 - font.width(fae), y0 + 3, FAE, false);
		}
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
