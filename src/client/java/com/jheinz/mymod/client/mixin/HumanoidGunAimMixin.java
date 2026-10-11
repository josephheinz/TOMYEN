package com.jheinz.mymod.client.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.HumanoidArm;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.mymod.GunItem;

/**
 * A player holding a gun one-handed (something in the off hand) points it with the gun arm alone, at the
 * same angle as the gun arm of the two-handed crossbow pose, so the gun's third-person transform still fits.
 * The other arm keeps vanilla's pose for whatever it holds.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidGunAimMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
	private void mymod$aimOneHanded(HumanoidRenderState state, CallbackInfo ci) {
		if (!(state instanceof AvatarRenderState) || !(state.getMainHandItemStack().getItem() instanceof GunItem)
				|| state.getUseItemStackForArm(state.mainArm.getOpposite()).isEmpty()) {
			return;
		}
		HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
		boolean right = state.mainArm == HumanoidArm.RIGHT;
		ModelPart arm = right ? model.rightArm : model.leftArm;
		arm.xRot = (float) (-Math.PI / 2) + model.head.xRot + 0.1f + (state.isCrouching ? 0.4f : 0.0f);
		arm.yRot = (right ? -0.3f : 0.3f) + model.head.yRot;
		arm.zRot = 0.0f;
	}
}
