package com.jheinz.mymod.longsword;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A long sword (see LongSwords for its stats and the two-handed rule). Only adds the two-handed hint to the tooltip. */
public class LongSwordItem extends Item {
	public LongSwordItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.mymod.long_sword.two_handed").withStyle(ChatFormatting.GRAY));
	}
}
