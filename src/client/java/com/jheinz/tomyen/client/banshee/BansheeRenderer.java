package com.jheinz.tomyen.client.banshee;

import com.mojang.blaze3d.vertex.PoseStack;

import org.jspecify.annotations.Nullable;

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

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.banshee.Banshee;

/**
 * Draws the Banshee translucent: one model, culled at the back faces, each texel carrying its own alpha (solid enough
 * in the face and hands, fading down the gown and to the ends of her hair and wisps), the whole of her flickering
 * faintly. Her eye sockets' glints are a glow layer over the same cubes, so there's no second skin to fight the first.
 * She doesn't keel over dying: she fades out as she sinks, or, banished, flies apart and is gone in half the time.
 */
public class BansheeRenderer extends MobRenderer<Banshee, BansheeRenderState, BansheeModel> {
	private static final Identifier TEXTURE = MyMod.id("textures/entity/banshee/banshee.png");
	private static final RenderType BODY = RenderTypes.entityTranslucentCull(TEXTURE);
	private static final RenderType EYES = RenderTypes.eyes(MyMod.id("textures/entity/banshee/banshee_eyes.png"));

	public BansheeRenderer(EntityRendererProvider.Context context) {
		super(context, new BansheeModel(context.bakeLayer(BansheeModel.LAYER)), 0.3f);
		addLayer(new GlowLayer(this));
	}

	@Override
	public Identifier getTextureLocation(BansheeRenderState state) {
		return TEXTURE;
	}

	@Override
	public BansheeRenderState createRenderState() {
		return new BansheeRenderState();
	}

	@Override
	public void extractRenderState(Banshee banshee, BansheeRenderState state, float partialTicks) {
		super.extractRenderState(banshee, state, partialTicks);
		state.windup = banshee.windupAmount(partialTicks);
		state.scream = banshee.screamAmount(partialTicks);
		state.attackAnim = banshee.getSwingAnimation(partialTicks);
		state.banished = banshee.isBanished();
		state.deathProgress = Math.min(1.0f, state.deathTime / (state.banished ? Banshee.BANISH_TICKS : Banshee.DEATH_TICKS));
		// A slow, uneven flicker, steadier while she wails; then out as she dies.
		float flicker = 0.9f + 0.06f * Mth.sin(state.ageInTicks * 0.13f) + 0.04f * Mth.sin(state.ageInTicks * 0.37f + 1.0f);
		flicker = Mth.lerp(Math.max(state.windup, state.scream), flicker, 1.0f);
		float dying = state.banished ? state.deathProgress * state.deathProgress : state.deathProgress;
		state.alpha = Mth.clamp(flicker * (1.0f - dying), 0.0f, 1.0f);
	}

	@Override
	protected @Nullable RenderType getRenderType(BansheeRenderState state, boolean bodyVisible, boolean forceTransparent, boolean glowing) {
		if (!bodyVisible && !forceTransparent) {
			return glowing ? RenderTypes.outline(TEXTURE) : null;
		}
		return BODY;
	}

	@Override
	protected int getModelTint(BansheeRenderState state) {
		// Banished, she burns white as she goes.
		return state.banished ? ARGB.colorFromFloat(state.alpha, 1.0f, 1.0f, 0.9f) : ARGB.white(state.alpha);
	}

	@Override
	protected float getShadowRadius(BansheeRenderState state) {
		return super.getShadowRadius(state) * state.alpha;
	}

	@Override
	protected float getFlipDegrees() {
		return 0.0f;
	}

	/** The glints in her sockets, glowing in the dark, fading with her. */
	private static class GlowLayer extends RenderLayer<BansheeRenderState, BansheeModel> {
		GlowLayer(BansheeRenderer renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, BansheeRenderState state, float yRot, float xRot) {
			if (state.alpha <= 0.02f || state.isInvisible) {
				return;
			}
			// The eyes render type adds its colour, so it fades by darkening.
			float a = state.alpha;
			collector.order(1).submitModel(getParentModel(), state, poseStack, EYES, light, OverlayTexture.NO_OVERLAY,
					ARGB.colorFromFloat(1.0f, a, a, a), null, state.outlineColor);
		}
	}
}
