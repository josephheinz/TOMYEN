package com.jheinz.tomyen.client.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.HumanoidArm;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.tomyen.GunItem;
import com.jheinz.tomyen.client.GunArmPoses;
import com.jheinz.tomyen.client.launcher.LauncherAnimation;
import com.jheinz.tomyen.launcher.RocketLauncherItem;

/**
 * A player holding a gun one-handed (something in the off hand) points it with the gun arm alone, at the
 * same angle as the gun arm of the two-handed crossbow pose, so the gun's third-person transform still fits.
 * The other arm keeps vanilla's pose for whatever it holds. Held in both hands, the support arm reloads and works
 * the gun (GunArmPoses). A rocket launcher's arms are posed by LauncherAnimation instead.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidGunAimMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
	private void tomyen$aimOneHanded(HumanoidRenderState state, CallbackInfo ci) {
		if (state instanceof AvatarRenderState && state.getMainHandItemStack().getItem() instanceof RocketLauncherItem) {
			// A launcher goes on the shoulder, held one-handed or not.
			LauncherAnimation.poseArms((HumanoidModel<?>) (Object) this, state);
			return;
		}
		if (!(state instanceof AvatarRenderState avatar) || !(state.getMainHandItemStack().getItem() instanceof GunItem)) {
			return;
		}
		if (state.getUseItemStackForArm(state.mainArm.getOpposite()).isEmpty()) {
			// Held in both hands: the support arm works the gun through reloads and the like.
			GunArmPoses.pose((HumanoidModel<?>) (Object) this, avatar);
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
