package com.jheinz.tomyen.client.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.tomyen.werewolf.Werewolves;

/**
 * A cursed villager in the middle of turning into a werewolf shudders, the way a frozen mob or a curing zombie
 * villager does: the renderer's shake is keyed off the frozen flag, so set it while the change is under way.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererWerewolfShakeMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
			at = @At("TAIL"))
	private void tomyen$shakeWhileTransforming(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		if (entity instanceof Villager villager && villager.hasAttached(Werewolves.TRANSFORMING)) {
			state.isFullyFrozen = true;
		}
	}
}
