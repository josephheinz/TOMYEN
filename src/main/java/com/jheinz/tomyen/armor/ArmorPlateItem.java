package com.jheinz.tomyen.armor;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A ballistic plate that slots into a {@link PlateCarrierItem}. */
public class ArmorPlateItem extends Item {
	private final int level;
	private final float bulletReduction;

	/** @param bulletReduction share of bullet damage the plate absorbs, after armor */
	public ArmorPlateItem(Properties properties, int level, float bulletReduction) {
		super(properties);
		this.level = level;
		this.bulletReduction = bulletReduction;
	}

	public int level() {
		return level;
	}

	public float bulletReduction() {
		return bulletReduction;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.tomyen.armor_plate.reduction", Math.round(bulletReduction * 100))
				.withStyle(ChatFormatting.BLUE));
		tooltip.accept(Component.translatable("item.tomyen.armor_plate.hint").withStyle(ChatFormatting.DARK_GRAY));
	}
}
