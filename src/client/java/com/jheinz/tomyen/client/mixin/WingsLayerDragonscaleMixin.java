package com.jheinz.tomyen.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.jheinz.tomyen.dragonscale.Dragonscales;

/**
 * The dragonscale chestplate's wings are vanilla's elytra, drawn from its equipment asset's wings layer (a dragon-wing
 * texture). Gliding they spread like an elytra's; the rest of the time they're folded small against the shoulder
 * blades as wing nubs. Socketed in an exoskeleton it's the same (WingsLayerExoSocketsMixin hands this layer the
 * socketed chestplate).
 */
@Mixin(WingsLayer.class)
public abstract class WingsLayerDragonscaleMixin {
	/** How big the folded wings are, and how far down and back they sit: on the chestplate's back, not inside it. */
	@Unique
	private static final float NUB_SCALE = 0.38f;
	@Unique
	private static final float NUB_DROP = 1.0f / 16.0f;
	@Unique
	private static final float NUB_BACK = 1.6f / 16.0f;

	@WrapOperation(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/entity/layers/EquipmentLayerRenderer;renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V"))
	private void tomyen$wingNubs(EquipmentLayerRenderer renderer, EquipmentClientInfo.LayerType layer, ResourceKey<EquipmentAsset> asset,
			Model<?> model, Object state, ItemStack stack, PoseStack poseStack, SubmitNodeCollector collector, int light,
			Identifier playerTexture, int outlineColor, int order, Operation<Void> original) {
		boolean folded = stack.is(Dragonscales.CHESTPLATE) && state instanceof HumanoidRenderState humanoid && !humanoid.isFallFlying;
		if (folded) {
			poseStack.pushPose();
			poseStack.translate(0.0f, NUB_DROP, NUB_BACK);
			poseStack.scale(NUB_SCALE, NUB_SCALE, NUB_SCALE);
		}
		original.call(renderer, layer, asset, model, state, stack, poseStack, collector, light, playerTexture, outlineColor, order);
		if (folded) {
			poseStack.popPose();
		}
	}
}
