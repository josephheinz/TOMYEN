package com.jheinz.mymod.bank;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

import org.jspecify.annotations.Nullable;

/**
 * Paying for trades by card, and paying the bank back.
 * <p>
 * A card sitting in a merchant payment slot counts as however many emeralds that slot's cost asks for,
 * as long as it has that much credit left. The mixins on {@code MerchantOffer} route both the "is this
 * paid for" check and the actual payment through here, so the result slot, single trades and shift-click
 * bulk trading all work the same way, on whichever side runs them; only the server's charge counts, and
 * only the server records the debt.
 */
public final class CreditCards {
	/** The player taking a trade's result right now, set around MerchantResultSlot.onTake (see the mixin). */
	public static final ThreadLocal<@Nullable Player> PAYER = new ThreadLocal<>();

	private CreditCards() {
	}

	public static boolean isCard(ItemStack stack) {
		return stack.getItem() instanceof CreditCardItem;
	}

	/** A plain emerald cost, the only kind a card can pay. */
	public static boolean isEmeraldCost(ItemCost cost) {
		return cost.item().value() == Items.EMERALD && cost.components().isEmpty();
	}

	/** Whether this stack pays {@code count} of this cost: the items themselves, or a card with the credit. */
	private static boolean pays(ItemCost cost, int count, ItemStack stack) {
		if (stack.getItem() instanceof CreditCardItem card) {
			return isEmeraldCost(cost) && card.available(stack) >= count;
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

	/** {@link MerchantOffer#take}, charging a card where one stands in for the emeralds. */
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
		if (stack.getItem() instanceof CreditCardItem card) {
			card.setBalance(stack, CreditCardItem.balance(stack) + count);
			if (PAYER.get() instanceof ServerPlayer player) {
				DebtCollection.charge(player, count, stack);
			}
		} else {
			stack.shrink(count);
		}
	}

	/** Index of the first card among these stacks with at least this much credit, or -1 if there isn't one. */
	public static int findCardSlot(Iterable<ItemStack> stacks, int count) {
		int i = 0;
		for (ItemStack stack : stacks) {
			if (stack.getItem() instanceof CreditCardItem card && card.available(stack) >= count) {
				return i;
			}
			i++;
		}
		return -1;
	}

	/** Every credit card the player is carrying, most charged first. */
	private static List<ItemStack> cardsOf(Player player) {
		List<ItemStack> cards = new ArrayList<>();
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (isCard(stack)) {
				cards.add(stack);
			}
		}
		cards.sort(Comparator.comparingInt(CreditCardItem::balance).reversed());
		return cards;
	}

	/** Clears this many emeralds off the cards the player carries, most charged first. */
	public static void restore(Player player, int emeralds) {
		for (ItemStack stack : cardsOf(player)) {
			if (emeralds <= 0) {
				return;
			}
			CreditCardItem card = (CreditCardItem) stack.getItem();
			int cleared = Math.min(emeralds, CreditCardItem.balance(stack));
			card.setBalance(stack, CreditCardItem.balance(stack) - cleared);
			emeralds -= cleared;
		}
	}

	/** Wipes the balance off every card the player carries: the account is settled. */
	public static void restoreAll(Player player) {
		for (ItemStack stack : cardsOf(player)) {
			((CreditCardItem) stack.getItem()).setBalance(stack, 0);
		}
	}

	/**
	 * Pays the debt down from the held stack: emeralds are worth one each, emerald blocks nine (change comes
	 * back in emeralds). Returns whether anything happened.
	 */
	public static boolean repay(ServerPlayer player, ItemStack held) {
		int owed = Debt.owed(player);
		boolean block = held.is(Items.EMERALD_BLOCK);
		if (!block && !held.is(Items.EMERALD)) {
			return false;
		}
		if (owed == 0) {
			player.sendOverlayMessage(Component.translatable("message.mymod.bank.nothing_owed").withStyle(ChatFormatting.GREEN));
			return true;
		}
		int paid;
		if (block) {
			int blocks = Math.min(held.getCount(), (owed + 8) / 9);
			paid = Math.min(blocks * 9, owed);
			held.shrink(blocks);
			int change = blocks * 9 - paid;
			if (change > 0) {
				player.getInventory().placeItemBackInInventory(new ItemStack(Items.EMERALD, change), Prediction.SERVER_ONLY);
			}
		} else {
			paid = Math.min(held.getCount(), owed);
			held.shrink(paid);
		}
		DebtCollection.repay(player, paid);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				paid >= owed ? SoundEvents.PLAYER_LEVELUP : SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7f, 1.2f);
		return true;
	}
}
