package com.jheinz.mymod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.jheinz.mymod.starforge.InfusionEffects;

/**
 * Star Forge infusions in the damage path: an infused weapon's bonus (Starlit, Consecrated) on the way in,
 * Voidtouched's armor-piercing share, and infused armor's resistances after armor.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityInfusionMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float mymod$infusedWeapon(float damage, ServerLevel level, DamageSource source) {
		return InfusionEffects.outgoing((LivingEntity) (Object) this, source, damage);
	}

	@WrapOperation(method = "getDamageAfterArmorAbsorb", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/damagesource/CombatRules;getDamageAfterAbsorb(Lnet/minecraft/world/entity/LivingEntity;FLnet/minecraft/world/damagesource/DamageSource;FF)F"))
	private float mymod$voidtouchedPierce(LivingEntity victim, float damage, DamageSource source, float armor, float toughness,
			Operation<Float> original) {
		return InfusionEffects.pierce(source, damage, original.call(victim, damage, source, armor, toughness));
	}

	@ModifyVariable(method = "getDamageAfterMagicAbsorb", at = @At("HEAD"), argsOnly = true)
	private float mymod$infusedArmor(float damage, DamageSource source) {
		return InfusionEffects.incoming((LivingEntity) (Object) this, source, damage);
	}
}
