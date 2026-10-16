package com.jheinz.tomyen.client.tvhelmet;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.tvhelmet.TvEmote;
import com.jheinz.tomyen.tvhelmet.TvHelmets;

/**
 * The TV Helmet's keys: Emote Wheel (period) and TV Night Vision (comma), both only while wearing the set.
 *
 * <p>Tap the Emote Wheel key for the next emote. Hold it and a wheel of little TV screens opens round the crosshair,
 * one per emote, clockwise from the top: the mouse steers a cursor instead of the view (MouseHandlerRadialMixin), and
 * letting go picks the one it points at (or nothing, in the middle).
 */
public final class TvEmoteWheel {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(MyMod.id("tv_helmet"));
	public static final KeyMapping EMOTE_KEY = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.tomyen.tv_emote", InputConstants.Type.KEYBOARD, InputConstants.KEY_PERIOD, CATEGORY));
	public static final KeyMapping NIGHT_VISION_KEY = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.tomyen.tv_night_vision", InputConstants.Type.KEYBOARD, InputConstants.KEY_COMMA, CATEGORY));

	/** Holding the key this long opens the wheel; a shorter press is a tap. */
	private static final int HOLD_TICKS = 5;
	private static final double DEADZONE = 14.0;
	private static final double CURSOR_LIMIT = 70.0;
	private static final double CURSOR_SPEED = 0.35;
	private static final int RADIUS = 78;
	/** An icon is the screen's 18 x 16 texels at twice size. */
	private static final int ICON_W = 36;
	private static final int ICON_H = 32;

	private static boolean wasDown;
	private static int heldTicks;
	private static boolean selecting;
	private static double cursorX;
	private static double cursorY;

	private TvEmoteWheel() {
	}

	/** Loads the class, registering the keys (they must be in before the options are read). */
	static void initialize() {
	}

	public static boolean isSelecting() {
		return selecting;
	}

	/** Mouse movement while the wheel is open steers its cursor instead of the camera. */
	public static void moveCursor(double dx, double dy) {
		cursorX += dx * CURSOR_SPEED;
		cursorY += dy * CURSOR_SPEED;
		double length = Math.sqrt(cursorX * cursorX + cursorY * cursorY);
		if (length > CURSOR_LIMIT) {
			cursorX *= CURSOR_LIMIT / length;
			cursorY *= CURSOR_LIMIT / length;
		}
	}

	/** The emote under the cursor, or null in the middle. */
	private static TvEmote hovered() {
		if (cursorX * cursorX + cursorY * cursorY < DEADZONE * DEADZONE) {
			return null;
		}
		float turn = (float) (Math.atan2(cursorX, -cursorY) / Mth.TWO_PI);
		return TvEmote.byId(Math.floorMod(Math.round(turn * TvEmote.count()), TvEmote.count()));
	}

	static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		boolean wearing = player != null && TvHelmets.isWearing(player);
		while (NIGHT_VISION_KEY.consumeClick()) {
			if (wearing && minecraft.gui.screen() == null) {
				ClientPlayNetworking.send(TvHelmets.ToggleNightVision.INSTANCE);
			}
		}
		while (EMOTE_KEY.consumeClick()) {
			// Only whether it's held matters.
		}
		boolean down = wearing && EMOTE_KEY.isDown() && minecraft.gui.screen() == null;
		if (down) {
			heldTicks++;
			if (!selecting && heldTicks >= HOLD_TICKS) {
				selecting = true;
				cursorX = 0.0;
				cursorY = 0.0;
				player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.25f, 1.6f);
			}
		} else if (wasDown) {
			if (selecting) {
				TvEmote picked = hovered();
				if (picked != null) {
					send(picked);
				}
			} else if (player != null && wearing) {
				send(TvHelmets.screen(player).emoteType().next());
			}
			selecting = false;
			heldTicks = 0;
		} else {
			selecting = false;
			heldTicks = 0;
		}
		wasDown = down;
	}

	private static void send(TvEmote emote) {
		ClientPlayNetworking.send(new TvHelmets.SelectEmote(emote.ordinal()));
	}

	private static Identifier icon(TvEmote emote) {
		return MyMod.id("textures/gui/tv_emote/" + emote.id() + ".png");
	}

	/** The wheel: a little TV screen per emote round the crosshair, the hovered one lit up and named in the middle. */
	static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!selecting || minecraft.player == null) {
			return;
		}
		Font font = minecraft.font;
		int cx = graphics.guiWidth() / 2, cy = graphics.guiHeight() / 2;
		TvEmote hovered = hovered();
		TvEmote current = TvHelmets.screen(minecraft.player).emoteType();
		graphics.fill(cx - 30, cy - 12, cx + 30, cy + 12, 0x90000000);
		for (TvEmote emote : TvEmote.values()) {
			float angle = Mth.TWO_PI * emote.ordinal() / TvEmote.count();
			int x = Math.round(cx + Mth.sin(angle) * RADIUS) - ICON_W / 2;
			int y = Math.round(cy - Mth.cos(angle) * RADIUS) - ICON_H / 2;
			boolean hover = emote == hovered;
			int grow = hover ? 2 : 0;
			// A wooden cabinet round a grey bezel round the screen.
			graphics.fill(x - 5 - grow, y - 4 - grow, x + ICON_W + 5 + grow, y + ICON_H + 4 + grow, hover ? 0xFFC0844A : 0xE0805230);
			graphics.fill(x - 2 - grow, y - 2 - grow, x + ICON_W + 2 + grow, y + ICON_H + 2 + grow, 0xFF3A3D42);
			graphics.blit(RenderPipelines.GUI_TEXTURED, icon(emote), x - grow, y - grow, 0.0f, 0.0f, ICON_W + grow * 2, ICON_H + grow * 2,
					18, 16, 18, 16, hover ? 0xFFFFFFFF : 0xFFB0B0B0);
			if (emote == current) {
				graphics.outline(x - 6 - grow, y - 5 - grow, ICON_W + 12 + grow * 2, ICON_H + 10 + grow * 2, 0xFFFFE070);
			}
		}
		Component name = hovered != null ? hovered.displayName() : Component.translatable("tv_helmet.tomyen.wheel.hint");
		graphics.centeredText(font, name, cx, cy - 4, hovered != null ? 0xFFDEF4FF : 0xFF9AA6B0);
		int px = cx + (int) Math.round(cursorX), py = cy + (int) Math.round(cursorY);
		graphics.fill(px - 1, py - 1, px + 2, py + 2, 0xFFDEF4FF);
		graphics.outline(px - 3, py - 3, 7, 7, 0x80DEF4FF);
	}
}
