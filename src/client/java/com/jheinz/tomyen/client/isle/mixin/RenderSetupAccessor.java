package com.jheinz.tomyen.client.isle.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.renderer.rendertype.RenderSetup;

/** A render setup's texture bindings by sampler name (each a record whose first component is the texture's id). */
@Mixin(RenderSetup.class)
public interface RenderSetupAccessor {
	@Accessor("textures")
	Map<String, ?> tomyen$textures();
}
