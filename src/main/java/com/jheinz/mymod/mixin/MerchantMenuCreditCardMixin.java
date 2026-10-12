package com.jheinz.mymod.mixin;

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

import com.jheinz.mymod.bank.CreditCardItem;
import com.jheinz.mymod.bank.CreditCards;
import com.jheinz.mymod.bank.Debt;

/**
 * Picking a trade from the list fills the payment slots from the inventory. When there aren't enough
 * emeralds for an emerald cost, this swaps them back out for a credit card with the credit to cover it,
 * so a card anywhere in the inventory works without dragging it into the slot by hand.
 */
@Mixin(MerchantMenu.class)
public abstract class MerchantMenuCreditCardMixin extends AbstractContainerMenu {
	/** Player inventory slots in the menu. */
	private static final int INV_START = 3;
	private static final int INV_END = 39;

	@Shadow
	@Final
	private Merchant trader;

	@Shadow
	@Final
	private MerchantContainer tradeContainer;

	protected MerchantMenuCreditCardMixin(MenuType<?> type, int containerId) {
		super(type, containerId);
	}

	@Inject(method = "tryMoveItems", at = @At("TAIL"))
	private void mymod$payByCard(int tradeIndex, CallbackInfo ci) {
		Player player = trader.getTradingPlayer();
		if (tradeIndex < 0 || tradeIndex >= trader.getOffers().size() || player == null || Debt.inCollections(player)) {
			return;
		}
		MerchantOffer offer = trader.getOffers().get(tradeIndex);
		mymod$fillWithCard(0, offer.getItemCostA(), offer.getCostA().getCount());
		offer.getItemCostB().ifPresent(costB -> mymod$fillWithCard(1, costB, costB.count()));
	}

	private void mymod$fillWithCard(int paymentSlot, ItemCost cost, int count) {
		ItemStack current = tradeContainer.getItem(paymentSlot);
		if (!CreditCards.isEmeraldCost(cost) || CreditCards.isCard(current) || cost.test(current) && current.getCount() >= count) {
			return;
		}
		Slot cardSlot = null;
		for (int i = INV_START; i < INV_END; i++) {
			ItemStack stack = slots.get(i).getItem();
			if (stack.getItem() instanceof CreditCardItem card && card.available(stack) >= count) {
				cardSlot = slots.get(i);
				break;
			}
		}
		if (cardSlot == null) {
			return;
		}
		// Not enough cash: put back what emeralds there were, and hand over the card instead.
		if (!current.isEmpty() && !moveItemStackTo(current, INV_START, INV_END, true)) {
			return;
		}
		tradeContainer.setItem(paymentSlot, cardSlot.getItem());
		cardSlot.set(ItemStack.EMPTY);
	}
}
