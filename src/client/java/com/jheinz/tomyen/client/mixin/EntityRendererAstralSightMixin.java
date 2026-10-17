package com.jheinz.tomyen.client.mixin;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.tomyen.client.fusion.gear.AstralVisorClient;

/**
 * The Astral Visor's sight: mobs near its wearer get the glowing-effect outline (seen through walls) in a soft violet,
 * on this client only. Something already outlined (glowing, a team colour) keeps its own.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererAstralSightMixin {
	@Inject(method = "extractRenderState", at = @At("RETURN"))
	private void tomyen$astralSight(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
		if (state.outlineColor == EntityRenderState.NO_OUTLINE) {
			int outline = AstralVisorClient.outline(entity);
			if (outline != 0) {
				state.outlineColor = outline;
			}
		}
	}
}
