package com.jheinz.mymod.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;

import com.jheinz.mymod.silver.HolySilverArmor;

/**
 * Holy silver armor against evil damage (see {@link HolySilverArmor}): each piece takes a share off the hit, and the
 * full set turns it aside (no hurt at all, so no knockback or bite effects either) with a golden flash.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHolySilverMixin {
	@WrapMethod(method = "hurtServer")
	private boolean mymod$holySilverWard(ServerLevel level, DamageSource source, float damage, Operation<Boolean> original) {
		LivingEntity self = (LivingEntity) (Object) this;
		float remaining = HolySilverArmor.remaining(self, source);
		if (remaining >= 1.0f || self.isDeadOrDying() || self.isInvulnerableTo(level, source)) {
			return original.call(level, source, damage);
		}
		if (remaining <= 0.0f) {
			HolySilverArmor.ward(level, self, source);
			return false;
		}
		return original.call(level, source, damage * remaining);
	}
}
