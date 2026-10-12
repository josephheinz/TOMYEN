package com.jheinz.mymod.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.jheinz.mymod.exoskeleton.ExoSockets;
import com.jheinz.mymod.exoskeleton.ExoskeletonItem;

/**
 * The cape treats what's in a worn exoskeleton's chest socket as the chest piece: hidden under a socketed elytra
 * and pushed out over a socketed chestplate, as with worn ones.
 */
@Mixin(CapeLayer.class)
public abstract class CapeLayerExoSocketsMixin {
	@WrapOperation(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/entity/layers/CapeLayer;hasLayer(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;)Z"))
	private boolean mymod$socketedChest(CapeLayer layer, ItemStack chest, EquipmentClientInfo.LayerType type, Operation<Boolean> original) {
		return original.call(layer, chest.getItem() instanceof ExoskeletonItem ? ExoSockets.armor(chest, EquipmentSlot.CHEST) : chest, type);
	}
}
