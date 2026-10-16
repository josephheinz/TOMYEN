package com.jheinz.tomyen.metal;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** An ordinary item with a few lines in its tooltip saying what's special about it (translation keys). */
public class HintItem extends Item {
	private final List<String> hints;

	public HintItem(Properties properties, List<String> hints) {
		super(properties);
		this.hints = List.copyOf(hints);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		for (String hint : hints) {
			tooltip.accept(Component.translatable(hint).withStyle(ChatFormatting.GRAY));
		}
	}
}
