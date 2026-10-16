package com.jheinz.tomyen.bank;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

import org.jspecify.annotations.Nullable;

/**
 * Paying for trades by card, credit or debit alike.
 * <p>
 * A card sitting in a merchant payment slot counts as however many emeralds that slot's cost asks for, as
 * long as it can cover them. The mixins on {@code MerchantOffer} route both the "is this paid for" check and
 * the actual payment through here, so the result slot, single trades and shift-click bulk trading all work
 * the same way, on whichever side runs them; only the server's payment counts.
 */
public final class PaymentCards {
	/** The player taking a trade's result right now, set around MerchantResultSlot.onTake (see the mixin). */
	public static final ThreadLocal<@Nullable Player> PAYER = new ThreadLocal<>();

	private PaymentCards() {
	}

	public static boolean isCard(ItemStack stack) {
		return stack.getItem() instanceof PaymentCardItem;
	}

	/** A plain emerald cost, the only kind a card can pay. */
	public static boolean isEmeraldCost(ItemCost cost) {
		return cost.item().value() == Items.EMERALD && cost.components().isEmpty();
	}

	/** Whether this stack pays {@code count} of this cost: the items themselves, or a card that can cover it. */
	private static boolean pays(ItemCost cost, int count, ItemStack stack) {
		if (stack.getItem() instanceof PaymentCardItem card) {
			return isEmeraldCost(cost) && card.canPay(stack, count);
		}
		return cost.test(stack) && stack.getCount() >= count;
	}

	/** {@link MerchantOffer#satisfiedBy}, with cards accepted in place of emeralds. */
	public static boolean satisfies(MerchantOffer offer, ItemStack buyA, ItemStack buyB) {
		if (!pays(offer.getItemCostA(), offer.getCostA().getCount(), buyA)) {
			return false;
		}
		return offer.getItemCostB().map(costB -> pays(costB, costB.count(), buyB)).orElse(buyB.isEmpty());
	}

	/** {@link MerchantOffer#take}, paying by card where one stands in for the emeralds. */
	public static boolean take(MerchantOffer offer, ItemStack buyA, ItemStack buyB) {
		if (!satisfies(offer, buyA, buyB)) {
			return false;
		}
		payWith(buyA, offer.getCostA().getCount());
		if (offer.getItemCostB().isPresent()) {
			payWith(buyB, offer.getCostB().getCount());
		}
		return true;
	}

	private static void payWith(ItemStack stack, int count) {
		if (stack.getItem() instanceof PaymentCardItem card) {
			card.pay(stack, count, PAYER.get() instanceof ServerPlayer player ? player : null);
		} else {
			stack.shrink(count);
		}
	}

	/** Whether a card in one of these payment slots can't be used by this player (a frozen credit card). */
	public static boolean blocked(Player player, ItemStack... stacks) {
		for (ItemStack stack : stacks) {
			if (stack.getItem() instanceof PaymentCardItem card && !card.usableBy(player)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Index of the card among these stacks to pay {@code count} emeralds with: one this player may use that
	 * can cover it, highest {@link PaymentCardItem#priority} first (debit before credit). -1 if there isn't
	 * one.
	 */
	public static int pickCard(Iterable<ItemStack> stacks, int count, Player player) {
		int best = -1;
		int bestPriority = Integer.MIN_VALUE;
		int i = 0;
		for (ItemStack stack : stacks) {
			if (stack.getItem() instanceof PaymentCardItem card && card.priority() > bestPriority && card.usableBy(player)
					&& card.canPay(stack, count)) {
				best = i;
				bestPriority = card.priority();
			}
			i++;
		}
		return best;
	}

	/** Server side, when no card could pay: tells the player why the first card with something to say declined. */
	public static void explainDecline(Iterable<ItemStack> stacks, int count, ServerPlayer player) {
		for (ItemStack stack : stacks) {
			if (stack.getItem() instanceof PaymentCardItem card && card.usableBy(player)) {
				Component reason = card.declined(stack, count);
				if (reason != null) {
					player.sendSystemMessage(reason.copy().withStyle(ChatFormatting.RED));
					return;
				}
			}
		}
	}
}
