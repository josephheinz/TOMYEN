package com.jheinz.tomyen.herbalism;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

/** An herbalism item with a line of grey help text under its name ({@code <description id>.desc} in the lang file). */
public class HerbalItem extends Item {
	public HerbalItem(Properties properties) {
		super(properties);
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		describe(this, tooltip);
	}

	static void describe(Item item, Consumer<Component> tooltip) {
		tooltip.accept(Component.translatable(item.getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
	}

	/** The same, for herbs and herbalism blocks. */
	public static class Placeable extends BlockItem {
		public Placeable(Block block, Properties properties) {
			super(block, properties);
		}

		@Override
		@SuppressWarnings("deprecation")
		public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
				TooltipFlag flag) {
			describe(this, tooltip);
		}
	}
}
