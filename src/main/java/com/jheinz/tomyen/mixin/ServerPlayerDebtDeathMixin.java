package com.jheinz.tomyen.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.tomyen.bank.DebtCollection;

/**
 * A hunted player's death settles the hunt before their inventory drops, so a debt written off by the
 * collectors clears the cards they were carrying too. Totems have already had their chance by now.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDebtDeathMixin {
	@Inject(method = "die", at = @At("HEAD"))
	private void tomyen$settleHunt(DamageSource source, CallbackInfo ci) {
		DebtCollection.onDeath((ServerPlayer) (Object) this, source);
	}
}
