package com.jheinz.mymod.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.jheinz.mymod.exoskeleton.ExoSockets;

/**
 * A head item without an armor texture (a carved pumpkin, a mob head) socketed in a worn exoskeleton is drawn on
 * the head as if worn.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererExoSocketsMixin {
	@WrapOperation(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/LivingEntity;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack mymod$socketedHeadItem(LivingEntity entity, EquipmentSlot slot, Operation<ItemStack> original) {
		ItemStack worn = original.call(entity, slot);
		return worn.isEmpty() && slot == EquipmentSlot.HEAD ? ExoSockets.worn(entity, slot) : worn;
	}
}
