package com.jheinz.tomyen.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.tomyen.powerarmor.PowerArmor;

/**
 * Power armor takes hits before any vanilla armor does: the piece hit absorbs its share (and the wear). A broken
 * piece leaves the hit to vanilla, so only the exoskeleton underneath protects.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityPowerArmorMixin {
	@Inject(method = "getDamageAfterArmorAbsorb", at = @At("HEAD"), cancellable = true)
	private void tomyen$powerArmor(DamageSource source, float damage, CallbackInfoReturnable<Float> cir) {
		float through = PowerArmor.absorb((LivingEntity) (Object) this, source, damage);
		if (through >= 0.0f) {
			cir.setReturnValue(through);
		}
	}
}
