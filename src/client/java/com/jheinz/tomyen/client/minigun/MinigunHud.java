package com.jheinz.tomyen.client.minigun;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.jheinz.tomyen.minigun.MinigunItem;
import com.jheinz.tomyen.minigun.MinigunState;
import com.jheinz.tomyen.minigun.RotaryGun;

/**
 * Readouts for a rotary gun: the Minigun's beside the hotbar (in place of a magazine count, every 5.56 round carried,
 * and a heat bar), and the heat bar the Hogfire's panel shows for its gun mount.
 */
public final class MinigunHud {
	private static final int WHITE = 0xFFFFFFFF;
	private static final int RED = 0xFFFF5555;
	private static final int GRAY = 0xFFAAAAAA;
	private static final int BAR_BACK = 0xFF202020;

	private MinigunHud() {
	}

	/** Beside the hotbar while a Minigun is held: "412 rds", the heat bar under it, and what's stopping it, if anything. */
	public static void extractAmmo(GuiGraphicsExtractor graphics, Minecraft minecraft, ItemStack held, int x, int y) {
		Player player = minecraft.player;
		if (!MinigunItem.canWield(player)) {
			graphics.text(minecraft.font, Component.translatable("hud.tomyen.minigun.needs_suit"), x, y, RED, true);
			return;
		}
		int rounds = RotaryGun.carried(player);
		String count = Component.translatable("hud.tomyen.minigun.rounds", rounds).getString();
		graphics.text(minecraft.font, count, x, y, rounds == 0 && !player.hasInfiniteMaterials() ? RED : WHITE, true);
		graphics.text(minecraft.font, "5.56", x + minecraft.font.width(count) + 6, y, GRAY, true);
		MinigunState state = MinigunItem.state(held);
		heatBar(graphics, x, y + 11, 40, state);
		if (state.overheated()) {
			graphics.text(minecraft.font, Component.translatable("hud.tomyen.minigun.overheated"), x + 44, y + 9,
					Util.getMillis() % 500 < 250 ? RED : GRAY, true);
		}
	}

	/** A bar from amber through to red as the barrels heat; blinking red once they've overheated. */
	public static void heatBar(GuiGraphicsExtractor graphics, int x, int y, int width, MinigunState state) {
		graphics.fill(x, y, x + width, y + 3, BAR_BACK);
		float heat = state.overheated() ? state.heat() : Mth.clamp(state.heat(), 0.0f, 1.0f);
		int filled = Mth.ceil(heat * width);
		if (filled <= 0) {
			return;
		}
		int colour = state.overheated() ? (Util.getMillis() % 500 < 250 ? 0xFFFF3B30 : 0xFF7A1A12)
				: heat > 0.75f ? 0xFFFF5A2A : heat > 0.45f ? 0xFFFF9A2A : 0xFFFFC94A;
		graphics.fill(x, y, x + filled, y + 3, colour);
	}
}
