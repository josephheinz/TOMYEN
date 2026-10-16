package com.jheinz.tomyen.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.tomyen.bank.PaymentCards;

/**
 * Taking a trade's result: a card its holder can't use right now (credit, while they're in collections)
 * declines, and a card payment is made by the player taking the result (see {@link PaymentCards#PAYER}).
 */
@Mixin(MerchantResultSlot.class)
public abstract class MerchantResultSlotPaymentCardMixin {
	@Shadow
	@Final
	private MerchantContainer slots;

	@Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
	private void tomyen$declineFrozenCard(Player player, CallbackInfoReturnable<Boolean> cir) {
		if (PaymentCards.blocked(player, slots.getItem(0), slots.getItem(1))) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "onTake", at = @At("HEAD"))
	private void tomyen$startPaying(Player player, ItemStack carried, CallbackInfo ci) {
		PaymentCards.PAYER.set(player);
	}

	@Inject(method = "onTake", at = @At("RETURN"))
	private void tomyen$stopPaying(Player player, ItemStack carried, CallbackInfo ci) {
		PaymentCards.PAYER.remove();
	}
}
