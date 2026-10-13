package com.jheinz.mymod.client.cathode;

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
 * A wood-veneer CRT television on a pair of bare human legs, in ten cubes (texture and UVs from
 * tools/gen_cathode_walker.py, which keeps a copy of this layout for its previews): the cabinet, 16 x 12 x 11 with its
 * screen bulging a pixel out of the front, two rabbit-ear antennas on top, and under it two legs, each a thigh (in
 * rolled-up trousers), a shin and a bare foot, hinged at the hip, knee and ankle.
 *
 * <p>Walking, it struts: each knee comes up high (the thigh swinging well forward, the shin hanging back under it),
 * the foot kept roughly level, the cabinet bobbing up as the leg passes and rocking and twisting with each step; it
 * leans into a sprint when it's hunting. Stood still, it breathes and tilts its cabinet this way and that, like a head
 * cocked at you. The cabinet turns and tips to look at whatever it's looking at. A kick chambers the right knee and
 * snaps the leg out straight while the cabinet rocks back. Charging a Static Burst, it shudders and its antennas
 * quiver. Dying, its legs buckle and its antennas droop.
 */
public class CathodeWalkerModel extends EntityModel<CathodeWalkerRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(MyMod.id("cathode_walker"), "main");
	private static final float ANTENNA_TILT = -0.2f;
	private static final float ANTENNA_SPLAY = 0.45f;
	/** Walk cycle radians per unit of walk animation. */
	private static final float STRIDE = 0.55f;

	private final ModelPart hips;
	private final ModelPart cabinet;
	private final ModelPart[] antennas = new ModelPart[2];
	private final ModelPart[] thighs = new ModelPart[2];
	private final ModelPart[] shins = new ModelPart[2];
	private final ModelPart[] feet = new ModelPart[2];

	public CathodeWalkerModel(ModelPart root) {
		super(root);
		hips = root.getChild("hips");
		cabinet = hips.getChild("cabinet");
		for (int i = 0; i < 2; i++) {
			antennas[i] = cabinet.getChild("antenna_" + i);
			thighs[i] = hips.getChild("leg_" + i);
			shins[i] = thighs[i].getChild("shin");
			feet[i] = shins[i].getChild("foot");
		}
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		// Hips 15 pixels up: legs of 7 + 6 + 2 reach the ground, the cabinet sits on top of them.
		PartDefinition hips = mesh.getRoot().addOrReplaceChild("hips", CubeListBuilder.create(), PartPose.offset(0.0f, 9.0f, 0.0f));
		PartDefinition cabinet = hips.addOrReplaceChild("cabinet", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-8.0f, -12.0f, -5.5f, 16.0f, 12.0f, 11.0f)
				// The screen, bulging out of the front on the left; the knobs are painted on the right.
				.texOffs(0, 23).addBox(-7.0f, -11.0f, -6.5f, 11.0f, 9.0f, 1.0f),
				PartPose.ZERO);
		for (int i = 0; i < 2; i++) {
			float side = i == 0 ? -1.0f : 1.0f;
			cabinet.addOrReplaceChild("antenna_" + i, CubeListBuilder.create().texOffs(24, 23).addBox(-0.5f, -9.0f, -0.5f, 1.0f, 9.0f, 1.0f),
					PartPose.offsetAndRotation(side * 2.0f, -12.0f, 2.5f, ANTENNA_TILT, 0.0f, side * ANTENNA_SPLAY));
			PartDefinition thigh = hips.addOrReplaceChild("leg_" + i, CubeListBuilder.create().texOffs(0, 33)
					.addBox(-2.0f, 0.0f, -2.0f, 4.0f, 7.0f, 4.0f), PartPose.offset(side * 3.5f, 0.0f, 0.0f));
			PartDefinition shin = thigh.addOrReplaceChild("shin", CubeListBuilder.create().texOffs(16, 33)
					.addBox(-2.0f, 0.0f, -2.0f, 4.0f, 6.0f, 4.0f), PartPose.offset(0.0f, 7.0f, 0.0f));
			shin.addOrReplaceChild("foot", CubeListBuilder.create().texOffs(32, 33)
					.addBox(-2.0f, 0.0f, -4.0f, 4.0f, 2.0f, 6.0f), PartPose.offset(0.0f, 6.0f, 0.0f));
		}
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(CathodeWalkerRenderState state) {
		super.setupAnim(state);
		float age = state.ageInTicks;
		float seed = state.seed;
		float dying = Math.min(1.0f, state.deathTime / 20.0f);
		float alive = 1.0f - dying;
		float amount = Math.min(1.0f, state.walkAnimationSpeed * 1.2f) * alive;
		float idle = (1.0f - amount) * alive;
		float phase = state.walkAnimationPos * STRIDE;
		float step = Mth.sin(phase);

		for (int i = 0; i < 2; i++) {
			float c = Mth.sin(phase + i * Mth.PI);
			// High knees: well forward on the lift, only a little back on the push.
			float thigh = amount * (c > 0.0f ? -1.3f * c : -0.45f * c);
			float shin = amount * (1.55f * (float) Math.pow(Math.max(0.0f, c), 0.8) + 0.15f);
			// Breathing, stood still: a slight give at the knees.
			shin += idle * 0.06f * (1.0f + Mth.sin(age * 0.1f + seed));
			thigh -= idle * 0.03f * (1.0f + Mth.sin(age * 0.1f + seed));
			if (i == 0 && state.attackAnim > 0.0f) {
				float[] kick = kick(state.attackAnim);
				thigh = kick[0];
				shin = kick[1];
			}
			// Dead, the knees buckle.
			thigh = Mth.lerp(dying, thigh, -0.5f);
			shin = Mth.lerp(dying, shin, 1.0f);
			thighs[i].xRot = thigh;
			shins[i].xRot = shin;
			// The foot kept roughly level with the ground, toes pointing a little on the lift.
			feet[i].xRot = -(thigh + shin) * 0.85f;
		}

		// The cabinet bobs up as each leg passes under it, and breathes stood still.
		hips.y += -0.9f * amount * Math.abs(step) + idle * 0.25f * Mth.sin(age * 0.1f + seed);

		// Looking where it looks; rocking and twisting with its strut, leaning into a sprint; stood still, it cocks
		// the cabinet one way and the other like a head tilted at you.
		float lean = (state.hunting ? 0.16f : 0.06f) * amount;
		cabinet.yRot = Mth.clamp(state.yRot, -70.0f, 70.0f) * Mth.DEG_TO_RAD + 0.15f * amount * step;
		cabinet.xRot = state.xRot * Mth.DEG_TO_RAD * 0.5f + lean + idle * 0.04f * Mth.sin(age * 0.037f + seed * 1.7f);
		cabinet.zRot = 0.1f * amount * step + idle * 0.13f * Mth.sin(age * 0.045f + seed);
		if (state.attackAnim > 0.0f) {
			float a = state.attackAnim;
			float strike = Mth.sin(Math.min(1.0f, a / 0.55f) * Mth.HALF_PI) * (a < 0.55f ? 1.0f : 1.0f - (a - 0.55f) / 0.45f);
			cabinet.xRot -= 0.3f * strike;
		}
		// Charging a burst: it shudders and its antennas quiver.
		float shudder = state.charge * alive;
		cabinet.xRot += 0.03f * shudder * Mth.sin(age * 2.7f);
		cabinet.zRot += 0.04f * shudder * Mth.sin(age * 3.3f);

		for (int i = 0; i < 2; i++) {
			float side = i == 0 ? -1.0f : 1.0f;
			float sway = 0.07f * Mth.sin(age * 0.2f + i * 1.3f + seed) + 0.12f * amount * step + 0.15f * shudder * Mth.sin(age * 4.1f + i);
			antennas[i].zRot += side * sway + side * 0.6f * dying;
			antennas[i].xRot += 0.05f * amount * Math.abs(step) + 0.3f * dying;
		}
	}

	/**
	 * The kicking thigh's and shin's angles at a point through the kick: the knee chambers up, the leg snaps out
	 * straight, then comes back down.
	 */
	private static float[] kick(float a) {
		if (a < 0.3f) {
			float t = a / 0.3f;
			return new float[] {-1.25f * t, 1.7f * t};
		}
		if (a < 0.55f) {
			float t = (a - 0.3f) / 0.25f;
			return new float[] {-1.25f - 0.4f * t, 1.7f * (1.0f - t)};
		}
		float t = (a - 0.55f) / 0.45f;
		return new float[] {-1.65f * (1.0f - t), 0.0f};
	}
}
