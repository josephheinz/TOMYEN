package com.jheinz.mymod.mech;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A part of a Bulwark Walker, fitted in a Mech Gantry. The torso, legs and arms wear (their durability is the
 * part's condition, and the torso also keeps the hull's integrity once it's been docked damaged); a Cargo Pod
 * keeps its hold's contents. Each says where it goes and what it does.
 */
public class MechPartItem extends Item {
	public MechPartItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		if (stack.isDamageableItem()) {
			float condition = 1.0f - stack.getDamageValue() / (float) stack.getMaxDamage();
			tooltip.accept(Component.translatable("item.mymod.mech_part.condition", Math.round(condition * 100.0f))
					.withStyle(condition < 0.35f ? ChatFormatting.RED : condition < 0.7f ? ChatFormatting.GOLD : ChatFormatting.GREEN));
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data != null && data.copyTag().contains("Hull")) {
			tooltip.accept(Component.translatable("item.mymod.mech_part.hull", Math.round(data.copyTag().getFloatOr("Hull", 1.0f) * 100.0f))
					.withStyle(ChatFormatting.GOLD));
		}
		ItemContainerContents hold = stack.get(DataComponents.CONTAINER);
		if (hold != null) {
			int items = hold.nonEmptyItemCopyStream().mapToInt(ItemStack::getCount).sum();
			if (items > 0) {
				tooltip.accept(Component.translatable("item.mymod.mech_part.hold", items).withStyle(ChatFormatting.GOLD));
			}
		}
		String key = getDescriptionId();
		tooltip.accept(Component.translatable(key + ".slot").withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable(key + ".hint").withStyle(ChatFormatting.DARK_GRAY));
	}
}
