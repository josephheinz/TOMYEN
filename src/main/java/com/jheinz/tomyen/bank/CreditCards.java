package com.jheinz.tomyen.bank;

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

/**
 * The credit account side of credit cards: clearing what's been charged to the cards a player carries, and
 * paying the bank back. Paying for trades with them is {@link PaymentCards}.
 */
public final class CreditCards {
	private CreditCards() {
	}

	public static boolean isCard(ItemStack stack) {
		return stack.getItem() instanceof CreditCardItem;
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
			player.sendOverlayMessage(Component.translatable("message.tomyen.bank.nothing_owed").withStyle(ChatFormatting.GREEN));
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
