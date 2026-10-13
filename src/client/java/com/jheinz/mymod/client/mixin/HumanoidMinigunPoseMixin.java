package com.jheinz.mymod.client.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.mymod.minigun.MinigunItem;

/**
 * The Minigun is carried at the hip: both arms hang {@link #DROP} lower than the two-handed crossbow hold guns use, and
 * follow the head's pitch only part of the way. The gun's third-person transform (tools/gen_minigun.py, TP_DROP) turns
 * it back up by the same angle, so it points ahead from the hip.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidMinigunPoseMixin {
	private static final float DROP = (float) Math.toRadians(43.0);
	/** How much of the head's pitch the arms (and so the gun) follow. */
	private static final float PITCH_FOLLOW = 0.4f;

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
	private void mymod$carryAtHip(HumanoidRenderState state, CallbackInfo ci) {
		if (!(state instanceof AvatarRenderState) || !(state.getMainHandItemStack().getItem() instanceof MinigunItem)
				|| !state.getUseItemStackForArm(state.mainArm.getOpposite()).isEmpty()) {
			return;
		}
		HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
		float lower = DROP - (1.0f - PITCH_FOLLOW) * model.head.xRot;
		model.rightArm.xRot += lower;
		model.leftArm.xRot += lower;
	}
}
