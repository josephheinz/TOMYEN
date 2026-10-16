package com.jheinz.tomyen.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.tomyen.bigredboots.BigRedBoots;

/**
 * A landing in Big Red Boots from high enough stomps (the halved fall damage itself is an attribute on the boots).
 * Players reach this through Player.causeFallDamage's super call, unless they can fly.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityBigRedBootsMixin {
	@Inject(method = "causeFallDamage", at = @At("HEAD"))
	private void tomyen$bigRedBootsStomp(double fallDistance, float damageModifier, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
		BigRedBoots.land((LivingEntity) (Object) this, fallDistance);
	}
}
