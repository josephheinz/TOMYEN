package com.jheinz.tomyen.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.tomyen.client.powerarmor.PowerArmorClient;
import com.jheinz.tomyen.client.powerarmor.PowerArmorRenderer;

/**
 * A player in power armor is drawn as the suit alone (PowerArmorLayer): their body is hidden, and so is
 * everything that would poke through the plate (worn armor and the exoskeleton, cape, stuck arrows and
 * stingers, shoulder parrots, a held spyglass). In first person the suit's gauntlet replaces the hand. With its
 * thrusters lit the walk cycle stops, and fast flight lays the body flat in the elytra-gliding pose.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererPowerArmorMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
			at = @At("TAIL"))
	private void tomyen$powerArmor(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		PowerArmorRenderer.Worn worn = PowerArmorClient.worn(entity, partialTicks);
		state.setData(PowerArmorRenderer.WORN, worn);
		if (worn == null) {
			return;
		}
		state.isInvisible = true;
		state.isInvisibleToPlayer = true;
		state.headEquipment = ItemStack.EMPTY;
		state.chestEquipment = ItemStack.EMPTY;
		state.legsEquipment = ItemStack.EMPTY;
		state.feetEquipment = ItemStack.EMPTY;
		state.showCape = false;
		state.arrowCount = 0;
		state.stingerCount = 0;
		state.parrotOnLeftShoulder = null;
		state.parrotOnRightShoulder = null;
		state.showExtraEars = false;
		state.heldOnHead.clear();
		if (worn.flying()) {
			// No walk cycle under thrust: the legs (and arms, bar any aim or swing) hang still.
			state.walkAnimationPos = 0.0f;
			state.walkAnimationSpeed = 0.0f;
			// Fast flight lies flat along the look like elytra gliding; the renderer eases in by fallFlyingScale(),
			// which is this squared over 100.
			state.isFallFlying = worn.flat() > 0.0f;
			state.fallFlyingTimeInTicks = 10.0f * (float) Math.sqrt(worn.flat());
		}
	}

	@Inject(method = "renderRightHand", at = @At("HEAD"), cancellable = true)
	private void tomyen$gauntletRight(PoseStack poseStack, SubmitNodeCollector collector, int light, Identifier skinTexture, boolean hasSleeve,
			CallbackInfo ci) {
		if (PowerArmorRenderer.submitFirstPersonArm(poseStack, collector, light, HumanoidArm.RIGHT)) {
			ci.cancel();
		}
	}

	@Inject(method = "renderLeftHand", at = @At("HEAD"), cancellable = true)
	private void tomyen$gauntletLeft(PoseStack poseStack, SubmitNodeCollector collector, int light, Identifier skinTexture, boolean hasSleeve,
			CallbackInfo ci) {
		if (PowerArmorRenderer.submitFirstPersonArm(poseStack, collector, light, HumanoidArm.LEFT)) {
			ci.cancel();
		}
	}
}
