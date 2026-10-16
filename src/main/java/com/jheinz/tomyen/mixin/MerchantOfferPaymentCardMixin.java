package com.jheinz.tomyen.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.tomyen.bank.PaymentCards;

/** A payment card in a payment slot counts as the emeralds that slot's cost asks for, and paying uses it. */
@Mixin(MerchantOffer.class)
public abstract class MerchantOfferPaymentCardMixin {
	@Inject(method = "satisfiedBy", at = @At("HEAD"), cancellable = true)
	private void tomyen$acceptCard(ItemStack buyA, ItemStack buyB, CallbackInfoReturnable<Boolean> cir) {
		if (PaymentCards.isCard(buyA) || PaymentCards.isCard(buyB)) {
			cir.setReturnValue(PaymentCards.satisfies((MerchantOffer) (Object) this, buyA, buyB));
		}
	}

	// Vanilla would shrink the card by the emerald count, destroying it, so cards are paid with here instead.
	@Inject(method = "take", at = @At("HEAD"), cancellable = true)
	private void tomyen$payByCard(ItemStack buyA, ItemStack buyB, CallbackInfoReturnable<Boolean> cir) {
		if (PaymentCards.isCard(buyA) || PaymentCards.isCard(buyB)) {
			cir.setReturnValue(PaymentCards.take((MerchantOffer) (Object) this, buyA, buyB));
		}
	}
}
