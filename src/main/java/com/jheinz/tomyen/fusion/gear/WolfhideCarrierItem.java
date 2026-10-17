package com.jheinz.tomyen.fusion.gear;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import com.jheinz.tomyen.armor.PlateCarrierItem;

/**
 * A plate carrier that's also fusion gear (the Wolfhide Carrier, and the Revenant Mail after it): it takes an armor
 * plate exactly as the Plate Carrier does (right-click a plate onto it; PlateCarrierItem.wornPlate counts it for the
 * bullet armor), and its tooltip adds the "Fused" lines.
 */
public class WolfhideCarrierItem extends PlateCarrierItem {
	private final String[] lines;

	public WolfhideCarrierItem(Properties properties, String... lines) {
		super(properties);
		this.lines = lines;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		fusedLines(tooltip, lines);
		super.appendHoverText(stack, context, display, tooltip, flag);
	}

	static void fusedLines(Consumer<Component> tooltip, String... lines) {
		tooltip.accept(Component.translatable("item.tomyen.fused").withStyle(ChatFormatting.LIGHT_PURPLE));
		for (String line : lines) {
			tooltip.accept(Component.translatable(line).withStyle(ChatFormatting.GRAY));
		}
	}
}
