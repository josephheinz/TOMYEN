package com.jheinz.mymod.client.scrapbench;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;

import com.jheinz.mymod.blueprint.Blueprint;
import com.jheinz.mymod.blueprint.Blueprints;
import com.jheinz.mymod.scrapbench.ScrapBench;

/** The Scrap Bench screen, and a "learned" note on blueprints the player has already read. */
public final class ScrapBenchClient {
	private ScrapBenchClient() {
	}

	public static void initialize() {
		MenuScreens.register(ScrapBench.MENU, ScrapBenchScreen::new);
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			Blueprint blueprint = Blueprints.of(stack);
			Minecraft minecraft = Minecraft.getInstance();
			if (blueprint != null && minecraft.player != null && Blueprints.knows(minecraft.player, blueprint.id())) {
				lines.add(1, Component.translatable("blueprint.mymod.known").withStyle(ChatFormatting.GREEN));
			}
		});
	}
}
