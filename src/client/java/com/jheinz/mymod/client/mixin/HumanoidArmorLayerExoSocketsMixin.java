package com.jheinz.mymod.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.mymod.exoskeleton.ExoSockets;

/**
 * After the worn exoskeleton (the chest piece), the chestplate socketed in it is drawn the way vanilla draws a
 * worn one: armor model, texture, trim and glint. An elytra in the chest socket has no armor layers, so this
 * draws nothing for it; WingsLayerExoSocketsMixin puts it on the back. Socketed gear (the Optic Monocle, the Servo
 * Arm Backpack) isn't any slot's armor, so it's drawn here too, each in the slot it's worn in, by its own renderer.
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerExoSocketsMixin {
	@Shadow
	private void renderArmorPiece(PoseStack poseStack, SubmitNodeCollector collector, ItemStack stack, EquipmentSlot slot, int light,
			HumanoidRenderState state) {
		throw new AssertionError();
	}

	@Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
			at = @At("TAIL"))
	private void mymod$socketedChestplate(PoseStack poseStack, SubmitNodeCollector collector, int light, HumanoidRenderState state, float yRot,
			float xRot, CallbackInfo ci) {
		ItemStack chestplate = ExoSockets.armor(state.chestEquipment, EquipmentSlot.CHEST);
		if (!chestplate.isEmpty()) {
			renderArmorPiece(poseStack, collector, chestplate, EquipmentSlot.CHEST, light, state);
		}
		for (ItemStack gear : ExoSockets.gear(state.chestEquipment)) {
			EquipmentSlot slot = ExoSockets.wornSlot(gear);
			if (slot != null) {
				renderArmorPiece(poseStack, collector, gear, slot, light, state);
			}
		}
	}
}
