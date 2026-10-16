package com.jheinz.tomyen.dragonscale;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import com.jheinz.tomyen.longsword.LongSwordItem;

/** A long sword of fossilized dragon bone (see LongSwords): every hit sets the target alight for {@link #BURN_SECONDS} s. */
public class DragonboneLongSwordItem extends LongSwordItem {
	public static final float BURN_SECONDS = 4.0f;

	public DragonboneLongSwordItem(Properties properties) {
		super(properties);
	}

	@Override
	public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		super.hurtEnemy(stack, target, attacker);
		target.igniteForSeconds(BURN_SECONDS);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.tomyen.dragonbone_long_sword.burns").withStyle(ChatFormatting.GOLD));
		super.appendHoverText(stack, context, display, tooltip, flag);
	}
}
