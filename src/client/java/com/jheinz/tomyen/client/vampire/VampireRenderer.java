package com.jheinz.tomyen.client.vampire;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

import org.jspecify.annotations.Nullable;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.vampire.Vampire;

/**
 * Draws the vampire faded by how well lit it is: in the dark only its glowing eyes show, and it fades in as
 * the light rises. The eyes always show.
 */
public class VampireRenderer extends MobRenderer<Vampire, VampireRenderState, VampireModel> {
	private static final Identifier TEXTURE = MyMod.id("textures/entity/vampire/vampire.png");
	private static final Identifier CLOAK_TEXTURE = MyMod.id("textures/entity/vampire/vampire_cloak.png");
	private static final RenderType EYES = RenderTypes.eyes(MyMod.id("textures/entity/vampire/vampire_eyes.png"));
	/** Below this it isn't drawn at all; above {@link #OPAQUE} it's drawn solid. */
	private static final float HIDDEN = 0.02f;
	private static final float OPAQUE = 0.99f;

	public VampireRenderer(EntityRendererProvider.Context context) {
		super(context, new VampireModel(context.bakeLayer(VampireModel.LAYER)), 0.5f);
		addLayer(new CloakLayer(this, new VampireCloakModel(context.bakeLayer(VampireCloakModel.LAYER))));
		addLayer(new VampireEyesLayer(this));
	}

	@Override
	public Identifier getTextureLocation(VampireRenderState state) {
		return TEXTURE;
	}

	@Override
	public VampireRenderState createRenderState() {
		return new VampireRenderState();
	}

	@Override
	public void extractRenderState(Vampire vampire, VampireRenderState state, float partialTicks) {
		super.extractRenderState(vampire, state, partialTicks);
		HumanoidMobRenderer.extractHumanoidRenderState(vampire, state, partialTicks, itemModelResolver);
		state.isCrouching = vampire.isStalking();
		state.lunging = vampire.isAggressive();
		state.hovering = vampire.isNoGravity();
		state.visibility = vampire.visibility(partialTicks);
	}

	@Override
	protected @Nullable RenderType getRenderType(VampireRenderState state, boolean bodyVisible, boolean forceTransparent, boolean glowing) {
		if (state.visibility < HIDDEN) {
			return glowing ? RenderTypes.outline(TEXTURE) : null;
		}
		return renderType(TEXTURE, state);
	}

	private static RenderType renderType(Identifier texture, VampireRenderState state) {
		return state.visibility < OPAQUE ? RenderTypes.entityTranslucent(texture) : RenderTypes.entityCutout(texture);
	}

	@Override
	protected int getModelTint(VampireRenderState state) {
		return ARGB.white(state.visibility);
	}

	@Override
	protected float getShadowRadius(VampireRenderState state) {
		return super.getShadowRadius(state) * state.visibility;
	}

	private static class CloakLayer extends RenderLayer<VampireRenderState, VampireModel> {
		private final VampireCloakModel cloak;

		CloakLayer(VampireRenderer renderer, VampireCloakModel cloak) {
			super(renderer);
			this.cloak = cloak;
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, VampireRenderState state, float yRot, float xRot) {
			if (state.visibility < HIDDEN) {
				return;
			}
			cloak.setupAnim(state);
			cloak.body.loadPose(getParentModel().body.storePose());
			collector.submitModel(cloak, state, poseStack, renderType(CLOAK_TEXTURE, state), light,
					LivingEntityRenderer.getOverlayCoords(state, 0.0f), ARGB.white(state.visibility), null, state.outlineColor);
		}
	}

	private static class VampireEyesLayer extends EyesLayer<VampireRenderState, VampireModel> {
		VampireEyesLayer(VampireRenderer renderer) {
			super(renderer);
		}

		@Override
		public RenderType renderType() {
			return EYES;
		}
	}
}
