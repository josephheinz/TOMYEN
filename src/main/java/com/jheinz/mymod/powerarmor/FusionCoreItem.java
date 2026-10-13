package com.jheinz.mymod.powerarmor;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * What powers a suit of power armor. Its charge ({@link PowerArmors#FUSION_CHARGE}, absent means full) is
 * {@link #MAX_CHARGE} units: an hour of walking for a Mk I torso (see {@link PowerArmor} for what drains it).
 * It shows as a bar like durability. A spent core is recharged in the Foundry: a third of the way with redstone
 * blocks, or all the way with enriched uranium (see {@link PowerArmorRecipes}).
 */
public class FusionCoreItem extends Item {
	public static final int MAX_CHARGE = 72000;

	public FusionCoreItem(Properties properties) {
		super(properties);
	}

	public static int charge(ItemStack stack) {
		return Mth.clamp(stack.getOrDefault(PowerArmors.FUSION_CHARGE, MAX_CHARGE), 0, MAX_CHARGE);
	}

	public static void setCharge(ItemStack stack, int charge) {
		int clamped = Mth.clamp(charge, 0, MAX_CHARGE);
		if (clamped == MAX_CHARGE) {
			stack.remove(PowerArmors.FUSION_CHARGE);
		} else {
			stack.set(PowerArmors.FUSION_CHARGE, clamped);
		}
	}

	public static float fraction(ItemStack stack) {
		return charge(stack) / (float) MAX_CHARGE;
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return charge(stack) < MAX_CHARGE;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.round(13.0f * fraction(stack));
	}

	/** Green when full, through amber to red as it runs down. */
	@Override
	public int getBarColor(ItemStack stack) {
		float charge = fraction(stack);
		return charge > 0.5f ? ARGB.srgbLerp((charge - 0.5f) * 2.0f, 0xFFFFB000, 0xFF6CFF4A) & 0xFFFFFF
				: ARGB.srgbLerp(charge * 2.0f, 0xFFFF3020, 0xFFFFB000) & 0xFFFFFF;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		int percent = Mth.ceil(fraction(stack) * 100.0f);
		tooltip.accept(Component.translatable(percent <= 0 ? "item.mymod.fusion_core.depleted" : "item.mymod.fusion_core.charge", percent)
				.withStyle(percent <= 0 ? ChatFormatting.RED : percent < 15 ? ChatFormatting.GOLD : ChatFormatting.GREEN));
		tooltip.accept(Component.translatable("item.mymod.fusion_core.use").withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("item.mymod.fusion_core.recharge").withStyle(ChatFormatting.DARK_GRAY));
	}
}
