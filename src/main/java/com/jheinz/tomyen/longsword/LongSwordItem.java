package com.jheinz.tomyen.longsword;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;

/**
 * A long sword (see LongSwords for its stats and the two-handed rule). Adds the two-handed hint to the tooltip. One
 * forged from a mod metal's sword strikes as that sword does: its hits, damage bonus, use and tooltip lines are the
 * sword's, so a magmasteel long sword sets things alight and a voidsteel one blinks.
 */
public class LongSwordItem extends Item {
	private final @Nullable Item sword;

	public LongSwordItem(Properties properties) {
		this(properties, null);
	}

	public LongSwordItem(Properties properties, @Nullable Item sword) {
		super(properties);
		this.sword = sword;
	}

	@Override
	public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		if (sword != null) {
			sword.hurtEnemy(stack, target, attacker);
		}
	}

	@Override
	public float getAttackDamageBonus(Entity victim, float damage, DamageSource damageSource) {
		return sword != null ? sword.getAttackDamageBonus(victim, damage, damageSource) : 0.0f;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		return sword != null ? sword.use(level, player, hand) : super.use(level, player, hand);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		if (sword != null) {
			sword.appendHoverText(stack, context, display, tooltip, flag);
		}
		tooltip.accept(Component.translatable("item.tomyen.long_sword.two_handed").withStyle(ChatFormatting.GRAY));
	}
}
