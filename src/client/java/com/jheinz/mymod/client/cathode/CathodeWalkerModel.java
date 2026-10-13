package com.jheinz.mymod.client.cathode;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

import com.jheinz.mymod.MyMod;

/**
 * A wood-veneer CRT television on a pair of long, skinny bare human legs (texture and UVs from
 * tools/gen_cathode_walker.py, which keeps a copy of this layout for its previews and checks it for z-fighting): the
 * cabinet, 16 x 12 x 11 with its screen bulging a pixel out of the front, two rabbit-ear antennas on top, and under it
 * two legs, each hinged at the hip, knee and ankle. A thigh is baggy rolled-up jeans over a thigh 2.5 across; a shin
 * a knobbly knee, a shin 2 across and a calf behind it; a foot a heel, instep, outer sole and ball, with five toes, big
 * toe first, each its own part hinged at its root.
 *
 * <p>Walking, it struts: each knee comes up high (the thigh swinging well forward, the shin hanging back under it),
 * the foot kept roughly level, its heel peeling up and its toes bending back on the push-off, the toes clawing in the
 * air and splaying as the foot lands; the hips ride at whatever height keeps the lower foot exactly on the ground, so
 * the cabinet dips and rises with the stride. It leans into a sprint when it's hunting. Stood still, it breathes and
 * tilts its cabinet this way and that, like a head cocked at you. The cabinet turns and tips to look at whatever it's
 * looking at. A kick chambers the right knee and snaps the leg out straight, toes flexed back, while the cabinet rocks
 * back. Charging a Static Burst, it shudders and its antennas quiver. Dying, its legs buckle, its toes splay stiffly
 * and its antennas droop.
 */
