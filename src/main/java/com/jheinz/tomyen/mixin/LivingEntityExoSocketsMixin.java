package com.jheinz.tomyen.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.tomyen.exoskeleton.ExoSockets;

/**
 * Hits wear down an exoskeleton's socketed armor as they do worn armor, and a suit that breaks drops what was
 * socketed (see {@link ExoSockets}).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityExoSocketsMixin {
	@Inject(method = "doHurtEquipment", at = @At("TAIL"))
	private void tomyen$hurtSocketedArmor(DamageSource source, float damage, EquipmentSlot[] slots, CallbackInfo ci) {
		ExoSockets.hurtArmor((LivingEntity) (Object) this, source, damage, slots);
	}

	@Inject(method = "onEquippedItemBroken", at = @At("HEAD"))
	private void tomyen$spillSockets(ItemStack brokenItem, EquipmentSlot slot, CallbackInfo ci) {
		ExoSockets.spill((LivingEntity) (Object) this, brokenItem);
	}
}
