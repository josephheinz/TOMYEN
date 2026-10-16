package com.jheinz.tomyen.client.alien;

import java.util.ArrayList;
import java.util.List;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.alien.Ufo;

/**
 * Draws a {@link Ufo} as a low-poly saucer turned on a lathe: {@value #SIDES} flat-shaded facets round, a dark belly
 * sloping up to a bright rim band, a pale upper hull with alternate panels a shade darker (so its slow spin shows), and
 * a glass dome with a little green pilot inside. Plain vertex colours on a white texture, smooth ramps from one edge of
 * each band to the other, no other detail. Eight lights round the rim chase through five colours, and the emitter
 * underneath pulses, brighter while it's beaming. Its legs swing out and down to the ground as it lands, and the ramp
 * swings down from its belly with warm light spilling down it. Hurt, it flashes red like any mob; going down, it lists
 * and spins.
 *
 * <p>Shapes are in blocks from its feet (the bottom of the emitter), with the hull's profile from the middle outward
 * and up in {@link #HULL} and {@link #DOME}. tools/gen_ufo.py previews the same mesh.
 */
public class UfoRenderer extends EntityRenderer<Ufo, UfoRenderState> {
	private static final Identifier WHITE = MyMod.id("textures/entity/ufo/ufo.png");
	private static final RenderType SOLID = RenderTypes.entityCutout(WHITE);
	private static final RenderType GLASS = RenderTypes.entityTranslucent(WHITE);
	private static final RenderType GLOW = RenderTypes.eyes(WHITE);
	static final int SIDES = 16;

	/** The hull's profile (radius, height) and the colours at each point; the emitter disc is the first span. */
	private static final float[][] HULL = {
			{0.0f, 0.0f}, {0.7f, 0.0f}, {0.85f, 0.1f}, {2.1f, 0.42f}, {2.75f, 0.62f}, {2.75f, 0.8f}, {2.15f, 1.02f}, {1.2f, 1.22f}};
	/** Colours at the bottom and top of each span of {@link #HULL} (after the emitter). */
	private static final int[][] HULL_COLOURS = {
			{0xFF2E333B, 0xFF2E333B}, {0xFF3E4550, 0xFF5C6573}, {0xFF6E7784, 0xFF8A93A0}, {0xFFD6DCE2, 0xFFD6DCE2},
			{0xFFB4BCC6, 0xFFC8CFD7}, {0xFFCED5DC, 0xFFE6EBF0}};
	/** Spans whose alternate panels are a shade darker. */
	private static final boolean[] PANELLED = {false, true, false, false, true, true};
	private static final float[][] DOME = {{1.2f, 1.22f}, {1.12f, 1.55f}, {0.88f, 1.83f}, {0.48f, 2.0f}, {0.0f, 2.04f}};
	private static final int[] DOME_COLOURS = {0x70609C9A, 0x7078C8C0, 0x8094E2D6, 0x98B8F2E8, 0xA8E4FFF8};
	private static final int[] LIGHT_COLOURS = {0xFFFF4A4A, 0xFFFFC83C, 0xFF48FF7A, 0xFF46C8FF, 0xFFC064FF};
	private static final int LIGHTS = 8;
	private static final float LIGHT_RADIUS = 2.8f;
	private static final float LIGHT_HEIGHT = 0.71f;
	private static final float LIGHT_SIZE = 0.11f;
	/** Where each leg meets the belly, and how far out its foot goes. */
	private static final float LEG_ROOT = 1.55f;
	private static final float LEG_SPREAD = 0.45f;
	private static final float LEG_TOP = 0.28f;
	/** The ramp's hinge, out from the middle along the way it faces, and its length and width. */
	private static final float RAMP_HINGE = 0.95f;
	private static final float RAMP_LENGTH = 2.0f;
	private static final float RAMP_WIDTH = 0.8f;
	private static final float RAMP_Y = 0.12f;

	private record Face(Vector3f[] corners, Vector3f normal, int[] colours) {
	}

