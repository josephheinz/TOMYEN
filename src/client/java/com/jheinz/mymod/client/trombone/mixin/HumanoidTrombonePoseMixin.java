package com.jheinz.mymod.client.trombone.mixin;

import org.joml.Matrix3f;
import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.mymod.client.trombone.TromboneHands;
import com.jheinz.mymod.client.trombone.TromboneSlide;
import com.jheinz.mymod.trombone.TromboneItem;

/**
 * A player holding a Trombone holds it up to their lips with both hands (it's drawn from the head: PlayerTromboneHeldMixin):
 * the off arm reaches for the braces by the face, the main arm for the slide's grip bar, following it out and in, each
 * arm stretched or shortened a little so the hand lands on the brass.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidTrombonePoseMixin {
	/** From the shoulder pivot to the middle of the hand, in model pixels. */
	@Unique
	private static final float ARM_REACH = 9.5f;

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
	private void mymod$holdTrombone(HumanoidRenderState state, CallbackInfo ci) {
		if (!(state instanceof AvatarRenderState avatar) || !(state.getMainHandItemStack().getItem() instanceof TromboneItem)) {
			return;
		}
		HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
		int frame = 0;
		if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.getEntity(avatar.id) instanceof Player player) {
			frame = TromboneSlide.frame(player);
		}
		boolean left = state.mainArm == HumanoidArm.LEFT;
		ModelPart mainArm = left ? model.leftArm : model.rightArm;
		ModelPart offArm = left ? model.rightArm : model.leftArm;
		mymod$aim(mainArm, mymod$fromHead(model.head, TromboneHands.thirdPersonSlide(frame), left));
		mymod$aim(offArm, mymod$fromHead(model.head, TromboneHands.thirdPersonGrip(), left));
	}

	/** A point in the trombone's head space to model space (pixels), mirrored for the left-handed. */
	@Unique
	private static Vector3f mymod$fromHead(ModelPart head, Vector3f anchor, boolean mirrored) {
		float x = mirrored ? -anchor.x : anchor.x;
		Vector3f local = new Vector3f(-x, -0.25f - anchor.y, anchor.z).mul(16.0f);
		new Matrix3f().rotationZYX(head.zRot, head.yRot, head.xRot).transform(local);
		return local.add(head.x, head.y, head.z);
	}

	/** Turns the arm (which hangs along +y) to point from its pivot at the target, and scales it to reach. */
	@Unique
	private static void mymod$aim(ModelPart arm, Vector3f target) {
		Vector3f d = target.sub(arm.x, arm.y, arm.z);
		float length = d.length();
		if (length < 1.0e-3f) {
			return;
		}
		d.div(length);
		float sinX = -Mth.sqrt(Math.max(0.0f, 1.0f - d.y * d.y));
		arm.xRot = (float) Math.atan2(sinX, d.y);
		arm.yRot = Math.abs(sinX) > 1.0e-4f ? (float) Math.atan2(d.x / sinX, d.z / sinX) : 0.0f;
		arm.zRot = 0.0f;
		arm.yScale = Mth.clamp(length / ARM_REACH, 0.85f, 1.35f);
	}
}
