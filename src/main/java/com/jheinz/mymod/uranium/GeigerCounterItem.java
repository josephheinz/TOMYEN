package com.jheinz.mymod.uranium;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Held in either hand, it clicks: now and then from the background, and faster the closer and more uranium ore there
 * is within {@link #RANGE} blocks (or radioactive stuff in the holder's pockets). The clicking is all client side
 * (GeigerCounterClient).
 */
public class GeigerCounterItem extends Item {
	public static final int RANGE = 8;

	public GeigerCounterItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.mymod.geiger_counter.hint").withStyle(ChatFormatting.GRAY));
	}
}
