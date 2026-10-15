package com.jheinz.mymod.survival.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;

import com.jheinz.mymod.survival.ScrapArmor;

/** Scrap armor against machines and electric damage (see {@link ScrapArmor}): each piece takes a share off the hit. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityScrapArmorMixin {
	@WrapMethod(method = "hurtServer")
	private boolean mymod$scrapArmor(ServerLevel level, DamageSource source, float damage, Operation<Boolean> original) {
		LivingEntity self = (LivingEntity) (Object) this;
		float remaining = ScrapArmor.remaining(self, source);
		if (remaining >= 1.0f || self.isDeadOrDying() || self.isInvulnerableTo(level, source)) {
			return original.call(level, source, damage);
		}
		boolean hurt = original.call(level, source, damage * remaining);
		if (hurt) {
			ScrapArmor.deflect(level, self);
		}
		return hurt;
	}
}
