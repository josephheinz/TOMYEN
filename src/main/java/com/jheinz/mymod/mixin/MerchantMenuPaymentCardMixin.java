package com.jheinz.mymod.mixin;

import java.util.List;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.mymod.bank.PaymentCards;

/**
 * Picking a trade from the list fills the payment slots from the inventory. When there aren't enough
 * emeralds for an emerald cost, this swaps them back out for a payment card that can cover it (a debit
 * card before a credit card), so a card anywhere in the inventory works without dragging it into the slot
 * by hand. If none can, the server says why a card declined.
 */
@Mixin(MerchantMenu.class)
public abstract class MerchantMenuPaymentCardMixin extends AbstractContainerMenu {
	/** Player inventory slots in the menu. */
	private static final int INV_START = 3;
	private static final int INV_END = 39;

	@Shadow
	@Final
	private Merchant trader;

	@Shadow
	@Final
	private MerchantContainer tradeContainer;

	protected MerchantMenuPaymentCardMixin(MenuType<?> type, int containerId) {
		super(type, containerId);
	}

	@Inject(method = "tryMoveItems", at = @At("TAIL"))
	private void mymod$payByCard(int tradeIndex, CallbackInfo ci) {
		Player player = trader.getTradingPlayer();
		if (tradeIndex < 0 || tradeIndex >= trader.getOffers().size() || player == null) {
			return;
		}
		MerchantOffer offer = trader.getOffers().get(tradeIndex);
		mymod$fillWithCard(player, 0, offer.getItemCostA(), offer.getCostA().getCount());
		offer.getItemCostB().ifPresent(costB -> mymod$fillWithCard(player, 1, costB, costB.count()));
	}

	private void mymod$fillWithCard(Player player, int paymentSlot, ItemCost cost, int count) {
		ItemStack current = tradeContainer.getItem(paymentSlot);
		if (!PaymentCards.isEmeraldCost(cost) || PaymentCards.isCard(current) || cost.test(current) && current.getCount() >= count) {
			return;
		}
		List<ItemStack> inventory = slots.subList(INV_START, INV_END).stream().map(Slot::getItem).toList();
		int card = PaymentCards.pickCard(inventory, count, player);
		if (card < 0) {
			if (player instanceof ServerPlayer serverPlayer) {
				PaymentCards.explainDecline(inventory, count, serverPlayer);
			}
			return;
		}
		// Not enough cash: put back what emeralds there were, and hand over the card instead.
		Slot cardSlot = slots.get(INV_START + card);
		if (!current.isEmpty() && !moveItemStackTo(current, INV_START, INV_END, true)) {
			return;
		}
		tradeContainer.setItem(paymentSlot, cardSlot.getItem());
		cardSlot.set(ItemStack.EMPTY);
	}
}
