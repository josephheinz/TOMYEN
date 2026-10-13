package com.jheinz.mymod.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import com.jheinz.mymod.Ammo;
import com.jheinz.mymod.GunItem;
import com.jheinz.mymod.MagazineContents;
import com.jheinz.mymod.MagazineItem;
import com.jheinz.mymod.ModComponents;
import com.jheinz.mymod.client.launcher.LauncherClient;
import com.jheinz.mymod.launcher.RocketLauncherItem;
import com.jheinz.mymod.napalm.FlamethrowerItem;
import com.jheinz.mymod.shotgun.ShotgunItem;

/** Rounds left in the held gun or magazine, drawn to the right of the hotbar. */
public final class AmmoHud {
	private static final int WHITE = 0xFFFFFFFF;
	private static final int RED = 0xFFFF5555;
	private static final int GRAY = 0xFFAAAAAA;

	private AmmoHud() {
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		ItemStack held = minecraft.player.getMainHandItem();
		int x = graphics.guiWidth() / 2 + 91 + 8;
		int y = graphics.guiHeight() - 20;

		if (held.getItem() instanceof RocketLauncherItem) {
			LauncherClient.extractAmmo(graphics, minecraft, held, x, y);
		} else if (held.getItem() instanceof FlamethrowerItem) {
			extractFuel(graphics, minecraft, held, x, y);
		} else if (held.getItem() instanceof GunItem gun) {
			MagazineContents loaded = held.get(ModComponents.LOADED_MAGAZINE);
			if (loaded == null && !gun.stats().singleRounds()) {
				graphics.text(minecraft.font, Component.translatable("hud.mymod.no_magazine"), x, y, RED, true);
				graphics.text(minecraft.font, Component.translatable("hud.mymod.punch_to_reload"), x, y + 10, GRAY, true);
				return;
			}
			int rounds = loaded == null ? 0 : loaded.rounds();
			int color = rounds == 0 ? RED : WHITE;
			String count = rounds + " / " + gun.capacity(held);
			graphics.text(minecraft.font, count, x, y, color, true);
			drawFireMode(graphics, minecraft, gun, held, x + minecraft.font.width(count) + 6, y);
			if (ShotgunItem.loading(held)) {
				graphics.text(minecraft.font, Component.translatable("hud.mymod.shotgun.loading"), x, y + 10, GRAY, true);
			} else if (held.has(ModComponents.BOLT_OPEN) && rounds > 0) {
				graphics.text(minecraft.font, Component.translatable("hud.mymod.work_bolt"), x, y + 10, GRAY, true);
			} else if (rounds == 0) {
				graphics.text(minecraft.font, Component.translatable("hud.mymod.punch_to_reload"), x, y + 10, GRAY, true);
			} else {
				drawAmmoName(graphics, minecraft, gun, loaded, x, y + 10);
			}
		} else if (held.getItem() instanceof MagazineItem magazine) {
			MagazineContents contents = held.get(ModComponents.MAGAZINE_CONTENTS);
			int rounds = contents == null ? 0 : contents.rounds();
			graphics.text(minecraft.font, rounds + " / " + magazine.capacity(), x, y, rounds == 0 ? GRAY : WHITE, true);
			graphics.text(minecraft.font, Component.translatable("hud.mymod.pack_hint"), x, y + 10, GRAY, true);
		}
	}

	/** A flamethrower's tank: "Fuel 64%" over a bar that runs from napalm orange down to red as it empties. */
	private static void extractFuel(GuiGraphicsExtractor graphics, Minecraft minecraft, ItemStack held, int x, int y) {
		if (!held.has(ModComponents.LOADED_MAGAZINE)) {
			graphics.text(minecraft.font, Component.translatable("hud.mymod.flamethrower.no_tank"), x, y, RED, true);
			graphics.text(minecraft.font, Component.translatable("hud.mymod.punch_to_reload"), x, y + 10, GRAY, true);
			return;
		}
		float fraction = FlamethrowerItem.fuel(held) / (float) FlamethrowerItem.TANK;
		int percent = Math.round(fraction * 100.0f);
		graphics.text(minecraft.font, Component.translatable("hud.mymod.flamethrower.fuel", percent), x, y, percent == 0 ? RED : WHITE, true);
		int width = 40;
		graphics.fill(x, y + 11, x + width, y + 14, 0xFF202020);
		graphics.fill(x, y + 11, x + Math.round(width * fraction), y + 14, fraction > 0.25f ? 0xFFFF7A1A : RED);
		if (percent == 0) {
			graphics.text(minecraft.font, Component.translatable("hud.mymod.punch_to_reload"), x, y + 16, GRAY, true);
		}
	}

	private static void drawAmmoName(GuiGraphicsExtractor graphics, Minecraft minecraft, GunItem gun,
			MagazineContents loaded, int x, int y) {
		Ammo round = Ammo.find(gun.ammo(), loaded.ammo());
		int color = round == null ? GRAY : 0xFF000000 | round.color();
		graphics.text(minecraft.font, new ItemStack(loaded.ammo()).getHoverName(), x, y, color, true);
	}

	/** Fire mode tag after the round count, e.g. "17 / 17  BURST". */
	private static void drawFireMode(GuiGraphicsExtractor graphics, Minecraft minecraft, GunItem gun, ItemStack held,
			int x, int y) {
		graphics.text(minecraft.font, Component.translatable(gun.fireMode(held).translationKey() + ".short"), x, y, GRAY, true);
	}
}
