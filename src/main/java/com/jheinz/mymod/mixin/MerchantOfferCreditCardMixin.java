package com.jheinz.mymod.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.mymod.bank.CreditCards;

/** A credit card in a payment slot counts as the emeralds that slot's cost asks for, and paying charges it. */
@Mixin(MerchantOffer.class)
public abstract class MerchantOfferCreditCardMixin {
	@Inject(method = "satisfiedBy", at = @At("HEAD"), cancellable = true)
	private void mymod$acceptCard(ItemStack buyA, ItemStack buyB, CallbackInfoReturnable<Boolean> cir) {
		if (CreditCards.isCard(buyA) || CreditCards.isCard(buyB)) {
			cir.setReturnValue(CreditCards.satisfies((MerchantOffer) (Object) this, buyA, buyB));
		}
	}

	// Vanilla would shrink the card by the emerald count, destroying it, so cards are paid with here instead.
	@Inject(method = "take", at = @At("HEAD"), cancellable = true)
	private void mymod$chargeCard(ItemStack buyA, ItemStack buyB, CallbackInfoReturnable<Boolean> cir) {
		if (CreditCards.isCard(buyA) || CreditCards.isCard(buyB)) {
			cir.setReturnValue(CreditCards.take((MerchantOffer) (Object) this, buyA, buyB));
		}
	}
}
