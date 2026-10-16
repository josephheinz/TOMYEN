package com.jheinz.mymod.client.fishgun;

import org.joml.Vector3f;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import com.jheinz.mymod.client.GunDisplay;
import com.jheinz.mymod.client.GunHands;
import com.jheinz.mymod.fishgun.FishAmmo;
import com.jheinz.mymod.fishgun.FishGunItem;
import com.jheinz.mymod.fishgun.FishGuns;
import com.jheinz.mymod.fishgun.LoadedFish;

/**
 * The Fish Gun on the client: the flying fish's renderer, the ammo HUD (the loaded fish, its weight and how many are
 * left, over its weight class: "Pea-shooter" to "CANNONBALL"), and its held pose (FishGunRig, from the model generator).
 */
public final class FishGunClient {
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GRAY = 0xFFAAAAAA;
	private static final int RED = 0xFFFF5555;
	private static final int GOLD = 0xFFFFAA00;

	private FishGunClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(FishGuns.FISH_ROUND, FishProjectileRenderer::new);
	}

	public static GunDisplay display() {
		float[] d = FishGunRig.FISH_GUN_DISPLAY;
		return new GunDisplay(new Vector3f(d[0], d[1], d[2]), d[3], d[4]);
	}

	public static GunHands.Anchors anchors() {
		return new GunHands.Anchors(FishGunRig.FISH_GUN_GRIP, FishGunRig.FISH_GUN_SUPPORT);
	}

	/** "Trophy Tuna, 412 lb  x1" over "CANNONBALL" (or "Loading..." while the next fish goes in). */
	public static void extractHud(GuiGraphicsExtractor graphics, Minecraft minecraft, ItemStack held, int x, int y) {
		LoadedFish loaded = held.get(FishGuns.LOADED_FISH);
		if (loaded == null) {
			graphics.text(minecraft.font, Component.translatable("hud.mymod.fish_gun.no_fish"), x, y, RED, true);
			return;
		}
		Component name = FishGunItem.describe(loaded.fish().value().getDefaultInstance(), loaded.trophy(), loaded.weight());
		graphics.text(minecraft.font, name, x, y, loaded.trophy() ? GOLD : WHITE, true);
		if (loaded.count() > 0) {
			graphics.text(minecraft.font, "x" + loaded.count(), x + minecraft.font.width(name) + 6, y, GRAY, true);
		}
		if (minecraft.player != null && minecraft.player.getCooldowns().isOnCooldown(held)) {
			graphics.text(minecraft.font, Component.translatable("hud.mymod.fish_gun.loading"), x, y + 10, GRAY, true);
			return;
		}
		String weightClass = FishAmmo.weightClass(loaded.weight());
		graphics.text(minecraft.font, Component.translatable("hud.mymod.fish_gun.class." + weightClass), x, y + 10, classColor(weightClass), true);
	}

	private static int classColor(String weightClass) {
		return switch (weightClass) {
			case "pea" -> 0xFFAAAAAA;
			case "slap" -> 0xFFFFFFFF;
			case "thump" -> 0xFFFFFF55;
			case "haymaker" -> 0xFFFFAA00;
			default -> RED;
		};
	}
}
