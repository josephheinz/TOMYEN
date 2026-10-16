package com.jheinz.tomyen.mixin;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.jheinz.tomyen.magmasteel.Magmasteel;

/** Each piece of magmasteel armor takes its share off fire and lava damage, before protection enchantments. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMagmasteelMixin {
	@ModifyVariable(method = "getDamageAfterMagicAbsorb", at = @At("HEAD"), argsOnly = true)
	private float tomyen$magmasteelFireResistance(float damage, DamageSource source) {
		return source.is(DamageTypeTags.IS_FIRE) ? damage * Magmasteel.fireDamageScale((LivingEntity) (Object) this) : damage;
	}
}
