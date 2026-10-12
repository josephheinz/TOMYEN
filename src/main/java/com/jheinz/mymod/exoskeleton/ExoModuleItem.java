package com.jheinz.mymod.exoskeleton;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A module that goes in one of the exoskeleton's sockets. */
public class ExoModuleItem extends Item {
	private final ExoModule module;

	public ExoModuleItem(Properties properties, ExoModule module) {
		super(properties);
		this.module = module;
	}

	public ExoModule module() {
		return module;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		tooltip.accept(module.description().copy().withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("item.mymod.exo_module.install").withStyle(ChatFormatting.GOLD));
	}
}
