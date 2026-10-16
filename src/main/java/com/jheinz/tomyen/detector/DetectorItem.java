package com.jheinz.tomyen.detector;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A tracker that works just by being held: what it reads, and how, is all on the client (DetectorClient) or in
 * {@link DetectorFixes}. Its tooltip says what it finds ({@code item.tomyen.<name>.hint}).
 */
public class DetectorItem extends Item {
	public DetectorItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		tooltip.accept(Component.translatable(getDescriptionId() + ".hint").withStyle(ChatFormatting.GRAY));
	}
}