	private static final List<Face> HULL_FACES = lathe(HULL, 1, HULL_COLOURS, PANELLED);
	private static final List<Face> EMITTER_FACES = lathe(new float[][] {HULL[0], HULL[1]}, 0, new int[][] {{-1, -1}}, new boolean[] {false});
	private static final List<Face> DOME_FACES = lathe(DOME, 0, domeColours(), new boolean[DOME.length - 1]);

	public UfoRenderer(EntityRendererProvider.Context context) {
		super(context);
		shadowRadius = 2.2f;
	}

	private static int[][] domeColours() {
		int[][] colours = new int[DOME.length - 1][];
		for (int i = 0; i < colours.length; i++) {
			colours[i] = new int[] {DOME_COLOURS[i], DOME_COLOURS[i + 1]};
		}
		return colours;
	}

	/**
	 * Turns a profile on a lathe: a ring of {@link #SIDES} flat facets for each span from point {@code from} on, wound to
	 * face outward. Each facet takes its span's bottom colour at its lower edge and its top colour at its upper one.
	 */
	private static List<Face> lathe(float[][] profile, int from, int[][] colours, boolean[] panelled) {
		List<Face> faces = new ArrayList<>();
		for (int span = from; span < profile.length - 1; span++) {
			float r0 = profile[span][0], y0 = profile[span][1], r1 = profile[span + 1][0], y1 = profile[span + 1][1];
			// Outward across the profile: (dy, -dr) in (radius, height).
			float outR = y1 - y0, outY = -(r1 - r0);
			for (int i = 0; i < SIDES; i++) {
				float a0 = Mth.TWO_PI * i / SIDES, a1 = Mth.TWO_PI * (i + 1) / SIDES, mid = (a0 + a1) * 0.5f;
				Vector3f[] corners = {ring(r0, y0, a0), ring(r0, y0, a1), ring(r1, y1, a1), ring(r1, y1, a0)};
				Vector3f normal = new Vector3f(Mth.cos(mid) * outR, outY, Mth.sin(mid) * outR).normalize();
				int[] span2 = colours[span - from];
				float shade = panelled[span - from] && i % 2 == 1 ? 0.93f : 1.0f;
				int bottom = shade(span2[0], shade), top = shade(span2[1], shade);
				int[] cornerColours = {bottom, bottom, top, top};
				faces.add(outward(corners, normal, cornerColours));
			}
		}
		return faces;
	}

	private static Vector3f ring(float radius, float y, float angle) {
		return new Vector3f(Mth.cos(angle) * radius, y, Mth.sin(angle) * radius);
	}

	private static int shade(int colour, float shade) {
		return colour == -1 ? -1 : ARGB.color(ARGB.alpha(colour), (int) (ARGB.red(colour) * shade), (int) (ARGB.green(colour) * shade),
				(int) (ARGB.blue(colour) * shade));
	}

	/** The corners in the order that faces the way of {@code normal} (counter-clockwise seen from that side). */
	private static Face outward(Vector3f[] corners, Vector3f normal, int[] colours) {
		Vector3f across = new Vector3f(corners[1]).sub(corners[0]).cross(new Vector3f(corners[2]).sub(corners[0]));
		if (across.lengthSquared() < 1.0e-12f) {
			across = new Vector3f(corners[2]).sub(corners[1]).cross(new Vector3f(corners[3]).sub(corners[1]));
		}
		if (across.dot(normal) < 0.0f) {
			return new Face(new Vector3f[] {corners[3], corners[2], corners[1], corners[0]}, normal,
					new int[] {colours[3], colours[2], colours[1], colours[0]});
		}
		return new Face(corners, normal, colours);
	}

	@Override
	public UfoRenderState createRenderState() {
		return new UfoRenderState();
	}

	@Override
	protected AABB getBoundingBoxForCulling(Ufo ufo, float partialTicks) {
		// Its legs reach down past its feet.
		return super.getBoundingBoxForCulling(ufo, partialTicks).expandTowards(0.0, -Ufo.LEG_HEIGHT - 0.2, 0.0);
	}

