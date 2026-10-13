package com.jheinz.mymod.client.meteorite;

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
import com.jheinz.mymod.meteorite.Meteor;

/**
 * A meteor: a fireball, an orange glow round a white-hot core, always facing the camera and drawn at full
 * brightness. The flame and smoke trail is the entity's own particles.
 */
public class MeteorRenderer extends EntityRenderer<Meteor, EntityRenderState> {
	private static final RenderType GLOW = RenderTypes.eyes(MyMod.id("textures/entity/meteor.png"));

	public MeteorRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected int getBlockLightLevel(Meteor meteor, BlockPos pos) {
		return 15;
	}

	@Override
	public void submit(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(0.0f, 0.5f, 0.0f);
		poseStack.rotate(camera.orientation);
		float flicker = 1.0f + 0.08f * Mth.sin(state.ageInTicks * 1.7f);
		layer(poseStack, collector, 3.2f * flicker, 0xB0FF8A30);
		layer(poseStack, collector, 1.2f, 0xFFFFF4D8);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	private static void layer(PoseStack poseStack, SubmitNodeCollector collector, float size, int color) {
		poseStack.pushPose();
		poseStack.scale(size, size, size);
		collector.submitCustomGeometry(poseStack, GLOW, (pose, buffer) -> quad(pose, buffer, color));
		poseStack.popPose();
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
