package com.jheinz.tomyen.client.bloomery;

import java.util.List;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.bloomery.BloomHammering;
import com.jheinz.tomyen.bloomery.Bloomery;
import com.jheinz.tomyen.bloomery.BloomeryBlockEntity;
import com.jheinz.tomyen.bloomery.BloomeryBlockEntity.Stroke;

/**
 * The Bloomery's client side: its screen; a small HUD under the crosshair while you look at one (its heat, the bellows
 * gauge and the last stroke's verdict), so the bellows can be worked by hand in the world; and the tooltips that make
 * bloom steel the stepping stone it is.
 */
public final class BloomeryClient {
	private static final int HUD_WIDTH = 100;
	private static final int PLATE = 0xB0101014;

	private BloomeryClient() {
	}

	public static void initialize() {
		MenuScreens.register(Bloomery.BLOOMERY_MENU, BloomeryScreen::new);
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, MyMod.id("bloomery"), BloomeryClient::extractHud);
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> tooltip(stack, lines));
	}

	private static void tooltip(ItemStack stack, List<Component> lines) {
		int at = Math.min(1, lines.size());
		if (stack.is(Bloomery.BLOOMERY.asItem())) {
			lines.add(at++, Component.translatable("block.tomyen.bloomery.desc").withStyle(ChatFormatting.GRAY));
			lines.add(at, Component.translatable("block.tomyen.bloomery.bellows").withStyle(ChatFormatting.GOLD));
		} else if (stack.is(Bloomery.SET.ingot) || stack.is(Bloomery.SET.nugget) || stack.is(Bloomery.SET.block.asItem())) {
			lines.add(at++, Component.translatable(Bloomery.CRUDE_HINT).withStyle(ChatFormatting.GRAY));
			if (stack.is(Bloomery.SET.ingot)) {
				lines.add(at, Component.translatable("item.tomyen.bloom_steel_ingot.stands_in").withStyle(ChatFormatting.DARK_GRAY));
			}
		} else if (stack.is(Bloomery.BLOOM)) {
			int blows = stack.getOrDefault(Bloomery.BLOOM_HITS, 0);
			if (blows > 0) {
				lines.add(at, Component.translatable("item.tomyen.bloom.blows", blows, BloomHammering.BLOWS).withStyle(ChatFormatting.GOLD));
			}
		}
	}

	private static void extractHud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.gui.screen() != null
				|| !(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
				|| !(minecraft.level.getBlockEntity(hit.getBlockPos()) instanceof BloomeryBlockEntity bloomery)) {
			return;
		}
		Font font = minecraft.font;
		int x0 = (graphics.guiWidth() - HUD_WIDTH) / 2;
		int y0 = graphics.guiHeight() / 2 + 12;
		graphics.fill(x0 - 4, y0 - 4, x0 + HUD_WIDTH + 4, y0 + 22, PLATE);

		graphics.text(font, Component.translatable("hud.tomyen.bloomery.temperature", Math.round(bloomery.temperature())), x0, y0 - 2,
				0xFFE6E0D8, true);
		int since = bloomery.sinceStroke(minecraft.level.getGameTime());
		Component verdict = BloomeryGauges.strokeText(bloomery.stroke(), bloomery.combo(), since);
		int colour = hudColour(bloomery.stroke());
		if (verdict == null && bloomery.lit() && bloomery.progress() > 0.0f) {
			verdict = Component.translatable("hud.tomyen.bloomery.blooming", Math.round(bloomery.progress() * 100.0f));
			colour = 0xFFFFB43A;
		}
		if (verdict != null) {
			graphics.text(font, verdict, x0 + HUD_WIDTH - font.width(verdict), y0 - 2, colour, true);
		}
		BloomeryGauges.heatBar(graphics, x0, y0 + 9, x0 + HUD_WIDTH, y0 + 12, bloomery.temperature(), bloomery.fuel());
		float smooth = since >= BloomeryBlockEntity.SINCE_CAP ? since : since + delta.getGameTimeDeltaPartialTick(false);
		BloomeryGauges.breath(graphics, x0, y0 + 15, x0 + HUD_WIDTH, y0 + 18, smooth);
	}

	private static int hudColour(Stroke stroke) {
		return switch (stroke) {
			case EARLY -> 0xFFFF6A5E;
			case GOOD -> 0xFF7CF070;
			case LATE -> 0xFFFFC04D;
			default -> 0xFFE6E0D8;
		};
	}
}