	@Override
	public void extractRenderState(Ufo ufo, UfoRenderState state, float partialTicks) {
		super.extractRenderState(ufo, state, partialTicks);
		state.spin = ufo.spin(partialTicks);
		state.yaw = ufo.getYRot(partialTicks);
		state.legs = ufo.legs(partialTicks);
		state.door = ufo.door(partialTicks);
		state.beaming = ufo.isBeaming();
		state.crashing = ufo.isCrashing();
		state.hurt = ufo.hurtTime > 0 || ufo.deathTime > 0;
		Vec3 velocity = new Vec3(ufo.getX() - ufo.xo, 0.0, ufo.getZ() - ufo.zo);
		double speed = velocity.length();
		if (state.crashing) {
			float wobble = state.ageInTicks * 0.3f;
			state.leanX = Mth.cos(wobble);
			state.leanZ = Mth.sin(wobble);
			state.lean = 0.45f;
		} else if (speed > 1.0e-3) {
			state.leanX = (float) (velocity.z / speed);
			state.leanZ = (float) (-velocity.x / speed);
			state.lean = (float) Math.min(0.18, speed * 0.4);
		} else {
			state.lean = 0.0f;
		}
	}

	@Override
	public void submit(UfoRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		int light = state.lightCoords;
		int overlay = OverlayTexture.pack(OverlayTexture.u(0.0f), OverlayTexture.v(state.hurt));
		poseStack.pushPose();
		if (state.lean > 0.0f) {
			poseStack.rotate(new Quaternionf().rotateAxis(state.lean, state.leanX, 0.0f, state.leanZ));
		}
		poseStack.rotate(Axis.YP.rotationDegrees(-state.yaw));
		collector.submitCustomGeometry(poseStack, SOLID, (pose, buffer) -> {
			legs(pose, buffer, state.legs, light, overlay);
			ramp(pose, buffer, state.door, light, overlay);
			pilot(pose, buffer, state.ageInTicks, light, overlay);
		});
		if (state.door > 0.0f) {
			collector.submitCustomGeometry(poseStack, GLOW, (pose, buffer) -> doorway(pose, buffer, state.door));
			collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) -> spill(pose, buffer, state.door));
		}

		poseStack.pushPose();
		poseStack.rotate(Axis.YP.rotationDegrees(state.yaw - state.spin));
		collector.submitCustomGeometry(poseStack, SOLID, (pose, buffer) -> {
			for (Face face : HULL_FACES) {
				face(pose, buffer, face, light, overlay, -1);
			}
		});
		int emitter = emitterColour(state);
		collector.submitCustomGeometry(poseStack, GLOW, (pose, buffer) -> {
			for (Face face : EMITTER_FACES) {
				face(pose, buffer, face, light, OverlayTexture.NO_OVERLAY, emitter);
			}
			lights(pose, buffer, state);
		});
		collector.submitCustomGeometry(poseStack, GLASS, (pose, buffer) -> {
			for (Face face : DOME_FACES) {
				face(pose, buffer, face, light, overlay, -1);
			}
		});
		poseStack.popPose();
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	/** Mint, pulsing; bright while beaming; dead once it's going down. */
	private static int emitterColour(UfoRenderState state) {
		if (state.crashing) {
			return 0xFF203028;
		}
		float pulse = 0.75f + 0.25f * Mth.sin(state.ageInTicks * (state.beaming ? 0.6f : 0.15f));
		float level = (state.beaming ? 1.0f : 0.6f) * pulse;
		return ARGB.colorFromFloat(1.0f, 0.55f * level, level, 0.82f * level);
	}

	private static void face(PoseStack.Pose pose, VertexConsumer buffer, Face face, int light, int overlay, int tint) {
		for (int i = 0; i < 4; i++) {
			Vector3f corner = face.corners()[i];
			int colour = tint != -1 ? tint : face.colours()[i];
			buffer.addVertex(pose, corner.x, corner.y, corner.z).setColor(colour).setUv(0.5f, 0.5f).setOverlay(overlay).setLight(light)
					.setNormal(pose, face.normal().x, face.normal().y, face.normal().z);
		}
	}

	/** An axis-aligned box. */
	private static void box(PoseStack.Pose pose, VertexConsumer buffer, float x0, float y0, float z0, float x1, float y1, float z1, int colour,
			int light, int overlay) {
		Vector3f[] c = {new Vector3f(x0, y0, z0), new Vector3f(x1, y0, z0), new Vector3f(x1, y1, z0), new Vector3f(x0, y1, z0),
				new Vector3f(x0, y0, z1), new Vector3f(x1, y0, z1), new Vector3f(x1, y1, z1), new Vector3f(x0, y1, z1)};
		int[][] quads = {{0, 1, 2, 3}, {5, 4, 7, 6}, {4, 0, 3, 7}, {1, 5, 6, 2}, {3, 2, 6, 7}, {4, 5, 1, 0}};
		Vector3f[] normals = {new Vector3f(0, 0, -1), new Vector3f(0, 0, 1), new Vector3f(-1, 0, 0), new Vector3f(1, 0, 0),
				new Vector3f(0, 1, 0), new Vector3f(0, -1, 0)};
		int[] colours = {colour, colour, colour, colour};
		for (int q = 0; q < 6; q++) {
			Vector3f[] corners = {c[quads[q][0]], c[quads[q][1]], c[quads[q][2]], c[quads[q][3]]};
			face(pose, buffer, outward(corners, normals[q], colours), light, overlay, -1);
		}
	}

	/** Four struts out and down from the belly, each with a foot pad; tucked away when they're in. */
	private static void legs(PoseStack.Pose root, VertexConsumer buffer, float out, int light, int overlay) {
		if (out < 0.02f) {
			return;
		}
		PoseStack stack = new PoseStack();
		for (int i = 0; i < 4; i++) {
			float angle = Mth.HALF_PI * i + Mth.PI * 0.25f;
			Vector3f top = ring(LEG_ROOT, LEG_TOP, angle);
			Vector3f foot = ring(LEG_ROOT + LEG_SPREAD * out, LEG_TOP - (LEG_TOP + (float) Ufo.LEG_HEIGHT) * out, angle);
			Vector3f along = new Vector3f(foot).sub(top);
			float length = along.length();
			stack.last().set(root);
			stack.translate(top.x, top.y, top.z);
			stack.rotate(new Quaternionf().rotationTo(0.0f, -1.0f, 0.0f, along.x / length, along.y / length, along.z / length));
			box(stack.last(), buffer, -0.07f, -length, -0.07f, 0.07f, 0.0f, 0.07f, 0xFF4A515C, light, overlay);
			stack.last().set(root);
			stack.translate(foot.x, foot.y, foot.z);
			box(stack.last(), buffer, -0.26f * out, 0.0f, -0.26f * out, 0.26f * out, 0.08f, 0.26f * out, 0xFF3A3F48, light, overlay);
		}
	}

	/** The ramp, hinged under the belly, swinging down until its end rests on the ground. */
	private static void ramp(PoseStack.Pose root, VertexConsumer buffer, float open, int light, int overlay) {
		if (open < 0.02f) {
			return;
		}
		float rest = (float) Math.asin(Math.min(1.0, (Ufo.LEG_HEIGHT + RAMP_Y) / RAMP_LENGTH));
		PoseStack stack = new PoseStack();
		stack.last().set(root);
		stack.translate(0.0f, RAMP_Y, RAMP_HINGE);
		stack.rotate(Axis.XP.rotation(rest * open));
		box(stack.last(), buffer, -RAMP_WIDTH * 0.5f, -0.06f, 0.0f, RAMP_WIDTH * 0.5f, 0.0f, RAMP_LENGTH, 0xFF5C6573, light, overlay);
	}

	/** The doorway the ramp leaves: glowing warm white in the belly. */
	private static void doorway(PoseStack.Pose pose, VertexConsumer buffer, float open) {
		int colour = ARGB.colorFromFloat(1.0f, open, open * 0.94f, open * 0.75f);
		float y = RAMP_Y - 0.005f;
		Vector3f[] corners = {new Vector3f(-RAMP_WIDTH * 0.45f, y, RAMP_HINGE), new Vector3f(RAMP_WIDTH * 0.45f, y, RAMP_HINGE),
				new Vector3f(RAMP_WIDTH * 0.45f, y, RAMP_HINGE + 1.0f), new Vector3f(-RAMP_WIDTH * 0.45f, y, RAMP_HINGE + 1.0f)};
		face(pose, buffer, outward(corners, new Vector3f(0.0f, -1.0f, 0.0f), new int[] {colour, colour, colour, colour}), 0xF000F0,
				OverlayTexture.NO_OVERLAY, -1);
	}

	/** Light spilling out of the doorway and down the ramp: a soft wedge, fading toward the ground. */
	private static void spill(PoseStack.Pose pose, VertexConsumer buffer, float open) {
		float ground = (float) -Ufo.LEG_HEIGHT;
		float foot = RAMP_HINGE + RAMP_LENGTH * 0.75f;
		float[][] wedge = {{-0.35f, RAMP_Y, RAMP_HINGE}, {0.35f, RAMP_Y, RAMP_HINGE}, {1.0f, ground, foot + 0.6f}, {-1.0f, ground, foot + 0.6f}};
		float[] alpha = {0.35f * open, 0.35f * open, 0.0f, 0.0f};
		for (int side = 0; side < 2; side++) {
			for (int i = 0; i < 4; i++) {
				float[] p = wedge[side == 0 ? i : 3 - i];
				buffer.addVertex(pose, p[0], p[1], p[2]).setColor(1.0f, 0.93f, 0.7f, alpha[side == 0 ? i : 3 - i]);
			}
		}
	}

	/** A little green head under the glass, looking about, two big dark eyes. */
	private static void pilot(PoseStack.Pose root, VertexConsumer buffer, float age, int light, int overlay) {
		PoseStack stack = new PoseStack();
		stack.last().set(root);
		stack.translate(0.0f, 1.22f, 0.15f);
		stack.rotate(Axis.YP.rotation(0.6f * Mth.sin(age * 0.03f)));
		box(stack.last(), buffer, -0.24f, 0.0f, -0.2f, 0.24f, 0.42f, 0.2f, 0xFF7CC04A, light, overlay);
		for (int side = -1; side <= 1; side += 2) {
			float x = side * 0.11f;
			box(stack.last(), buffer, x - 0.07f, 0.16f, 0.2f, x + 0.07f, 0.27f, 0.215f, 0xFF101418, light, overlay);
		}
	}

	/** Eight lights round the rim, each stepping through the colours a beat behind the last, so they chase round. */
	private static void lights(PoseStack.Pose pose, VertexConsumer buffer, UfoRenderState state) {
		int step = (int) (state.ageInTicks / 5.0f);
		for (int i = 0; i < LIGHTS; i++) {
			float angle = Mth.TWO_PI * (i + 0.5f) / LIGHTS;
			Vector3f at = ring(LIGHT_RADIUS, LIGHT_HEIGHT, angle);
			int colour = state.crashing && (step + i) % 3 != 0 ? 0xFF301010 : LIGHT_COLOURS[Math.floorMod(step - i, LIGHT_COLOURS.length)];
			box(pose, buffer, at.x - LIGHT_SIZE, at.y - LIGHT_SIZE, at.z - LIGHT_SIZE, at.x + LIGHT_SIZE, at.y + LIGHT_SIZE, at.z + LIGHT_SIZE,
					colour, 0xF000F0, OverlayTexture.NO_OVERLAY);
		}
	}
}
