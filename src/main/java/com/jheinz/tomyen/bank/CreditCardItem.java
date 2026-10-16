package com.jheinz.tomyen.bank;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import org.jspecify.annotations.Nullable;

/**
 * A credit card: pays a trade's emeralds for you. Put it in a villager's payment slot in place of the
 * emeralds (picking a trade you can't afford in cash does that for you) and each trade charges the
 * emeralds to the card instead (see {@link PaymentCards}), running up debt with the bank (see
 * {@link DebtCollection}). What's been charged is its balance, shown as a durability bar draining from its
 * credit limit. A maxed-out card doesn't break, it just declines, and every credit card is frozen while
 * its holder is in collections.
 * <p>
 * The balance is tracked on the card rather than as vanilla durability so it can't be "repaired" by
 * combining two cards in a crafting grid or grindstone. Only paying a banker brings it back down.
 */
public class CreditCardItem extends PaymentCardItem {
	private final int limit;

	public CreditCardItem(Properties properties, int limit) {
		super(properties);
		this.limit = limit;
	}

	/** The credit limit, in emeralds. */
	public int limit() {
		return limit;
	}

	public static int balance(ItemStack card) {
		return card.getOrDefault(Banking.CARD_BALANCE, 0);
	}

	/** Emeralds that can still be charged to it. */
	public int available(ItemStack card) {
		return Math.max(0, limit - balance(card));
	}

	/** Sets the balance, clamped to 0..limit; a clear card drops the component altogether. */
	public void setBalance(ItemStack card, int balance) {
		int clamped = Mth.clamp(balance, 0, limit);
		if (clamped == 0) {
			card.remove(Banking.CARD_BALANCE);
		} else {
			card.set(Banking.CARD_BALANCE, clamped);
		}
	}

	@Override
	public boolean canPay(ItemStack card, int emeralds) {
		return available(card) >= emeralds;
	}

	@Override
	public void pay(ItemStack card, int emeralds, @Nullable ServerPlayer payer) {
		setBalance(card, balance(card) + emeralds);
		if (payer != null) {
			DebtCollection.charge(payer, emeralds, card);
		}
	}

	@Override
	public boolean usableBy(Player player) {
		return !Debt.inCollections(player);
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return balance(stack) > 0;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.round(13.0f * available(stack) / limit);
	}

	/** Green with plenty of credit left, through yellow to red when maxed out, like a tool wearing down. */
	@Override
	public int getBarColor(ItemStack stack) {
		return Mth.hsvToRgb(available(stack) / (float) limit / 3.0f, 1.0f, 1.0f);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.tomyen.credit_card.balance", balance(stack), limit).withStyle(ChatFormatting.GRAY));
		if (available(stack) == 0) {
			tooltip.accept(Component.translatable("item.tomyen.credit_card.maxed_out").withStyle(ChatFormatting.RED));
		} else {
			tooltip.accept(Component.translatable("item.tomyen.credit_card.available", available(stack)).withStyle(ChatFormatting.GREEN));
		}
		tooltip.accept(Component.translatable("item.tomyen.credit_card.hint").withStyle(ChatFormatting.DARK_GRAY));
	}
}
