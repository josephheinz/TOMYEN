package com.jheinz.tomyen.neuralink;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A chip that goes in one of the Neuralink's module sockets. */
public class NeuralinkModuleItem extends Item {
	private final NeuralinkModule module;

	public NeuralinkModuleItem(Properties properties, NeuralinkModule module) {
		super(properties);
		this.module = module;
	}

	public NeuralinkModule module() {
		return module;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		tooltip.accept(module.description().copy().withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("item.tomyen.neuralink_module.install").withStyle(ChatFormatting.DARK_AQUA));
	}
}
