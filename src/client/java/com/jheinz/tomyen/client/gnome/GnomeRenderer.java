package com.jheinz.tomyen.client.gnome;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemDisplayContext;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.gnome.Gnome;
import com.jheinz.tomyen.gnome.GnomeCaptain;

/**
 * Draws a gnome or a Gnome Captain (one renderer each, on their own model layers) at a little over half its model's
 * size, in its coat colour, hugging whatever it's stolen.
 * <p>
 * As it sets into a statue at dawn, its glazed-ceramic twin texture fades in over its own (and back out as it wakes),
 * it shudders, and its stride and clock stop where they were: a statue stands frozen mid-step, still holding its carrot.
 */
public class GnomeRenderer extends MobRenderer<Gnome, GnomeRenderState, GnomeModel> {
	private static final float SCALE = 0.52f;
	private static final String[] COATS = {"blue", "green", "ochre"};
	private static final Identifier[] TROOP = new Identifier[COATS.length];
	private static final Identifier[] TROOP_STATUE = new Identifier[COATS.length];
	private static final Identifier CAPTAIN = MyMod.id("textures/entity/gnome/gnome_captain.png");
	private static final Identifier CAPTAIN_STATUE = MyMod.id("textures/entity/gnome/gnome_captain_statue.png");

	static {
		for (int i = 0; i < COATS.length; i++) {
			TROOP[i] = MyMod.id("textures/entity/gnome/gnome_" + COATS[i] + ".png");
			TROOP_STATUE[i] = MyMod.id("textures/entity/gnome/gnome_" + COATS[i] + "_statue.png");
		}
	}

	public GnomeRenderer(EntityRendererProvider.Context context, boolean captain) {
		super(context, new GnomeModel(context.bakeLayer(captain ? GnomeModel.CAPTAIN_LAYER : GnomeModel.TROOP_LAYER)), 0.22f);
		addLayer(new GlazeLayer(this));
		addLayer(new CarriedLayer(this));
	}

	static Identifier alive(GnomeRenderState state) {
		return state.captain ? CAPTAIN : TROOP[Math.floorMod(state.variant, TROOP.length)];
	}

	static Identifier statue(GnomeRenderState state) {
		return state.captain ? CAPTAIN_STATUE : TROOP_STATUE[Math.floorMod(state.variant, TROOP_STATUE.length)];
	}

	/** Fully set, it's drawn in its glaze outright; otherwise in its own colours, with the glaze faded over them. */
	@Override
	public Identifier getTextureLocation(GnomeRenderState state) {
		return state.statue >= 1.0f ? statue(state) : alive(state);
	}

	@Override
	public GnomeRenderState createRenderState() {
		return new GnomeRenderState();
	}

	@Override
	public void extractRenderState(Gnome gnome, GnomeRenderState state, float partialTicks) {
		super.extractRenderState(gnome, state, partialTicks);
		state.variant = gnome.variant();
		state.captain = gnome instanceof GnomeCaptain;
		state.carrying = gnome.isCarrying();
		byte mode = gnome.mode();
		state.marching = mode == Gnome.MODE_MARCHING;
		state.panic = mode == Gnome.MODE_PANIC;
		state.attack = gnome.getSwingAnimation(partialTicks);
		state.statue = gnome.statueAmount(partialTicks);
		state.shudder = state.statue > 0.0f && state.statue < 1.0f ? 4.0f * state.statue * (1.0f - state.statue) : 0.0f;
		state.shudderAge = state.ageInTicks;
		if (gnome.isStatue()) {
			state.walkAnimationPos = gnome.frozenWalkPos();
			state.walkAnimationSpeed = gnome.frozenWalkSpeed();
			state.ageInTicks = gnome.frozenAge();
			state.marching = false;
			state.panic = false;
			state.attack = 0.0f;
		}
		itemModelResolver.updateForLiving(state.carried, gnome.getMainHandItem(), ItemDisplayContext.GROUND, gnome);
	}

	@Override
	protected void scale(GnomeRenderState state, PoseStack poseStack) {
		poseStack.scale(SCALE, SCALE, SCALE);
	}

	/** The glaze, fading in over its own colours as it sets (and out again as it wakes). */
	private static class GlazeLayer extends RenderLayer<GnomeRenderState, GnomeModel> {
		GlazeLayer(GnomeRenderer renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, GnomeRenderState state, float yRot, float xRot) {
			if (state.statue <= 0.0f || state.statue >= 1.0f || state.isInvisible) {
				return;
			}
			collector.order(1).submitModel(getParentModel(), state, poseStack, RenderTypes.entityTranslucent(statue(state)), light,
					LivingEntityRenderer.getOverlayCoords(state, 0.0f), ARGB.white(state.statue), null, state.outlineColor);
		}
	}

	/** What it stole, hugged to its belly in front of its beard. */
	private static class CarriedLayer extends RenderLayer<GnomeRenderState, GnomeModel> {
		CarriedLayer(GnomeRenderer renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, GnomeRenderState state, float yRot, float xRot) {
			if (state.carried.isEmpty()) {
				return;
			}
			poseStack.pushPose();
			getParentModel().root().translateAndRotate(poseStack);
			getParentModel().body().translateAndRotate(poseStack);
			// Model space is upside down, in blocks: up the belly (from the hips) and out past the beard.
			poseStack.translate(0.0f, -3.5f / 16.0f, -5.0f / 16.0f);
			poseStack.rotateDegrees(Axis.ZP, 180.0f);
			poseStack.scale(0.8f, 0.8f, 0.8f);
			state.carried.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
			poseStack.popPose();
		}
	}
}
