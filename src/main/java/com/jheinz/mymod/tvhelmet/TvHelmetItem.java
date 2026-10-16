package com.jheinz.mymod.tvhelmet;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** The Cathode TV Helmet item: a plain helmet (see {@link TvHelmets}), with a tooltip saying what it does. */
public class TvHelmetItem extends Item {
	public TvHelmetItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.mymod.tv_helmet.screen").withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("item.mymod.tv_helmet.vision").withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("item.mymod.tv_helmet.water").withStyle(ChatFormatting.DARK_GRAY));
	}
}
