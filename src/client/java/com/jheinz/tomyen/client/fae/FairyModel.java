package com.jheinz.tomyen.client.fae;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

import com.jheinz.tomyen.MyMod;

/**
 * A fairy: a head, a small body and two wing panes, nothing more. It bobs as it hovers, its wings beat fast, it leans
 * into its flight, and its head follows what it's looking at. Wings are translucent (the model draws translucent).
 */
public class FairyModel extends EntityModel<FairyRenderState> implements ArmedModel<FairyRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(MyMod.id("fairy"), "main");

	private final ModelPart body;
	private final ModelPart head;
	private final ModelPart rightWing;
	private final ModelPart leftWing;

	public FairyModel(ModelPart root) {
		super(root, RenderTypes::entityTranslucent);
		body = root.getChild("body");
		head = root.getChild("head");
		rightWing = body.getChild("right_wing");
		leftWing = body.getChild("left_wing");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		// It hovers: the bottom of its body sits four pixels off the ground.
		root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0f, -4.0f, -2.0f, 4.0f, 4.0f, 4.0f),
				PartPose.offset(0.0f, 17.0f, 0.0f));
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 8).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 3.0f, 2.0f),
				PartPose.offset(0.0f, 17.0f, 0.0f));
		body.addOrReplaceChild("right_wing", CubeListBuilder.create().texOffs(0, 16).addBox(0.0f, -1.0f, 0.0f, 0.0f, 6.0f, 7.0f),
				PartPose.offset(-0.5f, 0.0f, 0.9f));
		body.addOrReplaceChild("left_wing", CubeListBuilder.create().texOffs(0, 16).mirror().addBox(0.0f, -1.0f, 0.0f, 0.0f, 6.0f, 7.0f),
				PartPose.offset(0.5f, 0.0f, 0.9f));
		return LayerDefinition.create(mesh, 32, 32);
	}

	@Override
	public void setupAnim(FairyRenderState state) {
		super.setupAnim(state);
		float age = state.ageInTicks;
		float flying = Math.min(state.walkAnimationSpeed / 0.3f, 1.0f);
		float bob = Mth.sin(age * 0.15f) * 0.8f * (1.0f - flying);
		body.y += bob;
		head.y += bob;
		body.xRot = 0.6f * flying;
		head.yRot = state.yRot * Mth.DEG_TO_RAD;
		head.xRot = state.xRot * Mth.DEG_TO_RAD;
		float flap = Mth.cos(age * 2.2f) * 0.55f;
		rightWing.yRot = -Mth.PI / 4.0f + flap;
		leftWing.yRot = Mth.PI / 4.0f - flap;
		rightWing.xRot = 0.3f * (1.0f - flying);
		leftWing.xRot = 0.3f * (1.0f - flying);
	}

	/** Anything it's stolen is held in front of its body (the item layer then offsets it as for any arm). */
	@Override
	public void translateToHand(FairyRenderState state, HumanoidArm arm, PoseStack poseStack) {
		root.translateAndRotate(poseStack);
		body.translateAndRotate(poseStack);
		poseStack.translate(0.0f, 0.0625f, 0.1875f);
		poseStack.scale(0.5f, 0.5f, 0.5f);
	}
}