public class CathodeWalkerModel extends EntityModel<CathodeWalkerRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(MyMod.id("cathode_walker"), "main");
	private static final float ANTENNA_TILT = -0.2f;
	private static final float ANTENNA_SPLAY = 0.45f;
	/** Walk cycle radians per unit of walk animation. */
	private static final float STRIDE = 0.55f;

	/** Model y of the ground, and of the hips at rest: legs of 8 + 7 + 2 reach it. */
	private static final float GROUND = 24.0f;
	private static final float HIP_Y = 7.0f;
	private static final float LEG_X = 3.5f;
	private static final float THIGH_LENGTH = 8.0f;
	private static final float SHIN_LENGTH = 7.0f;
	/** Ankle to sole, and the sole's back (heel) and front (ball) ends, in the foot's space. */
	private static final float SOLE = 2.0f;
	private static final float HEEL_BACK = 1.5f;
	private static final float BALL_FRONT = -5.3f;
	/** Where the toes hinge (the bottom of their roots), and the big toe's length. */
	private static final float TOE_Y = 1.9f;
	private static final float TOE_Z = -5.1f;
	private static final float BIG_TOE = 1.7f;
	/** Each toe's pivot across the foot, big toe first; + is toward the other foot. */
	private static final float[] TOE_X = {1.375f, 0.38f, -0.33f, -0.97f, -1.55f};

	/** The thigh: jeans (their top sunk into the cabinet), the rolled cuff round their hem, the bare thigh. */
	private static final Box[] THIGH = {
			new Box(0, 33, 3, 3, 3, -1.7f, -0.5f, -1.7f, 1.7f, 2.5f, 1.7f),
			new Box(12, 33, 3, 2, 3, -1.95f, 2.0f, -1.95f, 1.95f, 3.4f, 1.95f),
			new Box(24, 33, 2, 5, 2, -1.25f, 2.9f, -1.25f, 1.25f, 8.1f, 1.25f)};
	/** The shin: the knee, the shin, the calf. */
	private static final Box[] SHIN = {
			new Box(32, 33, 3, 3, 3, -1.35f, -1.3f, -1.5f, 1.35f, 1.3f, 1.15f),
			new Box(44, 33, 2, 6, 2, -1.0f, 1.0f, -1.0f, 1.0f, 7.3f, 1.0f),
			new Box(52, 33, 2, 3, 2, -1.15f, 1.6f, -0.6f, 1.15f, 4.4f, 1.45f)};
	/** The foot, + x toward the other foot: heel, instep (raised off the ground inside, the arch), outer sole, ball. */
	private static final Box[] FOOT = {
			new Box(0, 42, 2, 2, 3, -1.15f, -0.4f, -1.2f, 1.2f, 2.0f, 1.5f),
			new Box(10, 42, 3, 1, 3, -1.3f, 0.3f, -3.9f, 1.35f, 1.55f, -1.0f),
			new Box(22, 42, 1, 1, 3, -1.0f, 1.45f, -4.1f, -0.05f, 1.9f, -1.1f),
			new Box(30, 42, 3, 1, 2, -1.65f, 0.75f, -5.3f, 1.7f, 2.0f, -3.6f)};
	/** The toes about their pivots, big toe first, each smaller and shorter than the last. */
	private static final Box[] TOES = {
			new Box(40, 42, 1, 1, 2, -0.525f, -0.95f, -1.7f, 0.525f, 0.0f, 0.0f),
			new Box(46, 42, 1, 1, 2, -0.33f, -0.75f, -1.5f, 0.33f, 0.0f, 0.0f),
			new Box(52, 42, 1, 1, 2, -0.29f, -0.7f, -1.35f, 0.29f, 0.0f, 0.0f),
			new Box(58, 42, 1, 1, 2, -0.26f, -0.62f, -1.15f, 0.26f, 0.0f, 0.0f),
			new Box(0, 47, 1, 1, 2, -0.23f, -0.55f, -0.9f, 0.23f, 0.0f, 0.0f)};

	private final ModelPart hips;
	private final ModelPart cabinet;
	private final ModelPart[] antennas = new ModelPart[2];
	private final ModelPart[] thighs = new ModelPart[2];
	private final ModelPart[] shins = new ModelPart[2];
	private final ModelPart[] feet = new ModelPart[2];
	private final ModelPart[][] toes = new ModelPart[2][TOE_X.length];

	public CathodeWalkerModel(ModelPart root) {
		super(root);
		hips = root.getChild("hips");
		cabinet = hips.getChild("cabinet");
		for (int i = 0; i < 2; i++) {
			antennas[i] = cabinet.getChild("antenna_" + i);
			thighs[i] = hips.getChild("leg_" + i);
			shins[i] = thighs[i].getChild("shin");
			feet[i] = shins[i].getChild("foot");
			for (int j = 0; j < TOE_X.length; j++) {
				toes[i][j] = feet[i].getChild("toe_" + j);
			}
		}
	}

	/**
	 * A cube: where it sits on the texture and its size there, then its corners in pixels in its part's space. Where
	 * the two sizes differ the cube is grown or shrunk about its centre, so the textures stay whole texels.
	 */
	private record Box(int u, int v, int w, int h, int d, float x0, float y0, float z0, float x1, float y1, float z1) {
		/** Adds this cube, its x flipped if flip is negative (the right foot is the left mirrored). */
		CubeListBuilder addTo(CubeListBuilder builder, float flip) {
			float lo = flip < 0.0f ? -x1 : x0;
			float hi = flip < 0.0f ? -x0 : x1;
			float gx = (hi - lo - w) / 2.0f;
			float gy = (y1 - y0 - h) / 2.0f;
			float gz = (z1 - z0 - d) / 2.0f;
			return builder.texOffs(u, v).addBox(lo + gx, y0 + gy, z0 + gz, w, h, d, new CubeDeformation(gx, gy, gz));
		}
	}

	private static CubeListBuilder cubes(Box[] boxes, float flip) {
		CubeListBuilder builder = CubeListBuilder.create();
		for (Box box : boxes) {
			box.addTo(builder, flip);
		}
		return builder;
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition hips = mesh.getRoot().addOrReplaceChild("hips", CubeListBuilder.create(), PartPose.offset(0.0f, HIP_Y, 0.0f));
		PartDefinition cabinet = hips.addOrReplaceChild("cabinet", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-8.0f, -12.0f, -5.5f, 16.0f, 12.0f, 11.0f)
				// The screen, bulging out of the front on the left, its back sunk into the cabinet; the knobs are
				// painted on the right.
				.texOffs(0, 23).addBox(-7.0f, -11.0f, -6.4f, 11.0f, 9.0f, 1.0f, new CubeDeformation(0.0f, 0.0f, 0.1f)),
				PartPose.ZERO);
		for (int i = 0; i < 2; i++) {
			float side = i == 0 ? -1.0f : 1.0f;
			float flip = -side;
			cabinet.addOrReplaceChild("antenna_" + i, CubeListBuilder.create().texOffs(24, 23).addBox(-0.5f, -9.0f, -0.5f, 1.0f, 9.0f, 1.0f),
					PartPose.offsetAndRotation(side * 2.0f, -12.0f, 2.5f, ANTENNA_TILT, 0.0f, side * ANTENNA_SPLAY));
			PartDefinition thigh = hips.addOrReplaceChild("leg_" + i, cubes(THIGH, 1.0f), PartPose.offset(side * LEG_X, 0.0f, 0.0f));
			PartDefinition shin = thigh.addOrReplaceChild("shin", cubes(SHIN, 1.0f), PartPose.offset(0.0f, THIGH_LENGTH, 0.0f));
			PartDefinition foot = shin.addOrReplaceChild("foot", cubes(FOOT, flip), PartPose.offset(0.0f, SHIN_LENGTH, 0.0f));
			for (int j = 0; j < TOE_X.length; j++) {
				foot.addOrReplaceChild("toe_" + j, TOES[j].addTo(CubeListBuilder.create(), flip),
						PartPose.offset(flip * TOE_X[j], TOE_Y, TOE_Z));
			}
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
		float strike = state.attackAnim > 0.0f ? strike(state.attackAnim) : 0.0f;
		float lowest = 0.0f;

		for (int i = 0; i < 2; i++) {
			float p = phase + i * Mth.PI;
			float c = Mth.sin(p);
			float k = Mth.cos(p);
			// High knees: well forward on the lift, only a little back on the push.
			float thigh = amount * (c > 0.0f ? -1.3f * c : -0.45f * c);
			float shin = amount * (1.55f * (float) Math.pow(Math.max(0.0f, c), 0.8) + 0.15f);
			// Late in the stance the heel peels up and the toes bend back against the ground; just after it lands,
			// the toes splay; in the air they claw.
			float push = amount * 2.0f * Math.max(0.0f, -c) * Math.max(0.0f, k);
			float land = amount * 2.0f * Math.max(0.0f, -c) * Math.max(0.0f, -k);
			float curl = -0.6f * push + 0.3f * amount * Math.max(0.0f, c);
			// Breathing, stood still: a slight give at the knees.
			shin += idle * 0.06f * (1.0f + Mth.sin(age * 0.1f + seed));
			thigh -= idle * 0.03f * (1.0f + Mth.sin(age * 0.1f + seed));
			if (i == 0 && state.attackAnim > 0.0f) {
				float[] kick = kick(state.attackAnim);
				thigh = kick[0];
				shin = kick[1];
				push = 0.0f;
				land = 0.0f;
				curl = -0.7f * strike;
			}
			// Dead, the knees buckle and the toes splay stiffly.
			thigh = Mth.lerp(dying, thigh, -0.5f);
			shin = Mth.lerp(dying, shin, 1.0f);
			curl = Mth.lerp(dying, curl, -0.4f);
			land = Mth.lerp(dying, land, 1.0f);
			// The foot kept roughly level with the ground, toes pointing a little on the lift.
			float foot = -(thigh + shin) * 0.85f + 0.45f * push;
			thighs[i].xRot = thigh;
			shins[i].xRot = shin;
			feet[i].xRot = foot;
			float flip = i == 0 ? 1.0f : -1.0f;
			for (int j = 0; j < TOE_X.length; j++) {
				toes[i][j].xRot = curl;
				toes[i][j].yRot = -flip * (TOE_X[j] - 0.1f) * 0.22f * land;
			}
			lowest = Math.max(lowest, reach(thigh, shin, foot, curl));
		}

		// The hips at whatever height stands the lower foot on the ground.
		hips.y = GROUND - lowest;

		// Looking where it looks; rocking and twisting with its strut, leaning into a sprint; stood still, it cocks
		// the cabinet one way and the other like a head tilted at you.
		float lean = (state.hunting ? 0.16f : 0.06f) * amount;
		cabinet.yRot = Mth.clamp(state.yRot, -70.0f, 70.0f) * Mth.DEG_TO_RAD + 0.15f * amount * step;
		cabinet.xRot = state.xRot * Mth.DEG_TO_RAD * 0.5f + lean + idle * 0.04f * Mth.sin(age * 0.037f + seed * 1.7f);
		cabinet.zRot = 0.1f * amount * step + idle * 0.13f * Mth.sin(age * 0.045f + seed);
		cabinet.xRot -= 0.3f * strike;
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
	 * How far below the hip a posed leg reaches: the lowest of the back of its heel, the front of its ball and the tip
	 * of its big toe.
	 */
	private static float reach(float thigh, float shin, float foot, float curl) {
		float knee = thigh + shin;
		float sole = knee + foot;
		float toe = down(sole, TOE_Y, TOE_Z) + down(sole + curl, 0.0f, -BIG_TOE);
		float lowest = Math.max(Math.max(down(sole, SOLE, HEEL_BACK), down(sole, SOLE, BALL_FRONT)), toe);
		return THIGH_LENGTH * Mth.cos(thigh) + SHIN_LENGTH * Mth.cos(knee) + lowest;
	}

	/** The height (model y) of a point (y, z) of a part turned by angle about x. */
	private static float down(float angle, float y, float z) {
		return y * Mth.cos(angle) - z * Mth.sin(angle);
	}

	/** How far into its kick the cabinet has rocked back, 0 to 1. */
	private static float strike(float a) {
		return Mth.sin(Math.min(1.0f, a / 0.55f) * Mth.HALF_PI) * (a < 0.55f ? 1.0f : 1.0f - (a - 0.55f) / 0.45f);
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
