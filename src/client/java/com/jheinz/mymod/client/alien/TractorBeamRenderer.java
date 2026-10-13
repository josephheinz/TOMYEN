package com.jheinz.mymod.client.alien;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.alien.TractorBeam;

/**
 * A tractor beam: a pale green cone of light from the saucer's emitter (or from high overhead) widening down past
 * whatever it's lifting, a brighter core inside it, and faint rings drifting up it. Additive light, no texture. Drawn even
 * with the victim off screen, since the beam reaches far beyond it.
 */
public class TractorBeamRenderer extends EntityRenderer<TractorBeam, TractorBeamRenderer.State> {
	private static final int SIDES = 16;
	private static final float TOP_RADIUS = 0.6f;
	private static final float BOTTOM_RADIUS = 1.4f;
	/** How far below its victim's feet the beam reaches. */
	private static final float BELOW = 0.6f;
	private static final float RING_SPACING = 1.6f;

	public static class State extends EntityRenderState {
		/** The beam's source, relative to its victim's feet. */
		public Vec3 source = Vec3.ZERO;
	}

	public TractorBeamRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected boolean affectedByCulling(TractorBeam beam) {
		return false;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(TractorBeam beam, State state, float partialTicks) {
		super.extractRenderState(beam, state, partialTicks);
		state.source = beam.source(partialTicks).subtract(beam.getPosition(partialTicks));
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		Vector3f top = state.source.toVector3f();
		Vector3f bottom = new Vector3f(0.0f, -BELOW, 0.0f);
		float time = state.ageInTicks;
		collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) -> {
			cone(pose, buffer, top, bottom, TOP_RADIUS, BOTTOM_RADIUS, 0.22f, 0.05f);
			cone(pose, buffer, top, bottom, TOP_RADIUS * 0.5f, BOTTOM_RADIUS * 0.45f, 0.22f, 0.08f);
			float length = top.distance(bottom);
			// Rings drifting up the beam.
			for (float d = (time * 0.05f) % RING_SPACING; d < length; d += RING_SPACING) {
				float t = d / length;
				Vector3f at = new Vector3f(bottom).lerp(top, t);
				Vector3f below = new Vector3f(bottom).lerp(top, Math.max(0.0f, t - 0.04f));
				float radius = Mth.lerp(t, BOTTOM_RADIUS, TOP_RADIUS) * 1.02f;
				cone(pose, buffer, at, below, radius, radius, 0.25f * (1.0f - t * 0.5f), 0.0f);
			}
		});
		super.submit(state, poseStack, collector, camera);
	}

	/** A tube from {@code top} to {@code bottom} (both ends open), fading from one alpha to the other. */
	private static void cone(PoseStack.Pose pose, VertexConsumer buffer, Vector3f top, Vector3f bottom, float topRadius, float bottomRadius,
			float topAlpha, float bottomAlpha) {
		for (int i = 0; i < SIDES; i++) {
			float a0 = Mth.TWO_PI * i / SIDES;
			float a1 = Mth.TWO_PI * (i + 1) / SIDES;
			// Both windings, so it shows from inside as well as out.
			for (int side = 0; side < 2; side++) {
				float first = side == 0 ? a0 : a1;
				float second = side == 0 ? a1 : a0;
				vertex(pose, buffer, bottom, bottomRadius, first, bottomAlpha);
				vertex(pose, buffer, bottom, bottomRadius, second, bottomAlpha);
				vertex(pose, buffer, top, topRadius, second, topAlpha);
				vertex(pose, buffer, top, topRadius, first, topAlpha);
			}
		}
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vector3f centre, float radius, float angle, float alpha) {
		buffer.addVertex(pose, centre.x + Mth.cos(angle) * radius, centre.y, centre.z + Mth.sin(angle) * radius)
				.setColor(0.62f, 1.0f, 0.82f, alpha);
	}
}
