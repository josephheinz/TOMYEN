package com.jheinz.mymod.client.holy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.holy.LightOrb;

/** A glowing ball of light: a soft pulsing halo around a bright core, both always facing the camera. */
public class LightOrbRenderer extends EntityRenderer<LightOrb, EntityRenderState> {
	private static final RenderType GLOW = RenderTypes.eyes(MyMod.id("textures/entity/light_orb.png"));

	public LightOrbRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected int getBlockLightLevel(LightOrb orb, BlockPos pos) {
		return 15;
	}

	@Override
	public void submit(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(0.0f, 0.2f, 0.0f);
		poseStack.rotate(camera.orientation);
		float pulse = 1.0f + 0.12f * Mth.sin(state.ageInTicks * 0.6f);
		poseStack.pushPose();
		poseStack.scale(1.3f * pulse, 1.3f * pulse, 1.3f * pulse);
		collector.submitCustomGeometry(poseStack, GLOW, (pose, buffer) -> quad(pose, buffer, 0x90FFF4C8));
		poseStack.popPose();
		poseStack.scale(0.55f, 0.55f, 0.55f);
		collector.submitCustomGeometry(poseStack, GLOW, (pose, buffer) -> quad(pose, buffer, 0xFFFFFFFF));
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer buffer, int color) {
		vertex(buffer, pose, -0.5f, -0.5f, 0, 1, color);
		vertex(buffer, pose, 0.5f, -0.5f, 1, 1, color);
		vertex(buffer, pose, 0.5f, 0.5f, 1, 0, color);
		vertex(buffer, pose, -0.5f, 0.5f, 0, 0, color);
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, int u, int v, int color) {
		buffer.addVertex(pose, x, y, 0.0f).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0)
				.setNormal(pose, 0.0f, 1.0f, 0.0f);
	}

	@Override
	public EntityRenderState createRenderState() {
		return new EntityRenderState();
	}
}
