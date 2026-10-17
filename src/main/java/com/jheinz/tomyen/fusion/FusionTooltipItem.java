package com.jheinz.tomyen.fusion;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A fusion item with a few grey tooltip lines saying what it does, under a "Fused" tag. */
public class FusionTooltipItem extends Item {
	private final String[] lines;

	public FusionTooltipItem(Properties properties, String... lines) {
		super(properties);
		this.lines = lines;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		appendFusionLines(tooltip, lines);
	}

	public static void appendFusionLines(Consumer<Component> tooltip, String... lines) {
		tooltip.accept(Component.translatable("item.tomyen.fused").withStyle(ChatFormatting.LIGHT_PURPLE));
		for (String line : lines) {
			tooltip.accept(Component.translatable(line).withStyle(ChatFormatting.GRAY));
		}
	}
}
