package com.jheinz.tomyen.fusion.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.consume_effects.ConsumeEffect;

import com.jheinz.tomyen.fishing.RodCondition;
import com.jheinz.tomyen.fishing.RodItem;
import com.jheinz.tomyen.fishing.RodModifiers;
import com.jheinz.tomyen.fusion.FusionTooltipItem;

/**
 * The Hexed Rod (Hag Charm + Fae Rod, at a pond): a tier 3 fae rod with the hag's charm knotted into it for good. It
 * keeps the charm's luck (rarer fish, more so by night, cursed catches easier) and the Fae Rod's Feywild bonus, but the
 * hag's bargain has a price: every fight on it is a step harder ({@link #fightPenalty}), and every fish it lands that
 * can be eaten comes up hexed ({@link HexedCatch}): a gamble, eaten, between a powerful blessing and a nasty hex.
 */
public class HexedRodItem extends RodItem {
	public HexedRodItem(Properties properties, RodModifiers modifiers, RodCondition condition, RodModifiers bonus) {
		super(properties, 3, false, modifiers, true, condition, bonus);
	}

	@Override
	public int fightPenalty() {
		return 1;
	}

	@Override
	public ItemStack landed(ServerLevel level, ServerPlayer angler, ItemStack caught) {
		ItemStack hexed = hex(caught);
		if (hexed != caught) {
			level.sendParticles(ParticleTypes.WITCH, angler.getX(), angler.getY(1.0), angler.getZ(), 10, 0.4, 0.3, 0.4, 0.02);
		}
		return hexed;
	}

	/** The catch, hexed: a hex rolled when it's eaten, a sickly glint and a name to warn whoever finds it. Uneatable catches are left be. */
	public static ItemStack hex(ItemStack caught) {
		Consumable food = caught.get(DataComponents.CONSUMABLE);
		if (food == null || food.onConsumeEffects().stream().anyMatch(effect -> effect instanceof HexedCatch)) {
			return caught;
		}
		ItemStack hexed = caught.copy();
		List<ConsumeEffect> effects = new ArrayList<>(food.onConsumeEffects());
		effects.add(HexedCatch.INSTANCE);
		hexed.set(DataComponents.CONSUMABLE, new Consumable(food.consumeSeconds(), food.animation(), food.sound(), food.hasConsumeParticles(),
				List.copyOf(effects)));
		hexed.set(DataComponents.ITEM_NAME, Component.translatable("item.tomyen.hexed_catch", caught.getItemName())
				.withStyle(ChatFormatting.DARK_GREEN));
		hexed.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		hexed.set(DataComponents.LORE, new ItemLore(List.of(
				Component.translatable("item.tomyen.hexed_catch.lore").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC))));
		return hexed;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		FusionTooltipItem.appendFusionLines(tooltip, "item.tomyen.hexed_rod.hexed", "item.tomyen.hexed_rod.harder");
		super.appendHoverText(stack, context, display, tooltip, flag);
	}
}
