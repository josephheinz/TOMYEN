package com.jheinz.tomyen.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import com.jheinz.tomyen.Ammo;
import com.jheinz.tomyen.GunItem;
import com.jheinz.tomyen.MagazineContents;
import com.jheinz.tomyen.MagazineItem;
import com.jheinz.tomyen.ModComponents;
import com.jheinz.tomyen.client.energy.EnergyClient;
import com.jheinz.tomyen.client.fishgun.FishGunClient;
import com.jheinz.tomyen.client.leafblower.LeafBlowerClient;
import com.jheinz.tomyen.fishgun.FishGunItem;
import com.jheinz.tomyen.leafblower.LeafBlowerItem;
import com.jheinz.tomyen.client.launcher.LauncherClient;
import com.jheinz.tomyen.client.minigun.MinigunHud;
import com.jheinz.tomyen.energy.EnergyWeaponItem;
import com.jheinz.tomyen.launcher.RocketLauncherItem;
import com.jheinz.tomyen.minigun.MinigunItem;
import com.jheinz.tomyen.napalm.FlamethrowerItem;
import com.jheinz.tomyen.shoddy.ShoddyGunItem;
import com.jheinz.tomyen.shotgun.ShotgunItem;

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
		} else if (held.getItem() instanceof FishGunItem) {
			FishGunClient.extractHud(graphics, minecraft, held, x, y);
		} else if (held.getItem() instanceof LeafBlowerItem blower) {
			LeafBlowerClient.extractHud(graphics, minecraft, held, blower, x, y);
		} else if (held.getItem() instanceof FlamethrowerItem) {
			extractFuel(graphics, minecraft, held, x, y);
		} else if (held.getItem() instanceof MinigunItem) {
			MinigunHud.extractAmmo(graphics, minecraft, held, x, y);
		} else if (held.getItem() instanceof EnergyWeaponItem energy) {
			EnergyClient.extractAmmo(graphics, minecraft, held, energy, x, y);
		} else if (held.getItem() instanceof GunItem gun) {
			MagazineContents loaded = held.get(ModComponents.LOADED_MAGAZINE);
			if (loaded == null && !gun.stats().singleRounds()) {
				graphics.text(minecraft.font, Component.translatable("hud.tomyen.no_magazine"), x, y, RED, true);
				graphics.text(minecraft.font, Component.translatable("hud.tomyen.punch_to_reload"), x, y + 10, GRAY, true);
				return;
			}
			int rounds = loaded == null ? 0 : loaded.rounds();
			int color = rounds == 0 ? RED : WHITE;
			String count = rounds + " / " + gun.capacity(held);
			graphics.text(minecraft.font, count, x, y, color, true);
			drawFireMode(graphics, minecraft, gun, held, x + minecraft.font.width(count) + 6, y);
			if (held.getItem() instanceof ShoddyGunItem && ShoddyGunItem.jammed(held)) {
				graphics.text(minecraft.font, Component.translatable("hud.tomyen.shoddy.jammed"), x, y + 10, RED, true);
			} else if (ShotgunItem.loading(held)) {
				graphics.text(minecraft.font, Component.translatable("hud.tomyen.shotgun.loading"), x, y + 10, GRAY, true);
			} else if (held.has(ModComponents.BOLT_OPEN) && rounds > 0) {
				graphics.text(minecraft.font, Component.translatable("hud.tomyen.work_bolt"), x, y + 10, GRAY, true);
			} else if (rounds == 0) {
				graphics.text(minecraft.font, Component.translatable("hud.tomyen.punch_to_reload"), x, y + 10, GRAY, true);
			} else {
				drawAmmoName(graphics, minecraft, gun, loaded, x, y + 10);
			}
		} else if (held.getItem() instanceof MagazineItem magazine) {
			MagazineContents contents = held.get(ModComponents.MAGAZINE_CONTENTS);
			int rounds = contents == null ? 0 : contents.rounds();
			graphics.text(minecraft.font, rounds + " / " + magazine.capacity(), x, y, rounds == 0 ? GRAY : WHITE, true);
			graphics.text(minecraft.font, Component.translatable("hud.tomyen.pack_hint"), x, y + 10, GRAY, true);
		}
	}

	/** A flamethrower's tank: "Fuel 64%" over a bar that runs from napalm orange down to red as it empties. */
	private static void extractFuel(GuiGraphicsExtractor graphics, Minecraft minecraft, ItemStack held, int x, int y) {
		if (!held.has(ModComponents.LOADED_MAGAZINE)) {
			graphics.text(minecraft.font, Component.translatable("hud.tomyen.flamethrower.no_tank"), x, y, RED, true);
			graphics.text(minecraft.font, Component.translatable("hud.tomyen.punch_to_reload"), x, y + 10, GRAY, true);
			return;
		}
		float fraction = FlamethrowerItem.fuel(held) / (float) FlamethrowerItem.TANK;
		int percent = Math.round(fraction * 100.0f);
		graphics.text(minecraft.font, Component.translatable("hud.tomyen.flamethrower.fuel", percent), x, y, percent == 0 ? RED : WHITE, true);
		int width = 40;
		graphics.fill(x, y + 11, x + width, y + 14, 0xFF202020);
		graphics.fill(x, y + 11, x + Math.round(width * fraction), y + 14, fraction > 0.25f ? 0xFFFF7A1A : RED);
		if (percent == 0) {
			graphics.text(minecraft.font, Component.translatable("hud.tomyen.punch_to_reload"), x, y + 16, GRAY, true);
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
