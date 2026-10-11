package com.jheinz.mymod.client.vampire;

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

import com.jheinz.mymod.MyMod;

/**
 * A full-length red cape: a wide mantle over the shoulders and arms, a back panel that flares toward the
 * hem, side panels round the arms, front panels that hang open over the chest, a tall standing collar
 * that fans out round the back of the head, and a jewelled clasp at the throat. Every long panel is
 * hinged in two or three pieces so it can billow and ripple. Its "body" part takes the vampire model's
 * body pose each frame, so it leans and crouches with him.
 */
public class VampireCloakModel extends EntityModel<VampireRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(MyMod.id("vampire"), "cloak");

	final ModelPart body;
	private final ModelPart backUpper;
	private final ModelPart backMiddle;
	private final ModelPart backLower;
	private final ModelPart rightSide;
	private final ModelPart rightSideLower;
	private final ModelPart leftSide;
	private final ModelPart leftSideLower;
	private final ModelPart rightFront;
	private final ModelPart rightFrontLower;
	private final ModelPart leftFront;
	private final ModelPart leftFrontLower;

	public VampireCloakModel(ModelPart root) {
		super(root, RenderTypes::entityCutout);
		body = root.getChild("body");
		backUpper = body.getChild("back_upper");
		backMiddle = backUpper.getChild("back_middle");
		backLower = backMiddle.getChild("back_lower");
		rightSide = body.getChild("right_side");
		rightSideLower = rightSide.getChild("right_side_lower");
		leftSide = body.getChild("left_side");
		leftSideLower = leftSide.getChild("left_side_lower");
		rightFront = body.getChild("right_front");
		rightFrontLower = rightFront.getChild("right_front_lower");
		leftFront = body.getChild("left_front");
		leftFrontLower = leftFront.getChild("left_front_lower");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		// Mantle over the shoulders and the tops of the arms.
		PartDefinition body = root.addOrReplaceChild("body",
				CubeListBuilder.create().texOffs(0, 0).addBox(-9.0f, -1.0f, -3.0f, 18.0f, 3.0f, 7.0f), PartPose.ZERO);

		// Back panel, widening toward the hem, which just clears the ground.
		PartDefinition backUpper = body.addOrReplaceChild("back_upper",
				CubeListBuilder.create().texOffs(0, 10).addBox(-9.0f, 0.0f, 0.0f, 18.0f, 8.0f, 1.0f), PartPose.offset(0.0f, 1.0f, 4.0f));
		PartDefinition backMiddle = backUpper.addOrReplaceChild("back_middle",
				CubeListBuilder.create().texOffs(0, 19).addBox(-10.0f, 0.0f, 0.0f, 20.0f, 8.0f, 1.0f), PartPose.offset(0.0f, 8.0f, 0.0f));
		backMiddle.addOrReplaceChild("back_lower",
				CubeListBuilder.create().texOffs(0, 28).addBox(-11.0f, 0.0f, 0.0f, 22.0f, 6.0f, 1.0f), PartPose.offset(0.0f, 8.0f, 0.0f));

		// Side panels hanging outside the arms, from the front of the shoulder round to the back panel.
		PartDefinition rightSide = body.addOrReplaceChild("right_side",
				CubeListBuilder.create().texOffs(64, 0).addBox(-1.0f, 0.0f, -3.5f, 1.0f, 11.0f, 8.0f), PartPose.offset(-9.0f, 1.0f, 0.0f));
		rightSide.addOrReplaceChild("right_side_lower",
				CubeListBuilder.create().texOffs(64, 19).addBox(-1.0f, 0.0f, -3.5f, 1.0f, 11.0f, 8.0f), PartPose.offset(0.0f, 11.0f, 0.0f));
		PartDefinition leftSide = body.addOrReplaceChild("left_side",
				CubeListBuilder.create().texOffs(64, 0).mirror().addBox(0.0f, 0.0f, -3.5f, 1.0f, 11.0f, 8.0f), PartPose.offset(9.0f, 1.0f, 0.0f));
		leftSide.addOrReplaceChild("left_side_lower",
				CubeListBuilder.create().texOffs(64, 19).mirror().addBox(0.0f, 0.0f, -3.5f, 1.0f, 11.0f, 8.0f), PartPose.offset(0.0f, 11.0f, 0.0f));

		// Front panels, hinged at the outer edge so they swing open; the chest shows between them.
		PartDefinition rightFront = body.addOrReplaceChild("right_front",
				CubeListBuilder.create().texOffs(84, 0).addBox(0.0f, 0.0f, -1.0f, 5.0f, 11.0f, 1.0f), PartPose.offset(-9.0f, 1.0f, -3.5f));
		rightFront.addOrReplaceChild("right_front_lower",
				CubeListBuilder.create().texOffs(84, 12).addBox(0.0f, 0.0f, -1.0f, 5.0f, 11.0f, 1.0f), PartPose.offset(0.0f, 11.0f, 0.0f));
		PartDefinition leftFront = body.addOrReplaceChild("left_front",
				CubeListBuilder.create().texOffs(84, 0).mirror().addBox(-5.0f, 0.0f, -1.0f, 5.0f, 11.0f, 1.0f), PartPose.offset(9.0f, 1.0f, -3.5f));
		leftFront.addOrReplaceChild("left_front_lower",
				CubeListBuilder.create().texOffs(84, 12).mirror().addBox(-5.0f, 0.0f, -1.0f, 5.0f, 11.0f, 1.0f), PartPose.offset(0.0f, 11.0f, 0.0f));

		// Standing collar: a tall back piece leaning out behind the head, and a wing each side that wraps
		// forward round the head and flares out to a point.
		body.addOrReplaceChild("collar", CubeListBuilder.create().texOffs(0, 36).addBox(-5.0f, -9.0f, 0.0f, 10.0f, 9.0f, 1.0f),
				PartPose.offsetAndRotation(0.0f, -0.5f, 3.0f, -0.3f, 0.0f, 0.0f));
		body.addOrReplaceChild("left_collar", CubeListBuilder.create().texOffs(24, 36).addBox(0.0f, -9.0f, 0.0f, 5.0f, 9.0f, 1.0f),
				PartPose.offsetAndRotation(4.5f, -0.5f, 3.0f, -0.25f, 0.75f, 0.35f));
		body.addOrReplaceChild("right_collar", CubeListBuilder.create().texOffs(24, 36).mirror().addBox(-5.0f, -9.0f, 0.0f, 5.0f, 9.0f, 1.0f),
				PartPose.offsetAndRotation(-4.5f, -0.5f, 3.0f, -0.25f, -0.75f, -0.35f));

		// Clasp at the throat: a gold setting with a red stone, on a gold cord between the front panels.
		body.addOrReplaceChild("clasp", CubeListBuilder.create()
				.texOffs(40, 36).addBox(-1.0f, -1.0f, -1.0f, 2.0f, 2.0f, 1.0f)
				.texOffs(48, 36).addBox(-0.5f, -0.5f, -1.6f, 1.0f, 1.0f, 1.0f)
				.texOffs(54, 36).addBox(-4.0f, -0.2f, -0.6f, 8.0f, 1.0f, 1.0f),
				PartPose.offset(0.0f, 0.5f, -3.3f));
		return LayerDefinition.create(mesh, 128, 64);
	}

	@Override
	public void setupAnim(VampireRenderState state) {
		super.setupAnim(state);
		float age = state.ageInTicks;
		// Up to about 1 at a run; it streams out behind further the faster he goes, and always ripples in flight.
		float flow = Math.min(state.walkAnimationSpeed * 1.4f, 1.3f);
		if (state.hovering) {
			flow = Math.max(flow, 0.45f + 0.15f * Mth.sin(age * 0.09f));
		}
		// Each panel swings a little more than the one above and ripples a beat later, so a wave runs down it.
		backUpper.xRot = 0.06f + 0.7f * flow + Mth.sin(age * 0.11f) * 0.03f;
		backMiddle.xRot = 0.25f * flow + Mth.sin(age * 0.17f - 0.9f) * (0.04f + 0.12f * flow);
		backLower.xRot = 0.3f * flow + Mth.sin(age * 0.23f - 1.8f) * (0.05f + 0.18f * flow);
		backUpper.zRot = Mth.sin(age * 0.07f) * 0.02f;
		backLower.zRot = Mth.sin(age * 0.13f - 1.0f) * (0.02f + 0.04f * flow);

		// The sides flare out and trail back.
		float flare = 0.05f + 0.22f * flow;
		rightSide.zRot = flare + Mth.sin(age * 0.12f) * 0.03f;
		leftSide.zRot = -flare - Mth.sin(age * 0.12f + 1.3f) * 0.03f;
		rightSide.xRot = 0.35f * flow;
		leftSide.xRot = 0.35f * flow;
		rightSideLower.zRot = 0.5f * flare + Mth.sin(age * 0.19f - 1.0f) * (0.03f + 0.08f * flow);
		leftSideLower.zRot = -0.5f * flare - Mth.sin(age * 0.19f + 0.3f) * (0.03f + 0.08f * flow);

		// The fronts hang a little open, swing wider on the move, and fly open on a lunge.
		float open = 0.12f + 0.45f * flow + (state.lunging ? 0.9f : 0.0f);
		rightFront.yRot = open;
		leftFront.yRot = -open;
		rightFront.xRot = 0.12f * flow;
		leftFront.xRot = 0.12f * flow;
		rightFrontLower.yRot = 0.3f * open + Mth.sin(age * 0.21f) * 0.05f * flow;
		leftFrontLower.yRot = -0.3f * open - Mth.sin(age * 0.21f + 0.8f) * 0.05f * flow;

		// Crouched forward, the back still hangs down and drapes over him.
		if (state.isCrouching) {
			backUpper.xRot -= 0.25f;
			backMiddle.xRot += 0.15f;
		}
	}
}
