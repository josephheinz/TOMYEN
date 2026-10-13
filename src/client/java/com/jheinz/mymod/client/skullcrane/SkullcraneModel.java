package com.jheinz.mymod.client.skullcrane;

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
 * A skull about two-thirds of a block across: one cube of cranium with the face painted on, glowing red lenses set
 * into its sockets, a narrower jaw hinged underneath and a small antigrav emitter behind it. Four servo arms hang from
 * its underside, each three tapering segments ending in a two-pronged pincer.
 *
 * <p>The arms writhe like a squid's, on two sine waves phase-shifted per arm and per joint. Hunting, they flare out and
 * reach forward and the pincers open wide; near the prey they straighten toward it. Clamped on, they reach down onto
 * the victim's shoulders and the pincers close, trembling with the strain. The jaw chatters as it attacks, the skull
 * bobs and sways as it hovers, and dying, its arms go limp and its jaw drops.
 *
 * <p>A pack leader's ({@link #LEADER_LAYER}) is the same with a bronze crest along its crown, a horn plate swept back
 * from each temple, and arms {@value #LEADER_ARMS} times as long; its renderer scales it all up.
 */
public class SkullcraneModel extends EntityModel<SkullcraneRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(MyMod.id("skullcrane"), "main");
	public static final ModelLayerLocation LEADER_LAYER = new ModelLayerLocation(MyMod.id("skullcrane_leader"), "main");
	/** How much longer a leader's arm segments are. */
	private static final float LEADER_ARMS = 1.3f;

	/** Where each arm hangs from, relative to the skull's middle: a pair behind the jaw, a pair further back. */
	private static final float[][] ARMS = {{-3.5f, 2.0f, 1.0f}, {3.5f, 2.0f, 1.0f}, {-3.0f, 2.0f, 4.0f}, {3.0f, 2.0f, 4.0f}};
	/** The arms splay out from a point between the two pairs. */
	private static final float ARM_CENTRE_Z = 2.5f;
	/** Each arm segment's thickness and length, root to tip (texture slots in the same order). */
	private static final float[][] SEGMENTS = {{1.5f, 4.5f}, {1.25f, 4.0f}, {1.0f, 3.5f}};
	/** How far each segment overlaps the one before it, so the joints don't gap as they bend. */
	private static final float OVERLAP = 0.3f;
	/** How far a moving skull's arms stream behind, per block a tick. */
	private static final float TRAIL = 2.5f;

	private final ModelPart body;
	private final ModelPart skull;
	private final ModelPart jaw;
	private final ModelPart[][] arms = new ModelPart[ARMS.length][SEGMENTS.length];
	private final ModelPart[][] prongs = new ModelPart[ARMS.length][2];

	public SkullcraneModel(ModelPart root) {
		super(root);
		body = root.getChild("body");
		skull = body.getChild("skull");
		jaw = skull.getChild("jaw");
		for (int a = 0; a < ARMS.length; a++) {
			ModelPart part = skull.getChild("arm_" + a);
			for (int s = 0; s < SEGMENTS.length; s++) {
				arms[a][s] = part;
				if (s + 1 < SEGMENTS.length) {
					part = part.getChild("segment");
				}
			}
			for (int p = 0; p < 2; p++) {
				prongs[a][p] = part.getChild("prong_" + p);
			}
		}
	}

	public static LayerDefinition createBodyLayer() {
		return createLayer(false);
	}

	public static LayerDefinition createLeaderLayer() {
		return createLayer(true);
	}

	private static LayerDefinition createLayer(boolean leader) {
		MeshDefinition mesh = new MeshDefinition();
		// The skull sits at the bottom of the hitbox, its arms hanging below it.
		PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0.0f, 24.0f, 0.0f));
		PartDefinition skull = body.addOrReplaceChild("skull", CubeListBuilder.create()
				// The cranium, its face painted on.
				.texOffs(0, 0).addBox(-5.0f, -6.0f, -5.0f, 10.0f, 8.0f, 10.0f)
				// The lenses, standing a little proud of their sockets.
				.texOffs(40, 0).addBox(-3.0f, -2.0f, -5.25f, 2.0f, 2.0f, 1.0f)
				.texOffs(40, 0).addBox(1.0f, -2.0f, -5.25f, 2.0f, 2.0f, 1.0f)
				// The antigrav emitter underneath, behind the jaw.
				.texOffs(40, 4).addBox(-1.5f, 2.0f, 1.5f, 3.0f, 1.0f, 3.0f),
				PartPose.offset(0.0f, -5.0f, 0.0f));
		skull.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(0, 18).addBox(-4.0f, 0.0f, -5.0f, 8.0f, 2.0f, 5.0f),
				PartPose.offset(0.0f, 2.0f, 0.0f));
		if (leader) {
			// A crest along the crown, and a horn plate swept up and back from each temple.
			skull.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(26, 18).addBox(-1.0f, -2.0f, -4.0f, 2.0f, 2.0f, 8.0f),
					PartPose.offset(0.0f, -6.0f, 0.0f));
			for (int side = 0; side < 2; side++) {
				float sign = side == 0 ? -1.0f : 1.0f;
				skull.addOrReplaceChild("horn_" + side, CubeListBuilder.create().texOffs(46, 18).addBox(-0.5f, -4.0f, -1.5f, 1.0f, 4.0f, 3.0f),
						PartPose.offsetAndRotation(sign * 5.0f, -4.0f, 0.5f, -0.45f, 0.0f, sign * 0.35f));
			}
		}
		float armLength = leader ? LEADER_ARMS : 1.0f;
		int[][] slots = {{40, 10}, {48, 10}, {56, 10}};
		for (int a = 0; a < ARMS.length; a++) {
			PartDefinition parent = skull;
			String name = "arm_" + a;
			PartPose pose = PartPose.offset(ARMS[a][0], ARMS[a][1], ARMS[a][2]);
			for (int s = 0; s < SEGMENTS.length; s++) {
				float thick = SEGMENTS[s][0];
				float length = SEGMENTS[s][1] * armLength;
				parent = parent.addOrReplaceChild(name, CubeListBuilder.create()
						.texOffs(slots[s][0], slots[s][1]).addBox(-thick / 2.0f, 0.0f, -thick / 2.0f, thick, length, thick), pose);
				name = "segment";
				pose = PartPose.offset(0.0f, length - OVERLAP, 0.0f);
			}
			for (int p = 0; p < 2; p++) {
				parent.addOrReplaceChild("prong_" + p, CubeListBuilder.create().texOffs(60, 10).addBox(-0.25f, 0.0f, -0.25f, 0.5f, 2.5f, 0.5f),
						PartPose.offset(p == 0 ? -0.25f : 0.25f, SEGMENTS[SEGMENTS.length - 1][1] * armLength - OVERLAP, 0.0f));
			}
		}
		return LayerDefinition.create(mesh, 64, 32);
	}

	@Override
	public void setupAnim(SkullcraneRenderState state) {
		super.setupAnim(state);
		float age = state.ageInTicks;
		float seed = state.seed;
		float dying = state.deathProgress;
		float alive = 1.0f - dying;
		float steady = (1.0f - 0.7f * state.clamp) * alive;

		// Hovering: a slow bob and sway, steadier once it's got hold of something.
		body.y += Mth.sin(age * 0.15f + seed) * 0.6f * steady;
		skull.yRot = state.yRot * Mth.DEG_TO_RAD + 0.06f * Mth.sin(age * 0.07f + seed) * steady;
		skull.xRot = state.xRot * Mth.DEG_TO_RAD * 0.5f + 0.05f * Mth.sin(age * 0.11f + seed * 1.3f) * steady;
		skull.zRot = 0.06f * Mth.sin(age * 0.09f + seed * 0.7f) * steady + 0.6f * dying;

		// The jaw: shut at rest, snapping open and shut as it attacks, hanging open dead.
		jaw.xRot = 0.04f + state.chatter * (0.1f + 0.35f * Math.abs(Mth.sin(age * 0.9f + seed))) * alive + 0.55f * dying;

		animateArms(state, age, seed, alive);
	}

	private void animateArms(SkullcraneRenderState state, float age, float seed, float alive) {
		float time = age * (state.flare > 0.5f ? 1.8f : 1.0f);
		float jab = Mth.sin(state.attackAnim * Mth.PI);
		float reach = Math.max(state.reach, jab) * alive;
		float open = Mth.clamp(0.3f + 0.6f * state.flare - state.clamp, -0.3f, 1.0f);
		for (int a = 0; a < ARMS.length; a++) {
			float phase = seed + a * 1.9f + (a % 2) * 0.7f;
			// Rest direction: down and a little out from where it hangs; flared out and forward when hunting, sprawled
			// flat when dead.
			float outX = ARMS[a][0];
			float outZ = ARMS[a][2] - ARM_CENTRE_Z;
			float out = Mth.sqrt(outX * outX + outZ * outZ);
			float splay = 0.3f + 1.0f * state.flare + 1.4f * state.deathProgress;
			float dirX = outX / out * splay;
			float dirY = 1.0f;
			float dirZ = outZ / out * splay - 0.45f * state.flare;
			// Streaming behind as it flies.
			dirX -= state.velocityX * TRAIL * alive;
			dirY -= state.velocityY * TRAIL * alive;
			dirZ -= state.velocityZ * TRAIL * alive;
			float length = Mth.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
			dirX = Mth.lerp(reach, dirX / length, state.reachX);
			dirY = Mth.lerp(reach, dirY / length, state.reachY);
			dirZ = Mth.lerp(reach, dirZ / length, state.reachZ);
			length = Math.max(Mth.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ), 1.0e-4f);

			// The writhe dies down as it reaches, leaving a strained tremble when clamped on.
			float amplitude = ((1.0f - 0.8f * reach) * (1.0f + 0.4f * state.flare) + 0.15f * state.clamp) * alive;
			float tremble = state.clamp * 0.04f * Mth.sin(age * 3.1f + a * 2.0f);
			for (int s = 0; s < SEGMENTS.length; s++) {
				ModelPart segment = arms[a][s];
				float curl = (0.2f + 0.14f * s) * amplitude;
				float swayX = Mth.sin(time * 0.13f + phase + s * 0.8f) * 0.8f + Mth.sin(time * 0.29f + phase * 1.3f + s * 1.2f) * 0.35f;
				float swayZ = Mth.cos(time * 0.11f + phase * 0.7f + s * 0.7f) * 0.8f + Mth.sin(time * 0.23f + phase * 2.1f + s) * 0.35f;
				segment.xRot = curl * swayX + tremble;
				segment.zRot = curl * swayZ + tremble;
				if (s == 0) {
					// Points the root along the wanted direction: Z then X rotation takes straight down onto it.
					segment.xRot += (float) Math.asin(Mth.clamp(dirZ / length, -1.0f, 1.0f));
					segment.zRot += (float) Mth.atan2(-dirX / length, dirY / length);
				} else {
					// Bending in at the elbows to get a grip; limp and sagging dead.
					segment.xRot += 0.25f * state.clamp + 0.12f * state.deathProgress * s;
				}
			}
			// The pincer turns slowly about the arm, and opens wide hunting, snaps shut clamped on, and works a little
			// idly.
			arms[a][SEGMENTS.length - 1].yRot = 0.4f * Mth.sin(time * 0.07f + phase) * alive;
			float idle = 0.12f * Mth.sin(time * 0.17f + phase * 1.7f) * alive * (1.0f - state.clamp);
			float spread = Math.max(-0.05f, 0.15f + 0.5f * open + idle) + 0.3f * state.deathProgress;
			prongs[a][0].zRot = spread;
			prongs[a][1].zRot = -spread;
		}
	}
}
