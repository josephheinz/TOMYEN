package com.jheinz.tomyen.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.tomyen.dragonscale.DragonscaleArmor;

/**
 * Each dragonscale piece worn (or socketed) protects against fire like a level of Fire Protection, counted with the
 * enchantments' protection so it shares their cap.
 */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperDragonscaleMixin {
	@Inject(method = "getDamageProtection", at = @At("RETURN"), cancellable = true)
	private static void tomyen$dragonscaleFireProtection(ServerLevel level, LivingEntity victim, DamageSource source,
			CallbackInfoReturnable<Float> cir) {
		float extra = DragonscaleArmor.fireProtection(victim, source);
		if (extra > 0.0f) {
			cir.setReturnValue(cir.getReturnValueF() + extra);
		}
	}
}
