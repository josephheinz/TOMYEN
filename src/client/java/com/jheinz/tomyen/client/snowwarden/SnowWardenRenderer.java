package com.jheinz.tomyen.client.snowwarden;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.snowwarden.SnowWarden;

/**
 * Draws the Snow Warden at full size, with two glow layers over the same cubes: its carved face, always lit, and its
 * chest crystals (and a faint light in its tendrils), which pulse slowly and flare as it charges the frost blast.
 */
public class SnowWardenRenderer extends MobRenderer<SnowWarden, SnowWardenRenderState, SnowWardenModel> {
	private static final Identifier TEXTURE = MyMod.id("textures/entity/snow_warden/snow_warden.png");
	private static final RenderType FACE = RenderTypes.eyes(MyMod.id("textures/entity/snow_warden/snow_warden_face.png"));
	private static final RenderType CRYSTALS = RenderTypes.eyes(MyMod.id("textures/entity/snow_warden/snow_warden_crystals.png"));

	public SnowWardenRenderer(EntityRendererProvider.Context context) {
		super(context, new SnowWardenModel(context.bakeLayer(SnowWardenModel.LAYER)), 1.2f);
		addLayer(new GlowLayer(this));
	}

	@Override
	public Identifier getTextureLocation(SnowWardenRenderState state) {
		return TEXTURE;
	}

	@Override
	public SnowWardenRenderState createRenderState() {
		return new SnowWardenRenderState();
	}

	@Override
	public void extractRenderState(SnowWarden warden, SnowWardenRenderState state, float partialTicks) {
		super.extractRenderState(warden, state, partialTicks);
		state.swing = warden.swingProgress(partialTicks);
		state.swingLeft = warden.action() == SnowWarden.SWING_LEFT;
		state.slam = warden.slamProgress(partialTicks);
		state.charge = warden.chargeAmount(partialTicks);
		state.blast = warden.blastProgress(partialTicks);
		state.throwing = warden.throwProgress(partialTicks);
		state.roar = warden.roarProgress(partialTicks);
		state.sniff = warden.sniffAmount(partialTicks);
		float pulse = 0.55f + 0.15f * Mth.sin(state.ageInTicks * 0.08f);
		state.crystalGlow = Mth.clamp(pulse + (1.0f - pulse) * state.charge * 1.2f, 0.0f, 1.0f);
	}

	@Override
	protected AABB getBoundingBoxForCulling(SnowWarden warden, float partialTicks) {
		// Its fists reach well past its box when raised or spread.
		return super.getBoundingBoxForCulling(warden, partialTicks).inflate(1.2, 0.8, 1.2);
	}

	/** The carved face and the chest crystals, glowing in the dark. */
	private static class GlowLayer extends RenderLayer<SnowWardenRenderState, SnowWardenModel> {
		GlowLayer(SnowWardenRenderer renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, SnowWardenRenderState state, float yRot, float xRot) {
			if (state.isInvisible) {
				return;
			}
			// The eyes render type adds its colour, so a dimmer glow is a darker tint.
			collector.order(1).submitModel(getParentModel(), state, poseStack, FACE, light, OverlayTexture.NO_OVERLAY,
					ARGB.white(1.0f), null, state.outlineColor);
			float g = state.crystalGlow;
			collector.order(1).submitModel(getParentModel(), state, poseStack, CRYSTALS, light, OverlayTexture.NO_OVERLAY,
					ARGB.colorFromFloat(1.0f, g, g, g), null, state.outlineColor);
		}
	}
}
