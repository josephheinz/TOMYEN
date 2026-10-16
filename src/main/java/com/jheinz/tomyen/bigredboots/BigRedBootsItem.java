package com.jheinz.tomyen.bigredboots;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** The Big Red Boots item: plain boots (see {@link BigRedBoots}), with a tooltip saying what they do. */
public class BigRedBootsItem extends Item {
	public BigRedBootsItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.tomyen.big_red_boots.falls").withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("item.tomyen.big_red_boots.stomp").withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("item.tomyen.big_red_boots.waddle").withStyle(ChatFormatting.DARK_GRAY));
	}
}
