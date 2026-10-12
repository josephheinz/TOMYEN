package com.jheinz.mymod.bank;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import org.jspecify.annotations.Nullable;

/**
 * A debit card: pays a trade's emeralds straight out of a bank vault (see {@link Vaults}), the same way a
 * credit card does (see {@link PaymentCards}) but without any debt. A new card isn't linked to anyone;
 * right-clicking an ATM with it links it to that player's vault for good. From then on whoever pays with it
 * spends the holder's emeralds, so don't lose it.
 * <p>
 * It declines when the vault is short, or when the holder is offline (a vault is only reachable while its
 * owner is on). It never wears out, is picked over a credit card when a trade's payment is filled in for
 * you, and keeps working while its holder is in collections: only credit is frozen.
 */
public class DebitCardItem extends PaymentCardItem {
	public DebitCardItem(Properties properties) {
		super(properties);
	}

	/** Whose vault the card spends from, or null if it hasn't been linked yet. */
	public static @Nullable CardHolder holder(ItemStack card) {
		return card.get(Banking.CARD_HOLDER);
	}

	/** Links an unlinked card to this player's vault. */
	public static void link(ItemStack card, ServerPlayer player) {
		card.set(Banking.CARD_HOLDER, new CardHolder(player.getUUID(), player.getName().getString()));
		player.sendSystemMessage(Component.translatable("message.mymod.bank.debit_linked").withStyle(ChatFormatting.GREEN));
	}

	@Override
	public boolean canPay(ItemStack card, int emeralds) {
		CardHolder holder = holder(card);
		return holder != null && Vaults.canWithdraw(holder.id(), emeralds);
	}

	@Override
	public void pay(ItemStack card, int emeralds, @Nullable ServerPlayer payer) {
		CardHolder holder = holder(card);
		if (payer != null && holder != null) {
			Vaults.charge(holder, emeralds, payer);
		}
	}

	@Override
	public int priority() {
		return 1;
	}

	@Override
	public @Nullable Component declined(ItemStack card, int emeralds) {
		CardHolder holder = holder(card);
		if (holder == null) {
			return Component.translatable("message.mymod.bank.debit_unlinked");
		}
		VaultContainer vault = Vaults.find(holder.id());
		if (vault == null) {
			return Component.translatable("message.mymod.bank.debit_offline", holder.name());
		}
		return Component.translatable("message.mymod.bank.debit_declined", emeralds, holder.name(), Vaults.emeralds(vault));
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		CardHolder holder = holder(stack);
		if (holder == null) {
			tooltip.accept(Component.translatable("item.mymod.debit_card.unlinked").withStyle(ChatFormatting.YELLOW));
		} else {
			tooltip.accept(Component.translatable("item.mymod.debit_card.holder", holder.name()).withStyle(ChatFormatting.GRAY));
			// Only the holder's own client knows the vault's contents.
			int balance = Vaults.clientBalance(holder.id());
			if (balance >= 0) {
				tooltip.accept(Component.translatable("item.mymod.debit_card.balance", balance).withStyle(ChatFormatting.GREEN));
			}
		}
		tooltip.accept(Component.translatable("item.mymod.debit_card.hint").withStyle(ChatFormatting.DARK_GRAY));
	}
}
