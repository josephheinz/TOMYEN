package com.jheinz.mymod.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.mymod.client.mech.MechClient;
import com.jheinz.mymod.client.mech.MechFirstPerson;
import com.jheinz.mymod.mech.Mech;

/** In a Bulwark Walker's cockpit, the walker's own weapon arms where the pilot's hands and held item would be. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class MechHandsMixin {
	@Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void mymod$mechArmsInCockpit(float partialTicks, PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState playerState,
			FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
		Mech mech = MechClient.cockpitMech();
		if (mech != null) {
			MechFirstPerson.submit(mech, partialTicks, poseStack, collector);
			ci.cancel();
		}
	}
}
