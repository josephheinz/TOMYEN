package com.jheinz.tomyen.client.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.tomyen.client.schnozzle.WhipMotion;
import com.jheinz.tomyen.client.schnozzle.WhipPoses;

/**
 * A player cracking or snagging with the Nose Hair Whip swings their arm through the whip's motion (wind-up over the
 * shoulder, snap forward; or the snag's throw and yank) instead of vanilla's attack swing. Crouching and the idle arm bob
 * still add on afterwards, as they do to vanilla's swing. Any other swing is left to vanilla.
 */
@Mixin(HumanoidModel.class)
public abstract class WhipArmPoseMixin {
	@Inject(method = "setupAttackAnimation(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("HEAD"),
			cancellable = true)
	private void tomyen$whipArm(HumanoidRenderState state, CallbackInfo ci) {
		if (!(state instanceof AvatarRenderState avatar)) {
			return;
		}
		WhipMotion motion = WhipMotion.of(avatar, avatar.mainArm);
		if (motion.active()) {
			WhipPoses.poseArm((HumanoidModel<?>) (Object) this, state, avatar.mainArm, motion);
			ci.cancel();
		}
	}
}
