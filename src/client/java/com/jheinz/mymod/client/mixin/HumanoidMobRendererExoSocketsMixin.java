package com.jheinz.mymod.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.jheinz.mymod.exoskeleton.ExoSockets;

/**
 * The head, legs and feet the armor layer draws are a worn exoskeleton's socketed pieces (those slots are empty
 * while it's worn), so vanilla draws them over the frame with their textures, trims and glint. A socketed
 * chestplate is drawn by HumanoidArmorLayerExoSocketsMixin, since the chest slot holds the suit.
 */
@Mixin(HumanoidMobRenderer.class)
public abstract class HumanoidMobRendererExoSocketsMixin {
	@WrapOperation(method = "getEquipmentIfRenderable", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"))
	private static ItemStack mymod$socketedArmor(LivingEntity entity, EquipmentSlot slot, Operation<ItemStack> original) {
		ItemStack worn = original.call(entity, slot);
		return worn.isEmpty() && slot != EquipmentSlot.CHEST && ExoSockets.ARMOR_SLOTS.contains(slot) ? ExoSockets.worn(entity, slot) : worn;
	}
}
