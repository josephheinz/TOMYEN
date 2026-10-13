package com.jheinz.mymod.mixin;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.mymod.froststeel.Froststeel;

/**
 * Each piece of froststeel armor takes its share off freeze damage, before protection enchantments; the full set
 * can't freeze at all, as if it were leather (so powder snow neither frosts it over nor hurts it).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityFroststeelMixin {
	@ModifyVariable(method = "getDamageAfterMagicAbsorb", at = @At("HEAD"), argsOnly = true)
	private float mymod$froststeelFreezeResistance(float damage, DamageSource source) {
		return source.is(DamageTypeTags.IS_FREEZING) ? damage * Froststeel.freezeDamageScale((LivingEntity) (Object) this) : damage;
	}

	@Inject(method = "canFreeze", at = @At("HEAD"), cancellable = true)
	private void mymod$froststeelColdProof(CallbackInfoReturnable<Boolean> cir) {
		if (Froststeel.coldProof((LivingEntity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
