package com.jheinz.tomyen.client.fishing.catfish;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.fishing.catfish.GiantCatfish;

/**
 * Draws the Giant Catfish: sized by its weight, pitched as it climbs and dives, and on its side when it's out of the
 * water. Its box only covers its middle, so it's kept on screen by its full length.
 */
public class GiantCatfishRenderer extends MobRenderer<GiantCatfish, GiantCatfishRenderState, GiantCatfishModel> {
	private static final Identifier TEXTURE = MyMod.id("textures/entity/giant_catfish/giant_catfish.png");

	public GiantCatfishRenderer(EntityRendererProvider.Context context) {
		super(context, new GiantCatfishModel(context.bakeLayer(GiantCatfishModel.LAYER)), 1.6f);
	}

	@Override
	public Identifier getTextureLocation(GiantCatfishRenderState state) {
		return TEXTURE;
	}

	@Override
	public GiantCatfishRenderState createRenderState() {
		return new GiantCatfishRenderState();
	}

	@Override
	public void extractRenderState(GiantCatfish fish, GiantCatfishRenderState state, float partialTicks) {
		super.extractRenderState(fish, state, partialTicks);
		state.mounted = false;
		state.beached = !fish.isInWater() && fish.state() != GiantCatfish.SURFACING;
		state.swim = fish.swim(partialTicks);
		state.tail = fish.tail(partialTicks);
		state.slap = fish.slap(partialTicks);
		state.slapSide = fish.slapRight() ? 1.0f : -1.0f;
		state.thrash = fish.thrash(partialTicks);
		state.mouth = fish.mouth(partialTicks);
		state.roar = fish.roar(partialTicks);
		state.pitch = fish.pitch(partialTicks);
		state.sizeScale = fish.sizeScale();
	}

	@Override
	protected void setupRotations(GiantCatfishRenderState state, PoseStack poseStack, float bodyRot, float entityScale) {
		super.setupRotations(state, poseStack, bodyRot, entityScale);
		float size = state.sizeScale;
		if (state.beached && state.deathTime <= 0.0f) {
			// Beached, it lies on its side.
			poseStack.translate(0.5f * size, 0.45f * size, 0.0f);
			poseStack.rotate(Axis.ZP.rotationDegrees(90.0f));
		} else if (state.pitch != 0.0f) {
			poseStack.translate(0.0f, 0.5f * size, 0.0f);
			poseStack.rotate(Axis.XP.rotationDegrees(state.pitch));
			poseStack.translate(0.0f, -0.5f * size, 0.0f);
		}
	}

	@Override
	protected void scale(GiantCatfishRenderState state, PoseStack poseStack) {
		poseStack.scale(state.sizeScale, state.sizeScale, state.sizeScale);
	}

	@Override
	protected float getShadowRadius(GiantCatfishRenderState state) {
		return super.getShadowRadius(state) * state.sizeScale;
	}

	@Override
	protected AABB getBoundingBoxForCulling(GiantCatfish fish, float partialTicks) {
		float reach = 1.8f * fish.sizeScale();
		return super.getBoundingBoxForCulling(fish, partialTicks).inflate(reach, 0.5, reach);
	}
}
