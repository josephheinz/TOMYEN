package com.jheinz.mymod.client.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The Nose Hair Whip's lash reads the held item's display transform to find the handle tip (client/schnozzle/WhipHandles). */
@Mixin(ItemStackRenderState.class)
public interface ItemStackRenderStateAccessor {
	@Accessor("layers")
	ItemStackRenderState.LayerRenderState[] mymod$layers();
}
