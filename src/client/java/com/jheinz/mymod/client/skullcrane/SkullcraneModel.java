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
 * A human skull about two-thirds of a block across: cranium and bulging back, a brow ridge over sockets set with
 * glowing red optic lenses, an upper jaw of teeth, and a mandible on a mechanical hinge with a piston either side.
 * Bolted plates on its crown and temple, an antigrav emitter underneath, and two cables trailing off the back. Four
 * servo arms hang from its underside, each three strut-and-servo segments with a piston alongside, ending in a
 * wrist and a three-fingered grabber claw.
 *
 * <p>The arms writhe like a squid's, on two sine waves phase-shifted per arm and per joint, with a servo's twitchiness
 * on top: little jerks to a new angle, held a few ticks, then another. Hunting, they flare out and reach forward and
 * the claws open wide; near the prey they straighten toward it. Clamped on, they reach down onto the victim's shoulders
 * and the claws close, trembling with the strain. The jaw chatters as it attacks. The whole skull jitters and twitches
 * like a drone, and dying, its arms go limp and its jaw drops.
 */
public class SkullcraneModel extends EntityModel<SkullcraneRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(MyMod.id("skullcrane"), "main");

	/** Where each arm hangs from, relative to the skull's middle: a pair beside the jaw, a pair further back. */
	private static final float[][] ARMS = {{-4.5f, 2.0f, -1.0f}, {4.5f, 2.0f, -1.0f}, {-3.5f, 2.0f, 3.5f}, {3.5f, 2.0f, 3.5f}};
	/** Each arm segment's servo housing size and strut length, root to wrist (texture slots in the same order). */
	private static final float[][] SEGMENTS = {{2.0f, 4.0f}, {1.8f, 3.5f}, {1.6f, 3.0f}};
	private static final int FINGERS = 3;
	/** How far a moving skull's arms stream behind, per block a tick. */
	private static final float TRAIL = 2.5f;

	private final ModelPart body;
	private final ModelPart skull;
	private final ModelPart jaw;
	private final ModelPart[] cables = new ModelPart[2];
	private final ModelPart[][] arms = new ModelPart[ARMS.length][SEGMENTS.length];
	private final ModelPart[] wrists = new ModelPart[ARMS.length];
	private final ModelPart[][] fingers = new ModelPart[ARMS.length][FINGERS];
	private final ModelPart[][] tips = new ModelPart[ARMS.length][FINGERS];

	public SkullcraneModel(ModelPart root) {
		super(root);
		body = root.getChild("body");
		skull = body.getChild("skull");
		jaw = skull.getChild("jaw");
		for (int i = 0; i < cables.length; i++) {
			cables[i] = skull.getChild("cable_" + i);
		}
		for (int a = 0; a < ARMS.length; a++) {
			ModelPart part = skull.getChild("arm_" + a);
			for (int s = 0; s < SEGMENTS.length; s++) {
				arms[a][s] = part;
				part = part.getChild(s + 1 < SEGMENTS.length ? "segment" : "wrist");
			}
			wrists[a] = part;
			for (int f = 0; f < FINGERS; f++) {
				fingers[a][f] = part.getChild("finger_" + f);
				tips[a][f] = fingers[a][f].getChild("tip");
			}
		}
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		// The skull sits at the bottom of the hitbox, its arms hanging below it.
		PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0.0f, 24.0f, 0.0f));
		PartDefinition skull = body.addOrReplaceChild("skull", CubeListBuilder.create()
				// Cranium, and the bulge of the back of the head.
				.texOffs(0, 0).addBox(-5.0f, -6.0f, -5.0f, 10.0f, 8.0f, 10.0f)
				.texOffs(40, 0).addBox(-4.0f, -5.0f, 5.0f, 8.0f, 6.0f, 1.0f)
				// Brow ridge over the sockets.
				.texOffs(40, 7).addBox(-5.0f, -3.0f, -6.0f, 10.0f, 1.5f, 1.0f)
				// Upper jaw and teeth.
				.texOffs(0, 18).addBox(-4.0f, 2.0f, -5.0f, 8.0f, 1.5f, 5.0f)
				// Optic lenses, set into the sockets.
				.texOffs(26, 18).addBox(-3.75f, -1.5f, -5.5f, 2.5f, 2.5f, 0.5f)
				.texOffs(26, 18).addBox(1.25f, -1.5f, -5.5f, 2.5f, 2.5f, 0.5f)
				// Plates bolted to the crown and the right temple.
				.texOffs(40, 10).addBox(-0.5f, -6.6f, -3.0f, 5.0f, 0.6f, 6.0f)
				.texOffs(34, 18).addBox(-5.6f, -4.0f, -2.5f, 0.6f, 4.0f, 5.0f),
				PartPose.offset(0.0f, -5.0f, 0.0f));
		skull.addOrReplaceChild("jaw", CubeListBuilder.create()
				.texOffs(0, 25).addBox(-3.5f, 0.0f, -4.5f, 7.0f, 1.5f, 5.0f)
				// The hinge pistons.
				.texOffs(24, 25).addBox(-4.3f, -1.2f, -0.8f, 0.8f, 2.2f, 0.8f)
				.texOffs(24, 25).addBox(3.5f, -1.2f, -0.8f, 0.8f, 2.2f, 0.8f),
				PartPose.offset(0.0f, 3.5f, -0.5f));
		skull.addOrReplaceChild("thruster", CubeListBuilder.create()
				.texOffs(46, 18).addBox(-2.0f, 0.0f, -2.0f, 4.0f, 1.5f, 4.0f)
				.texOffs(46, 24).addBox(-1.5f, 1.5f, -1.5f, 3.0f, 0.5f, 3.0f),
				PartPose.offset(0.0f, 2.0f, 2.5f));
		for (int i = 0; i < 2; i++) {
			skull.addOrReplaceChild("cable_" + i, CubeListBuilder.create().texOffs(58, 0).addBox(-0.4f, 0.0f, -0.4f, 0.8f, 3.5f, 0.8f),
					PartPose.offset(i == 0 ? -2.5f : 2.5f, 1.0f, 5.0f));
		}
		for (int a = 0; a < ARMS.length; a++) {
			PartDefinition parent = skull;
			String name = "arm_" + a;
			PartPose pose = PartPose.offset(ARMS[a][0], ARMS[a][1], ARMS[a][2]);
			for (int s = 0; s < SEGMENTS.length; s++) {
				float servo = SEGMENTS[s][0];
				float strut = SEGMENTS[s][1];
				parent = parent.addOrReplaceChild(name, CubeListBuilder.create()
						.texOffs(s * 8, 32).addBox(-servo / 2.0f, -servo / 2.0f, -servo / 2.0f, servo, servo, servo)
						.texOffs(s * 8, 36).addBox(-0.6f, servo / 2.0f - 0.2f, -0.6f, 1.2f, strut, 1.2f)
						.texOffs(24, 32).addBox(0.5f, servo / 2.0f, -0.3f, 0.6f, strut - 1.0f, 0.6f),
						pose);
				name = "segment";
				pose = PartPose.offset(0.0f, servo / 2.0f - 0.2f + strut, 0.0f);
			}
			PartDefinition wrist = parent.addOrReplaceChild("wrist", CubeListBuilder.create()
					.texOffs(28, 32).addBox(-1.0f, -0.5f, -1.0f, 2.0f, 1.5f, 2.0f), pose);
			for (int f = 0; f < FINGERS; f++) {
				float angle = f * Mth.TWO_PI / FINGERS;
				PartDefinition finger = wrist.addOrReplaceChild("finger_" + f, CubeListBuilder.create()
						.texOffs(36, 32).addBox(-0.35f, 0.0f, -0.35f, 0.7f, 1.6f, 0.7f),
						PartPose.offsetAndRotation(Mth.sin(angle) * 0.55f, 0.9f, Mth.cos(angle) * 0.55f, 0.0f, angle, 0.0f));
				finger.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(40, 32).addBox(-0.3f, 0.0f, -0.3f, 0.6f, 1.3f, 0.6f),
						PartPose.offset(0.0f, 1.5f, 0.0f));
			}
		}
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(SkullcraneRenderState state) {
		super.setupAnim(state);
		float age = state.ageInTicks;
		float seed = state.seed;
		float dying = state.deathProgress;
		float alive = 1.0f - dying;
		float steady = 1.0f - 0.7f * state.clamp;

		// Drone jitter: a quick bob, and servo twitches of the whole skull, steadier once it's got hold of something.
		body.y += (Mth.sin(age * 0.23f + seed) * 0.5f + twitch(age, seed, 9.0f) * 0.3f) * alive * steady;
		skull.yRot = state.yRot * Mth.DEG_TO_RAD + 0.08f * twitch(age, seed + 1.3f, 11.0f) * alive * steady;
		skull.xRot = state.xRot * Mth.DEG_TO_RAD * 0.5f + 0.06f * twitch(age, seed + 2.9f, 7.0f) * alive * steady;
		skull.zRot = 0.07f * twitch(age, seed + 4.1f, 13.0f) * alive * steady + 0.6f * dying;

		// The jaw: shut at rest, clacking open and shut in steps as it attacks, hanging open dead.
		float clack = Math.round(Math.abs(Mth.sin(age * 1.4f + seed)) * 3.0f) / 3.0f;
		jaw.xRot = 0.04f + state.chatter * (0.12f + 0.4f * clack) * alive + 0.55f * dying;

		for (int i = 0; i < cables.length; i++) {
			cables[i].xRot = 0.35f + 0.15f * Mth.sin(age * 0.1f + i * 1.7f) - state.velocityZ * TRAIL * 0.5f;
			cables[i].zRot = 0.1f * Mth.cos(age * 0.13f + i * 2.3f) + state.velocityX * TRAIL * 0.5f;
		}

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
			float outZ = ARMS[a][2] + 1.0f;
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
				// Servo jerks on top: a new angle every few ticks, snapped to and held.
				float jerk = (0.12f + 0.1f * state.flare) * alive * (1.0f - state.clamp);
				segment.xRot = curl * swayX + jerk * twitch(age, phase + s * 3.1f, 5.0f + s) + tremble;
				segment.zRot = curl * swayZ + jerk * twitch(age, phase + s * 4.7f + 11.0f, 6.0f + s) + tremble;
				if (s == 0) {
					// Points the root along the wanted direction: Z then X rotation takes straight down onto it.
					segment.xRot += (float) Math.asin(Mth.clamp(dirZ / length, -1.0f, 1.0f));
					segment.zRot += (float) Mth.atan2(-dirX / length, dirY / length);
				} else {
					// Bending in at the elbows to get a grip; limp and sagging dead.
					segment.xRot += 0.25f * state.clamp + 0.12f * state.deathProgress * s;
				}
			}
			// Claws: wide open hunting, snapped shut clamped on, opening and closing a little idly.
			float idle = 0.15f * twitch(age, phase + 7.0f, 8.0f) * alive * (1.0f - state.clamp);
			wrists[a].yRot = 0.4f * Mth.sin(time * 0.07f + phase) * alive;
			for (int f = 0; f < FINGERS; f++) {
				fingers[a][f].xRot = 0.15f + 0.65f * open + idle - 0.35f * state.clamp + 0.3f * state.deathProgress;
				tips[a][f].xRot = -0.45f - 0.7f * state.clamp + 0.2f * open;
			}
		}
	}

	/**
	 * A servo's jerky motion: a pseudo-random angle (-1 to 1) held for {@code period} ticks, snapping quickly to the
	 * next one at the start of each.
	 */
	private static float twitch(float age, float seed, float period) {
		float t = age / period + seed;
		int step = Mth.floor(t);
		float snap = Math.min(1.0f, (t - step) * 6.0f);
		return Mth.lerp(snap, noise(step - 1, seed), noise(step, seed));
	}

	private static float noise(int step, float seed) {
		int hash = step * 0x27d4eb2d ^ Float.floatToIntBits(seed) * 0x165667b1;
		hash ^= hash >>> 15;
		hash *= 0x2c1b3c6d;
		hash ^= hash >>> 12;
		return (hash & 0xffff) / 32767.5f - 1.0f;
	}
}
