package com.jheinz.tomyen.client.isle.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.EntityType;

import com.jheinz.tomyen.client.isle.RevenantRenderState;

/**
 * A Revenant's render state is an avatar's (it's drawn with the player model, which needs one), and the dispatcher sends
 * every avatar state to the player renderer. Send a Revenant's to its own renderer instead.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class RevenantRendererDispatchMixin {
	@Shadow
	private Map<EntityType<?>, EntityRenderer<?, ?>> renderers;

	@Inject(method = "getRenderer(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;)Lnet/minecraft/client/renderer/entity/EntityRenderer;",
			at = @At("HEAD"), cancellable = true)
	@SuppressWarnings({"unchecked", "rawtypes"})
	private void tomyen$revenantRenderer(EntityRenderState state, CallbackInfoReturnable<EntityRenderer> cir) {
		if (state instanceof RevenantRenderState) {
			cir.setReturnValue(renderers.get(state.entityType));
		}
	}
}
