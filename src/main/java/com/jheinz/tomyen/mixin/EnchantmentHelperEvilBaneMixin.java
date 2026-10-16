package com.jheinz.tomyen.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.tomyen.holy.HolyLight;
import com.jheinz.tomyen.silver.EvilBane;

/**
 * Silver weapons bite the evil (see {@link EvilBane}): the multiplier goes on after the enchantments, so a player's hit
 * shows it as enchanted damage (scaled by the attack cooldown like the rest), and sweeps and mobs' hits get it too.
 */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperEvilBaneMixin {
	@Inject(method = "modifyDamage", at = @At("RETURN"), cancellable = true)
	private static void tomyen$evilBane(ServerLevel level, ItemStack weapon, Entity victim, DamageSource source, float damage,
			CallbackInfoReturnable<Float> cir) {
		EvilBane bane = EvilBane.of(weapon);
		if (bane != null && HolyLight.isEvil(victim)) {
			cir.setReturnValue(cir.getReturnValueF() * bane.multiplier());
			bane.strike(level, victim);
		}
	}
}
