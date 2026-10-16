package com.jheinz.tomyen.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.tomyen.powerarmor.PowerArmor;

/** Power armor takes no fall damage (a heavy landing sends out a shockwave instead), and can't swim. */
@Mixin(Player.class)
public abstract class PlayerPowerArmorMixin {
	@Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
	private void tomyen$powerArmorLanding(double fallDistance, float damageModifier, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
		if (PowerArmor.land((Player) (Object) this, fallDistance)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "updateSwimming", at = @At("HEAD"), cancellable = true)
	private void tomyen$powerArmorSinks(CallbackInfo ci) {
		Player player = (Player) (Object) this;
		if (PowerArmor.isWearing(player)) {
			player.setSwimming(false);
			ci.cancel();
		}
	}
}
