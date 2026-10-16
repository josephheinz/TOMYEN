package com.jheinz.tomyen.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.jheinz.tomyen.exoskeleton.ExoSockets;
import com.jheinz.tomyen.exoskeleton.ExoskeletonItem;

/**
 * An elytra socketed in a worn exoskeleton is drawn on the back like a worn one (texture, cape or custom
 * elytra skin, glint, spread while gliding), mounted behind the frame's power pack instead of against the body.
 */
@Mixin(WingsLayer.class)
public abstract class WingsLayerExoSocketsMixin {
	@Unique
	private static final String SUBMIT =
			"submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V";

	/** How much further back the wings sit: clear of the power pack, which stands 6 px off the back. */
	@Unique
	private static final float PACK_DEPTH = 5.0f / 16.0f;

	@ModifyExpressionValue(method = SUBMIT, at = @At(value = "FIELD",
			target = "Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;chestEquipment:Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack tomyen$socketedElytra(ItemStack chest) {
		return chest.getItem() instanceof ExoskeletonItem ? ExoSockets.armor(chest, EquipmentSlot.CHEST) : chest;
	}

	@WrapOperation(method = SUBMIT, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
	private void tomyen$clearPowerPack(PoseStack poseStack, float x, float y, float z, Operation<Void> original,
			@Local(argsOnly = true) HumanoidRenderState state) {
		original.call(poseStack, x, y, state.chestEquipment.getItem() instanceof ExoskeletonItem ? z + PACK_DEPTH : z);
	}
}
