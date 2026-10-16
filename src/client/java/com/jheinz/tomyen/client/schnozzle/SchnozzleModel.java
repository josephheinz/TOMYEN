package com.jheinz.tomyen.client.schnozzle;

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
 * A giant nose, built at half size (the renderer doubles it): stacked boxes stepping forward down the bridge to a
 * rosy bulb of a tip, flaring wings either side, two nostrils underneath with coarse hairs poking out, and a ragged
 * stump of raw flesh on top. Twelve six-segment tentacles hang from the underside and flanks.
 *
 * <p>Each tentacle segment sways on two sine waves of its own, phase-shifted per tentacle and per segment so they
 * writhe and curl independently, more wildly toward the tips and faster while it's hunting. The roots aim each
 * tentacle: hanging and splayed a little outward at rest, streaming behind it when it moves fast, and straightening
 * out toward its target when that's close or it lashes. The nostrils and wings flare and pulse before it fires, it
 * rears back before a sneeze and jolts forward when it goes off, and dying it deflates while its tentacles go limp.
 */
public class SchnozzleModel extends EntityModel<SchnozzleRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(MyMod.id("schnozzle"), "main");

	/** Where each tentacle hangs from, relative to the middle of the nose's underside: eight underneath, four up the flanks. */
	private static final float[][] ANCHORS = {
			{-5.0f, -0.5f, -5.5f}, {5.0f, -0.5f, -5.5f}, {-6.5f, -0.5f, -1.0f}, {6.5f, -0.5f, -1.0f},
			{-5.0f, -0.5f, 3.5f}, {5.0f, -0.5f, 3.5f}, {-2.0f, -0.5f, 4.5f}, {2.0f, -0.5f, 4.5f},
			{-7.0f, -5.0f, 4.0f}, {7.0f, -5.0f, 4.0f}, {-5.5f, -13.0f, 3.5f}, {5.5f, -13.0f, 3.5f}};
	/** Width and length of each segment, root to tip (texture slots in the same order). */
	private static final float[][] SEGMENTS = {{3.0f, 4.5f}, {2.6f, 4.5f}, {2.2f, 4.0f}, {1.8f, 4.0f}, {1.4f, 3.5f}, {1.0f, 3.5f}};
	/** Hairs per nostril: x, z within the nostril, and how they lean. */
	private static final float[][] HAIRS = {{-1.0f, -2.0f, 0.3f}, {0.6f, 0.0f, -0.2f}, {-0.4f, 2.0f, 0.1f}};
	/** How far a moving nose's tentacles stream behind, per block a tick. */
	private static final float TRAIL = 3.0f;

	private final ModelPart body;
	private final ModelPart nose;
	private final ModelPart leftWing;
	private final ModelPart rightWing;
	private final ModelPart[] nostrils = new ModelPart[2];
	private final ModelPart[][] hairs = new ModelPart[2][HAIRS.length];
	private final ModelPart[][] tentacles = new ModelPart[ANCHORS.length][SEGMENTS.length];

	public SchnozzleModel(ModelPart root) {
		super(root);
		body = root.getChild("body");
		nose = body.getChild("nose");
		leftWing = nose.getChild("left_wing");
		rightWing = nose.getChild("right_wing");
		for (int side = 0; side < 2; side++) {
			nostrils[side] = nose.getChild("nostril_" + side);
			for (int i = 0; i < HAIRS.length; i++) {
				hairs[side][i] = nostrils[side].getChild("hair_" + i);
			}
		}
		for (int t = 0; t < ANCHORS.length; t++) {
			ModelPart part = body.getChild("tentacle_" + t);
			for (int s = 0; s < SEGMENTS.length; s++) {
				tentacles[t][s] = part;
				if (s + 1 < SEGMENTS.length) {
					part = part.getChild("segment");
				}
			}
		}
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		// Everything hangs off the middle of the nose's underside, which sits at the bottom of the hitbox.
		PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0.0f, 24.0f, 0.0f));
		PartDefinition nose = body.addOrReplaceChild("nose", CubeListBuilder.create()
				// Base (with the nostril holes in its underside), then up the bridge, each step set further back.
				.texOffs(0, 0).addBox(-7.0f, -3.0f, -7.0f, 14.0f, 3.0f, 12.0f)
				.texOffs(0, 15).addBox(-6.0f, -10.0f, -8.0f, 12.0f, 7.0f, 13.0f)
				.texOffs(0, 35).addBox(-5.0f, -17.0f, -6.0f, 10.0f, 7.0f, 11.0f)
				.texOffs(0, 53).addBox(-4.0f, -24.0f, -4.0f, 8.0f, 7.0f, 9.0f)
				// Ridges down the front of the bridge, softening the steps.
				.texOffs(56, 26).addBox(-3.5f, -17.0f, -7.0f, 7.0f, 7.0f, 1.0f)
				.texOffs(74, 26).addBox(-3.0f, -24.0f, -5.0f, 6.0f, 7.0f, 1.0f)
				// The bulb of the tip.
				.texOffs(56, 0).addBox(-5.5f, -9.5f, -11.0f, 11.0f, 7.0f, 3.0f)
				.texOffs(56, 10).addBox(-4.0f, -8.5f, -12.0f, 8.0f, 5.0f, 1.0f)
				// The stump where it was torn off, with lumps of raw flesh on top.
				.texOffs(56, 16).addBox(-4.5f, -26.0f, -3.5f, 9.0f, 2.0f, 8.0f)
				.texOffs(92, 16).addBox(-3.5f, -27.5f, -2.0f, 3.0f, 1.5f, 3.0f)
				.texOffs(92, 16).addBox(0.5f, -28.0f, 0.0f, 3.0f, 2.0f, 2.0f)
				.texOffs(104, 16).addBox(-1.0f, -27.0f, 2.0f, 2.0f, 1.0f, 2.0f)
				.texOffs(112, 16).addBox(1.5f, -27.0f, -3.0f, 2.0f, 1.0f, 2.0f),
				PartPose.ZERO);
		nose.addOrReplaceChild("right_wing", CubeListBuilder.create().texOffs(0, 69).addBox(-3.0f, 0.0f, -5.5f, 3.0f, 9.0f, 10.0f),
				PartPose.offset(-6.0f, -9.5f, -2.0f));
		nose.addOrReplaceChild("left_wing", CubeListBuilder.create().texOffs(0, 69).mirror().addBox(0.0f, 0.0f, -5.5f, 3.0f, 9.0f, 10.0f),
				PartPose.offset(6.0f, -9.5f, -2.0f));
		for (int side = 0; side < 2; side++) {
			PartDefinition nostril = nose.addOrReplaceChild("nostril_" + side, CubeListBuilder.create()
					.texOffs(30, 69).addBox(-2.0f, 0.0f, -3.5f, 4.0f, 0.5f, 7.0f),
					PartPose.offset(side == 0 ? -3.5f : 3.5f, 0.0f, -2.0f));
			for (int i = 0; i < HAIRS.length; i++) {
				nostril.addOrReplaceChild("hair_" + i, CubeListBuilder.create().texOffs(56, 69).addBox(-0.35f, 0.0f, -0.35f, 0.7f, 4.0f, 0.7f),
						PartPose.offsetAndRotation(HAIRS[i][0] * (side == 0 ? 1.0f : -1.0f), 0.3f, HAIRS[i][1], 0.0f, 0.0f, HAIRS[i][2]));
			}
		}
		for (int t = 0; t < ANCHORS.length; t++) {
			PartDefinition parent = body;
			String name = "tentacle_" + t;
			PartPose pose = PartPose.offset(ANCHORS[t][0], ANCHORS[t][1], ANCHORS[t][2]);
			for (int s = 0; s < SEGMENTS.length; s++) {
				float width = SEGMENTS[s][0];
				float length = SEGMENTS[s][1];
				parent = parent.addOrReplaceChild(name, CubeListBuilder.create().texOffs(s * 16, 96)
						.addBox(-width / 2.0f, 0.0f, -width / 2.0f, width, length, width), pose);
				name = "segment";
				// The next one starts just inside the end of this one, so the joints don't gape as they bend.
				pose = PartPose.offset(0.0f, length - 0.4f, 0.0f);
			}
		}
		return LayerDefinition.create(mesh, 128, 128);
	}

	@Override
	public void setupAnim(SchnozzleRenderState state) {
		super.setupAnim(state);
		float age = state.ageInTicks;
		float dying = state.deathProgress;
		float alive = 1.0f - dying;
		float flare = state.flare;
		float pulse = flare * (0.75f + 0.25f * Mth.sin(age * 1.3f));

		// A slow bob and breathing swell; a recoil when it sneezes.
		body.y += Mth.sin(age * 0.1f) * 1.0f * alive;
		body.z += 3.0f * state.sneezeJolt;
		float breath = 1.0f + 0.02f * Mth.sin(age * 0.15f) * alive;

		// Nostrils toward its target, rearing back further to aim and before a sneeze, snapping forward when it goes.
		nose.xRot = -0.1f - 0.25f * flare - (state.sneezing ? 0.35f * flare : 0.0f) + 0.5f * state.sneezeJolt;
		nose.zRot = Mth.sin(age * 0.05f) * 0.03f * alive;
		// Deflates as it dies: squashed down, sagging out.
		nose.yScale = breath * (1.0f - 0.6f * dying);
		nose.xScale = breath * (1.0f + 0.25f * dying);
		nose.zScale = breath * (1.0f + 0.25f * dying);

		float wingFlare = 0.05f + 0.35f * pulse + 0.04f * Mth.sin(age * 0.15f) * alive - 0.2f * dying;
		rightWing.zRot = wingFlare;
		leftWing.zRot = -wingFlare;
		rightWing.yRot = -0.15f * pulse;
		leftWing.yRot = 0.15f * pulse;

		for (int side = 0; side < 2; side++) {
			nostrils[side].xScale = 1.0f + 0.45f * pulse;
			nostrils[side].zScale = 1.0f + 0.35f * pulse;
			for (int i = 0; i < HAIRS.length; i++) {
				ModelPart hair = hairs[side][i];
				float phase = side * 2.1f + i * 1.7f;
				// Sniffing in sucks the hairs up and in; otherwise they sway in its breath.
				hair.xRot = 0.15f * Mth.sin(age * 0.2f + phase) + (state.firing ? 0.6f * flare : -0.4f * flare);
				hair.zRot += 0.12f * Mth.cos(age * 0.17f + phase);
			}
		}

		animateTentacles(state, age, alive);
	}

	private void animateTentacles(SchnozzleRenderState state, float age, float alive) {
		float time = age * (state.aggressive ? 1.6f : 1.0f);
		float lash = Mth.sin(state.attackAnim * Mth.PI);
		float reach = Math.max(state.reach, lash) * alive;
		for (int t = 0; t < ANCHORS.length; t++) {
			float phase = t * 1.7f + (t % 3) * 0.9f;
			// Rest direction: down, splayed out from where it hangs (the flank ones more, and splaying flat when dead).
			float outX = ANCHORS[t][0];
			float outZ = ANCHORS[t][2];
			float out = Mth.sqrt(outX * outX + outZ * outZ);
			float splay = (t >= 8 ? 0.6f : 0.3f) + 1.2f * state.deathProgress;
			float dirX = out > 0.0f ? outX / out * splay : 0.0f;
			float dirY = 1.0f;
			float dirZ = out > 0.0f ? outZ / out * splay : 0.0f;
			// Streaming behind when it moves.
			dirX -= state.velocityX * TRAIL * alive;
			dirY -= state.velocityY * TRAIL * alive;
			dirZ -= state.velocityZ * TRAIL * alive;
			float length = Mth.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
			dirX /= length;
			dirY /= length;
			dirZ /= length;
			// Toward the target.
			dirX = Mth.lerp(reach, dirX, state.reachX);
			dirY = Mth.lerp(reach, dirY, state.reachY);
			dirZ = Mth.lerp(reach, dirZ, state.reachZ);
			length = Math.max(Mth.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ), 1.0e-4f);

			// Waves die down as it reaches (the tentacle straightens) and go slack when it dies.
			float amplitude = (1.0f - 0.65f * reach) * alive;
			for (int s = 0; s < SEGMENTS.length; s++) {
				ModelPart segment = tentacles[t][s];
				float curl = (0.12f + 0.09f * s) * amplitude;
				float swayX = Mth.sin(time * 0.11f + phase + s * 0.7f) * 0.8f + Mth.sin(time * 0.23f + phase * 1.3f + s * 1.1f) * 0.4f;
				float swayZ = Mth.cos(time * 0.09f + phase * 0.7f + s * 0.6f) * 0.8f + Mth.sin(time * 0.19f + phase * 2.1f + s * 0.9f) * 0.4f;
				segment.xRot = curl * swayX;
				segment.zRot = curl * swayZ;
				if (s == 0) {
					// The flank ones ride down with the nose as it deflates.
					segment.y = ANCHORS[t][1] * nose.yScale;
					// Points the root along the wanted direction: Z then X rotation takes straight down onto it.
					segment.xRot += (float) Math.asin(Mth.clamp(dirZ / length, -1.0f, 1.0f));
					segment.zRot += (float) Mth.atan2(-dirX / length, dirY / length);
				} else {
					// Whip-crack: the segments curl over and snap straight as it lashes.
					segment.xRot -= 0.35f * lash * Mth.sin(state.attackAnim * Mth.TWO_PI + s * 0.5f) * alive;
					// Limp: slack, sagging a little more joint by joint.
					segment.xRot += 0.08f * state.deathProgress * s;
				}
			}
		}
	}
}
