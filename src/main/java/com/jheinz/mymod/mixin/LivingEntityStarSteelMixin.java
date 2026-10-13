package com.jheinz.mymod.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.mymod.meteorite.StarSteel;

/** A full set of Star Steel armor takes the edge off every hit by night, after armor and enchantments. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityStarSteelMixin {
	@Inject(method = "getDamageAfterMagicAbsorb", at = @At("RETURN"), cancellable = true)
	private void mymod$starSteelAtNight(DamageSource source, float damage, CallbackInfoReturnable<Float> cir) {
		cir.setReturnValue(StarSteel.damageTaken((LivingEntity) (Object) this, source, cir.getReturnValueF()));
	}
}
