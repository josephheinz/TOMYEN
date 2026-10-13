package com.jheinz.mymod.client.alien;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

import com.jheinz.mymod.MyMod;

/**
 * A little alien, about a block and a third tall: a big head (a wide block of face tapering to a small chin) on a narrow
 * body in a silver suit, with long thin arms and short thin legs. Seven cubes in all; the face, eyes and suit are all
 * in the texture (tools/gen_ufo.py).
 *
 * <p>It toddles on its short legs with a quick stride, arms swinging, head bobbing and tipping curiously; raising its
 * ray gun arm to zap; and dazed, it sways and lolls its head.
 */
public class AlienModel extends EntityModel<AlienRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(MyMod.id("alien"), "main");

	private final ModelPart head;
	private final ModelPart body;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;

	public AlienModel(ModelPart root) {
		super(root);
		head = root.getChild("head");
		body = root.getChild("body");
		rightArm = root.getChild("right_arm");
		leftArm = root.getChild("left_arm");
		rightLeg = root.getChild("right_leg");
		leftLeg = root.getChild("left_leg");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("head", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-4.5f, -9.0f, -4.0f, 9.0f, 8.0f, 8.0f)
				// A narrow chin under it, so the head tapers.
				.texOffs(34, 0).addBox(-2.5f, -1.0f, -3.0f, 5.0f, 1.0f, 5.0f),
				PartPose.offset(0.0f, 12.0f, 0.0f));
		root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 16).addBox(-2.0f, 0.0f, -1.5f, 4.0f, 5.0f, 3.0f),
				PartPose.offset(0.0f, 12.0f, 0.0f));
		root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(14, 16).addBox(-1.0f, -0.5f, -0.5f, 1.0f, 7.0f, 1.0f),
				PartPose.offset(-2.0f, 12.5f, 0.0f));
		root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(14, 16).mirror().addBox(0.0f, -0.5f, -0.5f, 1.0f, 7.0f, 1.0f),
				PartPose.offset(2.0f, 12.5f, 0.0f));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(18, 16).addBox(-0.5f, 0.0f, -0.5f, 1.0f, 7.0f, 1.0f),
				PartPose.offset(-1.0f, 17.0f, 0.0f));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(18, 16).mirror().addBox(-0.5f, 0.0f, -0.5f, 1.0f, 7.0f, 1.0f),
				PartPose.offset(1.0f, 17.0f, 0.0f));
		return LayerDefinition.create(mesh, 64, 32);
	}

	@Override
	public void setupAnim(AlienRenderState state) {
		super.setupAnim(state);
		float walk = state.walkAnimationPos * 1.6f;
		float stride = Math.min(state.walkAnimationSpeed, 1.0f);
		float age = state.ageInTicks;

		head.yRot = state.yRot * Mth.DEG_TO_RAD;
		head.xRot = state.xRot * Mth.DEG_TO_RAD;
		// A curious tilt now and then, and a bob in time with its steps.
		head.zRot = 0.12f * Mth.sin(age * 0.05f + state.seed) * Mth.sin(age * 0.013f + state.seed);
		head.y += Math.abs(Mth.cos(walk)) * 0.6f * stride;

		rightLeg.xRot = Mth.cos(walk) * 1.1f * stride;
		leftLeg.xRot = Mth.cos(walk + Mth.PI) * 1.1f * stride;
		rightArm.xRot = Mth.cos(walk + Mth.PI) * 0.8f * stride;
		leftArm.xRot = Mth.cos(walk) * 0.8f * stride;
		rightArm.zRot = 0.08f + 0.04f * Mth.sin(age * 0.09f);
		leftArm.zRot = -0.08f - 0.04f * Mth.sin(age * 0.09f);

		if (state.attackTime > 0.0f) {
			// Ray gun arm up, pointing at the target.
			float raise = Mth.sin(state.attackTime * Mth.PI);
			rightArm.xRot = Mth.lerp(raise, rightArm.xRot, -1.45f + head.xRot);
			rightArm.yRot = head.yRot * raise;
		}
		if (state.dazed) {
			float sway = Mth.sin(age * 0.18f);
			head.zRot += 0.35f * sway;
			head.xRot += 0.25f + 0.1f * Mth.cos(age * 0.23f);
			body.zRot = 0.1f * sway;
			rightArm.zRot += 0.25f;
			leftArm.zRot -= 0.25f;
		}
	}
}
