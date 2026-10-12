package com.jheinz.mymod.tank;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A 120mm round for a tank's gun. It does nothing in hand: stow it in a tank's ammo rack for the loader. */
public class ShellItem extends Item {
	private final ShellType type;

	public ShellItem(Properties properties, ShellType type) {
		super(properties);
		this.type = type;
	}

	public ShellType type() {
		return type;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.mymod." + type.id() + "_shell.hint").withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("item.mymod.tank_round.hint_stow").withStyle(ChatFormatting.DARK_GRAY));
	}
}
