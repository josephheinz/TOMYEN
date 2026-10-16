package com.jheinz.tomyen.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.item.ItemStackRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Applies an item layer's display and local transform, exactly as drawing it does (client/schnozzle/WhipHandles). */
@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface ItemLayerRenderStateInvoker {
	@Invoker("applyTransform")
	void tomyen$applyTransform(PoseStack.Pose pose);
}
