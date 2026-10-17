package com.jheinz.tomyen.client.crow;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.crow.Crow;

/** Draws a crow at a little over half its model's size, with whatever it's stolen dangling from its beak. */
public class CrowRenderer extends MobRenderer<Crow, CrowRenderState, CrowModel> {
	private static final Identifier TEXTURE = MyMod.id("textures/entity/crow/crow.png");
	private static final float SCALE = 0.55f;

	public CrowRenderer(EntityRendererProvider.Context context) {
		super(context, new CrowModel(context.bakeLayer(CrowModel.LAYER)), 0.18f);
		addLayer(new BeakLayer(this));
	}

	@Override
	public Identifier getTextureLocation(CrowRenderState state) {
		return TEXTURE;
	}

	@Override
	public CrowRenderState createRenderState() {
		return new CrowRenderState();
	}

	@Override
	public void extractRenderState(Crow crow, CrowRenderState state, float partialTicks) {
		super.extractRenderState(crow, state, partialTicks);
		state.fly = crow.flyAmount(partialTicks);
		state.flap = crow.flap(partialTicks);
		state.flapStrength = crow.flapStrength();
		state.flightPitch = crow.flightPitch(partialTicks);
		state.peck = crow.peck(partialTicks);
		state.caw = crow.caw(partialTicks);
		state.sleeping = crow.isSleeping();
		itemModelResolver.updateForLiving(state.carried, crow.getMainHandItem(), ItemDisplayContext.GROUND, crow);
	}

	@Override
	protected void scale(CrowRenderState state, PoseStack poseStack) {
		poseStack.scale(SCALE, SCALE, SCALE);
	}

	/** The loot, held crosswise at the tip of the beak, hanging below it. */
	private static class BeakLayer extends RenderLayer<CrowRenderState, CrowModel> {
		BeakLayer(CrowRenderer renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, CrowRenderState state, float yRot, float xRot) {
			if (state.carried.isEmpty()) {
				return;
			}
			poseStack.pushPose();
			CrowModel model = getParentModel();
			model.root().translateAndRotate(poseStack);
			model.body().translateAndRotate(poseStack);
			model.head().translateAndRotate(poseStack);
			model.beak().translateAndRotate(poseStack);
			// Model space is upside down, in blocks: out to the tip of the beak and down below it.
			poseStack.translate(0.0f, 3.0f / 16.0f, -3.6f / 16.0f);
			poseStack.rotateDegrees(Axis.ZP, 180.0f);
			poseStack.rotateDegrees(Axis.YP, 90.0f);
			poseStack.scale(0.6f, 0.6f, 0.6f);
			state.carried.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
			poseStack.popPose();
		}
	}
}
