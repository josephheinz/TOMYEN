package com.jheinz.mymod.mixin;

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

import com.jheinz.mymod.bank.CreditCards;
import com.jheinz.mymod.bank.Debt;

/**
 * Taking a trade's result: cards are frozen while their holder is in collections, and a card charge is
 * billed to the player taking the result (see {@link CreditCards#PAYER}).
 */
@Mixin(MerchantResultSlot.class)
public abstract class MerchantResultSlotCreditCardMixin {
	@Shadow
	@Final
	private MerchantContainer slots;

	@Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
	private void mymod$declineFrozenCard(Player player, CallbackInfoReturnable<Boolean> cir) {
		boolean card = CreditCards.isCard(slots.getItem(0)) || CreditCards.isCard(slots.getItem(1));
		if (card && Debt.inCollections(player)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "onTake", at = @At("HEAD"))
	private void mymod$startPaying(Player player, ItemStack carried, CallbackInfo ci) {
		CreditCards.PAYER.set(player);
	}

	@Inject(method = "onTake", at = @At("RETURN"))
	private void mymod$stopPaying(Player player, ItemStack carried, CallbackInfo ci) {
		CreditCards.PAYER.remove();
	}
}
