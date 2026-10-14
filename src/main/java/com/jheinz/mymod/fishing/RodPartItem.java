package com.jheinz.mymod.fishing;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A line, hook, reel or lure for a mod fishing rod, fitted at the Tackle Bench. */
public class RodPartItem extends Item {
	private final PartSlot slot;
	private final RodModifiers modifiers;

	public RodPartItem(Properties properties, PartSlot slot, RodModifiers modifiers) {
		super(properties.stacksTo(16));
		this.slot = slot;
		this.modifiers = modifiers;
	}

	public PartSlot slot() {
		return slot;
	}

	public RodModifiers modifiers() {
		return modifiers;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		tooltip.accept(Component.translatable("tooltip.mymod.rod_part", slot.label()).withStyle(ChatFormatting.GRAY));
		modifiers.describe().forEach(tooltip);
		tooltip.accept(Component.translatable("tooltip.mymod.rod_part.fit").withStyle(ChatFormatting.DARK_GRAY));
	}
}
