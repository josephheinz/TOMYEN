package com.jheinz.mymod.sodajetpack;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** The Soda-Bottle Jetpack and the Fizz Charge: plain items (see {@link SodaJetpacks}) with tooltips. */
public class SodaJetpackItem extends Item {
	private final String[] lines;

	public SodaJetpackItem(Properties properties, String... lines) {
		super(properties);
		this.lines = lines;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		for (int i = 0; i < lines.length; i++) {
			tooltip.accept(Component.translatable(lines[i]).withStyle(i == lines.length - 1 && lines.length > 1 ? ChatFormatting.DARK_GRAY
					: ChatFormatting.GRAY));
		}
	}
}
