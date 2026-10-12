package com.jheinz.mymod.client.powerarmor;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;

import com.jheinz.mymod.powerarmor.PowerArmorPiece;

/**
 * Power armor on a player, copying the pose of their (hidden) body part for part, so it walks, swings, aims guns
 * and crouches with them. Flying, their walk cycle is stopped at the source (AvatarRendererPowerArmorMixin), and
 * fast flight lays the whole body flat like elytra gliding; hovering, the legs hang with a relaxed bend and sway.
 */
public class PowerArmorLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	public PowerArmorLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
	}

	private static PartPose bend(PartPose pose, float xRot, float zRot) {
		return PartPose.offsetAndRotation(pose.x(), pose.y(), pose.z(), pose.xRot() + xRot, pose.yRot(), pose.zRot() + zRot);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		PowerArmorRenderer.Worn worn = state.getData(PowerArmorRenderer.WORN);
		if (worn == null) {
			return;
		}
		PlayerModel body = getParentModel();
		PartPose[] parts = new PartPose[PowerArmorPiece.COUNT];
		parts[PowerArmorPiece.HELMET.ordinal()] = body.head.storePose();
		parts[PowerArmorPiece.TORSO.ordinal()] = body.body.storePose();
		parts[PowerArmorPiece.LEFT_ARM.ordinal()] = body.leftArm.storePose();
		parts[PowerArmorPiece.RIGHT_ARM.ordinal()] = body.rightArm.storePose();
		parts[PowerArmorPiece.LEFT_LEG.ordinal()] = body.leftLeg.storePose();
		parts[PowerArmorPiece.RIGHT_LEG.ordinal()] = body.rightLeg.storePose();
		if (worn.flying() && worn.flat() > 0.0f) {
			// Vanilla snaps the head to gliding at any flatness; ease it from the look instead.
			PartPose head = parts[PowerArmorPiece.HELMET.ordinal()];
			parts[PowerArmorPiece.HELMET.ordinal()] = PartPose.offsetAndRotation(head.x(), head.y(), head.z(),
					Mth.lerp(worn.flat(), state.xRot * Mth.DEG_TO_RAD, -Mth.PI / 4.0f), head.yRot(), head.zRot());
		}
		if (worn.flying()) {
			// Hanging from the thrusters: one knee a little more bent than the other, drifting slowly. Lying flat
			// into fast flight, the legs straighten out behind.
			float hang = 1.0f - worn.flat();
			float sway = Mth.sin(state.ageInTicks * 0.08f) * 0.05f;
			parts[PowerArmorPiece.RIGHT_LEG.ordinal()] = bend(parts[PowerArmorPiece.RIGHT_LEG.ordinal()], (0.2f + sway) * hang, 0.04f * hang);
			parts[PowerArmorPiece.LEFT_LEG.ordinal()] = bend(parts[PowerArmorPiece.LEFT_LEG.ordinal()], (0.07f - sway) * hang, -0.04f * hang);
		}
		PowerArmorRenderer.submit(poseStack, collector, parts, worn.look(), worn.open(), worn.flame(), state.ageInTicks, light,
				LivingEntityRenderer.getOverlayCoords(state, 0.0f), state.outlineColor, PowerArmorRenderer.SCALE);
	}
}
