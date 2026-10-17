package com.jheinz.tomyen.client.accountant;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

import com.jheinz.tomyen.MyMod;

/**
 * The Accountant, everything but his jelly shell (that's {@link #SHELL_LAYER}, drawn see-through over this by
 * {@link AccountantRenderer}): the core with its heavy-lidded eyes and flat mouth, a double-breasted pinstripe jacket with
 * lapels, shirt collar, striped tie, buttons and pocket square, stubby jelly hands out of the sleeves, an attache case in
 * the right, round glasses and a pencil resting on them. Drawn at twice a slime's scale (the renderer halves it).
 * <p>
 * Hopping, his tie flies up and his arms flare out, the case swinging; landing jolts his glasses down. To audit, his
 * left arm swings up and its jelly hand stretches out of the cuff to push his glasses back up his face; then he looks
 * over the figures, the glasses nodding, the case lifted.
 * <p>
 * The cube layout in createBodyLayer and createShellLayer, and the reach of the audit, are written by
 * tools/gen_accountant.py, which also paints the texture to match and mirrors setupAnim for its preview: change the shape
 * there, and keep the two animations in step.
 */
public class AccountantModel extends EntityModel<AccountantRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(MyMod.id("accountant"), "main");
	public static final ModelLayerLocation SHELL_LAYER = new ModelLayerLocation(MyMod.id("accountant"), "shell");

	// BEGIN GENERATED REACH by tools/gen_accountant.py
	private static final float REACH_X = -1.859f;
	private static final float REACH_Y = 0.698f;
	private static final float REACH_STRETCH = 5.218f;
	// END GENERATED REACH

	private final ModelPart tie;
	private final ModelPart glasses;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart leftHand;
	private final ModelPart briefcase;

	public AccountantModel(ModelPart root) {
		super(root);
		ModelPart body = root.getChild("body");
		tie = body.getChild("tie");
		glasses = body.getChild("glasses");
		rightArm = body.getChild("right_arm");
		leftArm = body.getChild("left_arm");
		leftHand = leftArm.getChild("left_hand");
		briefcase = rightArm.getChild("right_hand").getChild("case");
	}

	public static LayerDefinition createBodyLayer() {
		// BEGIN GENERATED BODY by tools/gen_accountant.py
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create()
				.texOffs(68, 32).addBox(-5.000f, -13.000f, -5.000f, 10.000f, 10.000f, 10.000f)
				.texOffs(78, 58).addBox(-4.000f, -15.000f, -8.250f, 2.000f, 2.000f, 2.000f)
				.texOffs(78, 58).mirror().addBox(2.000f, -15.000f, -8.250f, 2.000f, 2.000f, 2.000f).mirror(false)
				.texOffs(0, 67).addBox(-1.000f, -11.500f, -8.250f, 2.000f, 1.000f, 1.000f)
				.texOffs(0, 32).addBox(-8.500f, -10.000f, -8.500f, 17.000f, 9.000f, 17.000f)
				.texOffs(94, 58).addBox(-2.500f, -10.250f, -9.150f, 2.000f, 2.000f, 1.000f)
				.texOffs(94, 58).mirror().addBox(0.500f, -10.250f, -9.150f, 2.000f, 2.000f, 1.000f).mirror(false)
				.texOffs(100, 58).addBox(-1.000f, -10.400f, -9.500f, 2.000f, 2.000f, 1.000f)
				.texOffs(112, 58).addBox(-4.000f, -4.500f, -9.000f, 1.000f, 1.000f, 1.000f)
				.texOffs(112, 58).mirror().addBox(3.000f, -4.500f, -9.000f, 1.000f, 1.000f, 1.000f).mirror(false)
				.texOffs(6, 67).addBox(4.500f, -9.000f, -9.000f, 2.000f, 1.000f, 1.000f),
				PartPose.offset(0.000f, 24.000f, 0.000f));
		p_body.addOrReplaceChild("tie", CubeListBuilder.create()
				.texOffs(18, 58).addBox(-1.500f, 0.000f, -0.250f, 3.000f, 7.000f, 1.000f),
				PartPose.offset(0.000f, -8.400f, -9.000f));
		p_body.addOrReplaceChild("pencil", CubeListBuilder.create()
				.texOffs(32, 58).addBox(-0.500f, -0.500f, -3.000f, 1.000f, 1.000f, 6.000f),
				PartPose.offsetAndRotation(-9.000f, -16.000f, -3.000f, 0.220f, 0.000f, 0.000f));
		p_body.addOrReplaceChild("glasses", CubeListBuilder.create()
				.texOffs(68, 58).addBox(-5.000f, -2.000f, -0.500f, 4.000f, 4.000f, 1.000f)
				.texOffs(68, 58).mirror().addBox(1.000f, -2.000f, -0.500f, 4.000f, 4.000f, 1.000f).mirror(false)
				.texOffs(106, 58).addBox(-1.000f, -1.000f, -0.250f, 2.000f, 1.000f, 1.000f)
				.texOffs(120, 58).addBox(-7.750f, -1.000f, -0.250f, 3.000f, 1.000f, 1.000f)
				.texOffs(120, 58).mirror().addBox(4.750f, -1.000f, -0.250f, 3.000f, 1.000f, 1.000f).mirror(false)
				.texOffs(0, 58).addBox(-8.750f, -1.000f, -0.250f, 1.000f, 1.000f, 8.000f)
				.texOffs(0, 58).mirror().addBox(7.750f, -1.000f, -0.250f, 1.000f, 1.000f, 8.000f).mirror(false),
				PartPose.offset(0.000f, -14.000f, -9.500f));
		p_body.addOrReplaceChild("right_lapel", CubeListBuilder.create()
				.texOffs(26, 58).addBox(-1.000f, 0.000f, -0.500f, 2.000f, 6.000f, 1.000f),
				PartPose.offsetAndRotation(-2.500f, -10.000f, -8.500f, 0.000f, 0.000f, -0.400f));
		PartDefinition p_right_arm = p_body.addOrReplaceChild("right_arm", CubeListBuilder.create()
				.texOffs(46, 58).addBox(-1.500f, 0.000f, -1.500f, 3.000f, 4.000f, 3.000f),
				PartPose.offset(-9.500f, -9.500f, 0.000f));
		PartDefinition p_right_hand = p_right_arm.addOrReplaceChild("right_hand", CubeListBuilder.create()
				.texOffs(86, 58).addBox(-1.000f, 0.000f, -1.000f, 2.000f, 2.000f, 2.000f),
				PartPose.offset(0.000f, 4.000f, 0.000f));
		p_body.addOrReplaceChild("left_lapel", CubeListBuilder.create()
				.texOffs(26, 58).mirror().addBox(-1.000f, 0.000f, -0.600f, 2.000f, 6.000f, 1.000f).mirror(false),
				PartPose.offsetAndRotation(2.500f, -10.000f, -8.500f, 0.000f, 0.000f, 0.400f));
		PartDefinition p_left_arm = p_body.addOrReplaceChild("left_arm", CubeListBuilder.create()
				.texOffs(46, 58).mirror().addBox(-1.500f, 0.000f, -1.500f, 3.000f, 4.000f, 3.000f).mirror(false),
				PartPose.offset(9.500f, -9.500f, 0.000f));
		p_left_arm.addOrReplaceChild("left_hand", CubeListBuilder.create()
				.texOffs(86, 58).mirror().addBox(-1.000f, 0.000f, -1.000f, 2.000f, 2.000f, 2.000f).mirror(false),
				PartPose.offset(0.000f, 4.000f, 0.000f));
		p_right_hand.addOrReplaceChild("case", CubeListBuilder.create()
				.texOffs(58, 58).addBox(-0.500f, -0.750f, -2.000f, 1.000f, 1.000f, 4.000f)
				.texOffs(108, 32).addBox(-1.500f, 0.250f, -3.500f, 2.000f, 3.000f, 7.000f)
				.texOffs(116, 58).addBox(-2.000f, 0.750f, -2.500f, 1.000f, 1.000f, 1.000f)
				.texOffs(116, 58).addBox(-2.000f, 0.750f, 1.500f, 1.000f, 1.000f, 1.000f),
				PartPose.offset(0.000f, 1.500f, 0.000f));
		return LayerDefinition.create(mesh, 128, 128);
		// END GENERATED BODY
	}

	public static LayerDefinition createShellLayer() {
		// BEGIN GENERATED SHELL by tools/gen_accountant.py
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("shell", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-8.000f, -16.000f, -8.000f, 16.000f, 16.000f, 16.000f),
				PartPose.offset(0.000f, 24.000f, 0.000f));
		return LayerDefinition.create(mesh, 128, 128);
		// END GENERATED SHELL
	}

	@Override
	public void setupAnim(AccountantRenderState state) {
		super.setupAnim(state);
		float age = state.ageInTicks;
		float air = Math.max(0.0f, state.squish);
		float land = Math.max(0.0f, -state.squish) * 2.0f;

		// The tie flies up and the arms flare out in the air; the glasses jolt down on landing. A little idle sway.
		tie.xRot += -1.1f * air + 0.06f * Mth.sin(age * 0.11f);
		tie.zRot += 0.05f * Mth.sin(age * 0.07f);
		rightArm.xRot += 0.06f * Mth.sin(age * 0.09f);
		rightArm.zRot += 0.35f * air;
		leftArm.xRot -= 0.06f * Mth.sin(age * 0.09f);
		leftArm.zRot -= 0.35f * air;
		briefcase.xRot += -0.06f * Mth.sin(age * 0.09f) + 0.08f * Mth.sin(age * 0.13f);
		briefcase.zRot -= 0.3f * air;
		glasses.y += 0.5f * land;

		float audit = state.audit;
		if (audit > 0.0f) {
			// Arm up, the hand's jelly stretching (and thinning) out of the cuff to the bridge of his glasses; a push
			// that slides them up; then a nod over the figures with the case lifted.
			float raise = smooth(0.0f, 0.18f, audit) * (1.0f - smooth(0.42f, 0.56f, audit));
			float push = smooth(0.2f, 0.3f, audit) * (1.0f - smooth(0.52f, 0.7f, audit));
			float read = smooth(0.55f, 0.65f, audit) * (1.0f - smooth(0.92f, 1.0f, audit));
			leftArm.xRot += REACH_X * raise;
			leftArm.yRot += REACH_Y * raise;
			float stretch = 1.0f + (REACH_STRETCH - 1.0f) * raise + 0.25f * push;
			float thin = Math.max(0.55f, 1.0f / Mth.sqrt(stretch));
			leftHand.xScale = thin;
			leftHand.yScale = stretch;
			leftHand.zScale = thin;
			glasses.xRot += 0.1f * read * Mth.sin(audit * 40.0f);
			glasses.y -= 0.9f * push;
			briefcase.xRot += 0.25f * read;
			rightArm.xRot -= 0.3f * read;
		}
	}

	private static float smooth(float from, float to, float t) {
		float x = Mth.clamp((t - from) / (to - from), 0.0f, 1.0f);
		return x * x * (3.0f - 2.0f * x);
	}
}
