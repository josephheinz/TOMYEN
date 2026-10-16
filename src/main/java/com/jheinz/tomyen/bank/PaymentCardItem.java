package com.jheinz.tomyen.bank;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

/**
 * A card that pays a villager trade's emeralds for you: put it in a payment slot in place of the emeralds,
 * or pick a trade you can't afford in cash and one is moved there from your inventory (see
 * {@link PaymentCards}). What paying actually does is up to the card: a {@link CreditCardItem} runs up
 * debt, a {@link DebitCardItem} spends from a bank vault.
 */
public abstract class PaymentCardItem extends Item {
	protected PaymentCardItem(Properties properties) {
		super(properties);
	}

	/**
	 * Whether the card can cover this many emeralds right now. Asked on both sides; where the client can't
	 * know for sure it guesses, and the server has the final say.
	 */
	public abstract boolean canPay(ItemStack card, int emeralds);

	/**
	 * Pays this many emeralds, already checked with {@link #canPay}. {@code payer} is the player taking the
	 * trade, on the server; it's null for the client's prediction of the trade.
	 */
	public abstract void pay(ItemStack card, int emeralds, @Nullable ServerPlayer payer);

	/** Whether this player may pay with it at all. */
	public boolean usableBy(Player player) {
		return true;
	}

	/** Cards with a higher priority are picked first when a trade's payment is filled in for you. */
	public int priority() {
		return 0;
	}

	/** Server side: why the card can't cover this many emeralds, told to the player, or null to say nothing. */
	public @Nullable Component declined(ItemStack card, int emeralds) {
		return null;
	}
}
