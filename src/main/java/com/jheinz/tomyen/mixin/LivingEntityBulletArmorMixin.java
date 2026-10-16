package com.jheinz.tomyen.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.jheinz.tomyen.ModDamageTypes;
import com.jheinz.tomyen.armor.ArmorPlateItem;
import com.jheinz.tomyen.armor.PlateCarrierItem;

/**
 * Bullets: armor counts more or less depending on the round, a plate carrier's plate absorbs a share
 * of what gets through, and Projectile Protection counts double.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityBulletArmorMixin {
	private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/** Vanilla Projectile Protection is worth 2 protection points per level; bullets get this many more. */
	private static final float BULLET_PROJECTILE_PROTECTION_BONUS_PER_LEVEL = 2.0f;

	// Vanilla's armor constants (CombatRules): armor caps at 20 and each point blocks 1/25 of the damage.
	private static final float MAX_ARMOR = 20.0f;
	private static final float ARMOR_DIVIDER = 25.0f;

	@WrapOperation(method = "getDamageAfterArmorAbsorb", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/damagesource/CombatRules;getDamageAfterAbsorb(Lnet/minecraft/world/entity/LivingEntity;FLnet/minecraft/world/damagesource/DamageSource;FF)F"))
	private float tomyen$scaleArmorForBullets(LivingEntity entity, float damage, DamageSource source, float armor,
			float toughness, Operation<Float> original) {
		float multiplier = ModDamageTypes.armorMultiplier(source);
		float result = original.call(entity, damage, source, armor * multiplier, toughness * multiplier);
		if (ModDamageTypes.deformsOnArmor(source)) {
			result *= 1.0f - Math.min(armor, MAX_ARMOR) / ARMOR_DIVIDER;
		}
		ArmorPlateItem plate = PlateCarrierItem.wornPlate(entity);
		if (plate != null) {
			result *= 1.0f - plate.bulletReduction() * ModDamageTypes.plateEffectiveness(source);
		}
		return result;
	}

	@WrapOperation(method = "getDamageAfterMagicAbsorb", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getDamageProtection(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;)F"))
	private float tomyen$boostProjectileProtection(ServerLevel level, LivingEntity entity, DamageSource source,
			Operation<Float> original) {
		float protection = original.call(level, entity, source);
		if (!ModDamageTypes.isBullet(source)) {
			return protection;
		}
		Holder<Enchantment> projectileProtection = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(Enchantments.PROJECTILE_PROTECTION);
		int levels = 0;
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			levels += EnchantmentHelper.getItemEnchantmentLevel(projectileProtection, entity.getItemBySlot(slot));
		}
		return protection + levels * BULLET_PROJECTILE_PROTECTION_BONUS_PER_LEVEL;
	}
}
