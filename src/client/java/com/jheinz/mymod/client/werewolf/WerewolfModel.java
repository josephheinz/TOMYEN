package com.jheinz.mymod.client.werewolf;

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
 * The werewolf: a hunched, lean wolf-man in twelve cubes. A narrow waist (still belted in the scraps of its villager's
 * robe) leans forward under a broad chest, which leans further; the head is held level out in front of it, with a
 * long snout, a jaw that drops open and pointed ears. Long arms hang to the knees, a bushy tail droops behind.
 * <p>
 * It walks with its arms swinging against its legs, raises its claws while it hunts, rakes them down and across to
 * attack (one arm, then the other) with a snarl, and to howl throws its head back with its jaw wide and its arms out.
 * The texture and a preview of this model come from tools/gen_werewolf.py, which mirrors these cubes and poses.
 */
public class WerewolfModel extends EntityModel<WerewolfRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(MyMod.id("werewolf"), "main");

	private static final float BODY_LEAN = 0.2f;
	private static final float CHEST_LEAN = 0.3f;
	/** Mobs' swings last this many ticks. */
	private static final float SWING_TICKS = 6.0f;
	/** Swipes starting in alternate windows this long use alternate arms. */
	private static final float SWIPE_ALTERNATION = 20.0f;

	private final ModelPart body;
	private final ModelPart chest;
	private final ModelPart head;
	private final ModelPart jaw;
	private final ModelPart rightEar;
	private final ModelPart leftEar;
	private final ModelPart tail;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;

	public WerewolfModel(ModelPart root) {
		super(root);
		body = root.getChild("body");
		chest = body.getChild("chest");
		head = chest.getChild("head");
		jaw = head.getChild("jaw");
		rightEar = head.getChild("right_ear");
		leftEar = head.getChild("left_ear");
		tail = body.getChild("tail");
		rightArm = root.getChild("right_arm");
		leftArm = root.getChild("left_arm");
		rightLeg = root.getChild("right_leg");
		leftLeg = root.getChild("left_leg");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
				.texOffs(0, 19).addBox(-4.0f, -8.0f, -3.0f, 8.0f, 8.0f, 6.0f),
				PartPose.offsetAndRotation(0.0f, 9.0f, 1.0f, BODY_LEAN, 0.0f, 0.0f));
		PartDefinition chest = body.addOrReplaceChild("chest", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-6.5f, -10.0f, -4.5f, 13.0f, 10.0f, 9.0f),
				PartPose.offsetAndRotation(0.0f, -7.0f, 0.0f, CHEST_LEAN, 0.0f, 0.0f));
		// Held level, out in front of the shoulders.
		PartDefinition head = chest.addOrReplaceChild("head", CubeListBuilder.create()
				.texOffs(44, 0).addBox(-4.0f, -7.0f, -5.0f, 8.0f, 7.0f, 7.0f)
				.texOffs(74, 0).addBox(-2.0f, -4.0f, -11.0f, 4.0f, 3.0f, 6.0f),
				PartPose.offsetAndRotation(0.0f, -9.0f, -3.0f, -(BODY_LEAN + CHEST_LEAN), 0.0f, 0.0f));
		head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(74, 9).addBox(-2.0f, 0.0f, -6.0f, 4.0f, 2.0f, 6.0f),
				PartPose.offset(0.0f, -1.0f, -5.0f));
		head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(96, 0).addBox(-1.5f, -4.0f, -0.5f, 3.0f, 4.0f, 1.0f),
				PartPose.offsetAndRotation(-2.5f, -7.0f, -1.5f, 0.0f, 0.0f, -0.25f));
		head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(96, 0).mirror().addBox(-1.5f, -4.0f, -0.5f, 3.0f, 4.0f, 1.0f),
				PartPose.offsetAndRotation(2.5f, -7.0f, -1.5f, 0.0f, 0.0f, 0.25f));
		body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(96, 8).addBox(-2.0f, -2.0f, 0.0f, 4.0f, 4.0f, 8.0f),
				PartPose.offsetAndRotation(0.0f, -1.5f, 2.5f, -1.1f, 0.0f, 0.0f));
		root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(0, 33).addBox(-2.0f, -2.0f, -2.0f, 4.0f, 19.0f, 4.0f),
				PartPose.offsetAndRotation(-8.5f, -4.0f, -5.0f, -0.15f, 0.0f, 0.12f));
		root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(0, 33).mirror().addBox(-2.0f, -2.0f, -2.0f, 4.0f, 19.0f, 4.0f),
				PartPose.offsetAndRotation(8.5f, -4.0f, -5.0f, -0.15f, 0.0f, -0.12f));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(16, 33).addBox(-2.5f, 0.0f, -2.5f, 5.0f, 16.0f, 5.0f),
				PartPose.offset(-3.0f, 8.0f, 2.0f));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(16, 33).mirror().addBox(-2.5f, 0.0f, -2.5f, 5.0f, 16.0f, 5.0f),
				PartPose.offset(3.0f, 8.0f, 2.0f));
		return LayerDefinition.create(mesh, 128, 64);
	}

	@Override
	public void setupAnim(WerewolfRenderState state) {
		super.setupAnim(state);
		float age = state.ageInTicks;
		float walk = state.walkAnimationPos;
		float speed = state.walkAnimationSpeed;
		float swing = Mth.cos(walk * 0.6662f) * 1.2f * speed;

		head.yRot = Mth.clamp(state.yRot, -60.0f, 60.0f) * Mth.DEG_TO_RAD;
		head.xRot += state.xRot * Mth.DEG_TO_RAD * 0.6f;

		// Walking: legs stride, arms swing against them, the body bobs.
		rightLeg.xRot = swing;
		leftLeg.xRot = -swing;
		rightArm.xRot -= swing * 0.8f;
		leftArm.xRot += swing * 0.8f;
		body.y += Math.abs(Mth.cos(walk * 0.6662f)) * speed;
		if (state.aggressive) {
			rightArm.xRot -= 0.5f;
			leftArm.xRot -= 0.5f;
			rightEar.xRot -= 0.35f;
			leftEar.xRot -= 0.35f;
		}

		// The tail sways, and lifts as it runs.
		tail.xRot += 0.1f * Mth.sin(age * 0.1f) + 0.4f * speed;
		tail.yRot += 0.15f * Mth.sin(age * 0.07f);

		float attack = state.attackAnim;
		if (attack > 0.0f) {
			// A claw swipe: the arm hauled up and back, then raked down and across, with a snarl.
			float rake = Mth.sin(attack * Mth.PI);
			float raise = Mth.sin(Mth.sqrt(attack) * Mth.PI);
			ModelPart arm = state.leftSwipe ? leftArm : rightArm;
			float side = state.leftSwipe ? -1.0f : 1.0f;
			arm.xRot += -2.2f * raise + 0.8f * rake;
			arm.yRot += 0.5f * rake * side;
			arm.zRot += 0.4f * raise * side;
			jaw.xRot += 0.35f * rake;
		}

		float howl = state.howl;
		if (howl > 0.0f) {
			// Rearing back, head thrown up, jaw wide, arms out.
			body.xRot -= 0.25f * howl;
			chest.xRot -= 0.2f * howl;
			head.xRot -= 0.6f * howl;
			head.yRot *= 1.0f - howl;
			jaw.xRot += 0.55f * howl;
			rightArm.xRot += 0.35f * howl;
			leftArm.xRot += 0.35f * howl;
			rightArm.zRot += 0.35f * howl;
			leftArm.zRot -= 0.35f * howl;
			rightEar.xRot -= 0.4f * howl;
			leftEar.xRot -= 0.4f * howl;
		}
	}

	/** Which arm a swipe uses: they alternate, by when the swipe began. */
	static boolean leftSwipe(float ageInTicks, float attackAnim) {
		int start = Mth.floor((ageInTicks - attackAnim * SWING_TICKS) / SWIPE_ALTERNATION);
		return (start & 1) == 1;
	}
}
