package com.jheinz.mymod.client.vampire;

import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.util.Mth;

import com.jheinz.mymod.MyMod;

/** The plain player model, wearing a vampire skin in the standard 64x64 player-skin layout; the cloak is a layer on top. */
public class VampireModel extends HumanoidModel<VampireRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(MyMod.id("vampire"), "main");

	public VampireModel(ModelPart root) {
		super(root);
	}

	public static LayerDefinition createBodyLayer() {
		return LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64);
	}

	@Override
	public void setupAnim(VampireRenderState state) {
		super.setupAnim(state);
		if (!state.lunging) {
			// Arms held close under the cloak rather than swinging.
			rightArm.xRot *= 0.3f;
			leftArm.xRot *= 0.3f;
		}
		if (state.hovering) {
			// Floating: legs together, trailing slightly back and drifting, arms a little out from the sides.
			float drift = Mth.sin(state.ageInTicks * 0.09f) * 0.06f;
			rightLeg.xRot = 0.25f + drift;
			leftLeg.xRot = 0.22f + drift;
			if (!state.lunging) {
				rightArm.zRot = 0.15f;
				leftArm.zRot = -0.15f;
			}
		}
	}

	@Override
	protected void setupAttackAnimation(VampireRenderState state) {
		super.setupAttackAnimation(state);
		if (state.lunging) {
			AnimationUtils.animateZombieArms(leftArm, rightArm, true, state);
		}
	}
}
