package com.jheinz.tomyen.client.isle.shade;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.isle.shade.SoulWisp;

/**
 * A Soul Wisp: one camera-facing quad of soul fire (a bright core fading out to a soft halo, all in one texture, so
 * nothing overlaps), full-bright, breathing slowly in size and turning. Also the fallback form for a shade whose mob
 * can't be drawn as itself.
 */
public class SoulWispRenderer extends EntityRenderer<SoulWisp, EntityRenderState> {
	private static final Identifier TEXTURE = MyMod.id("textures/entity/soul_wisp.png");
	private static final RenderType RENDER_TYPE = RenderTypes.entityTranslucentEmissive(TEXTURE);

	public SoulWispRenderer(EntityRendererProvider.Context context) {
		super(context);
		shadowRadius = 0.0f;
	}

	@Override
	public EntityRenderState createRenderState() {
		return new EntityRenderState();
	}

	@Override
	protected int getBlockLightLevel(SoulWisp entity, BlockPos pos) {
		return 15;
	}

	@Override
	public void submit(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(0.0f, state.boundingBoxHeight * 0.5f, 0.0f);
		submitWisp(poseStack, collector, camera, state.ageInTicks + (float) state.x * 3.0f, 0.75f, 0xFFFFFFFF);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	/** A wisp {@code size} across, centred on the pose's origin, coloured {@code argb}. */
	static void submitWisp(PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, float age, float size, int argb) {
		poseStack.pushPose();
		float breath = 1.0f + 0.08f * Mth.sin(age * 0.21f) + 0.04f * Mth.sin(age * 0.53f);
		poseStack.rotate(camera.orientation);
		poseStack.rotate(Axis.ZP.rotationDegrees(age * 2.0f));
		poseStack.scale(size * breath, size * breath, size * breath);
		collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> {
			vertex(buffer, pose, -0.5f, -0.5f, 0, 1, argb);
			vertex(buffer, pose, 0.5f, -0.5f, 1, 1, argb);
			vertex(buffer, pose, 0.5f, 0.5f, 1, 0, argb);
			vertex(buffer, pose, -0.5f, 0.5f, 0, 0, argb);
		});
		poseStack.popPose();
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, int u, int v, int argb) {
		buffer.addVertex(pose, x, y, 0.0f)
				.setColor(argb)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(LightCoordsUtil.FULL_BRIGHT)
				.setNormal(pose, 0.0f, 1.0f, 0.0f);
	}
}
