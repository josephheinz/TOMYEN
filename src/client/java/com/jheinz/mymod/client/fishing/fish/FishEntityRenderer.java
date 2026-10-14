package com.jheinz.mymod.client.fishing.fish;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.LivingEntityEmissiveLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.fishing.fish.ModFish;

/**
 * Draws any living fish, as vanilla draws its cod and salmon: the body wags as it swims, and out of water the fish lies
 * on its side and flops harder. A fish with glowing parts gets them drawn again full bright from its _glow texture. A
 * lava fish counts lava as its water.
 */
public class FishEntityRenderer extends MobRenderer<ModFish, LivingEntityRenderState, FishEntityModel> {
	private final FishModelLayers.Shape shape;
	private final Identifier texture;

	public FishEntityRenderer(EntityRendererProvider.Context context, FishModelLayers.Shape shape, ModelLayerLocation layer) {
		super(context, new FishEntityModel(context.bakeLayer(layer)), shape.shadow());
		this.shape = shape;
		this.texture = MyMod.id("textures/entity/fish/" + shape.id() + ".png");
		if (shape.glows()) {
			Identifier glow = MyMod.id("textures/entity/fish/" + shape.id() + "_glow.png");
			addLayer(new LivingEntityEmissiveLayer<>(this, state -> glow, (state, ageInTicks) -> 1.0f,
					new FishEntityModel(context.bakeLayer(layer)), RenderTypes::entityTranslucentEmissive, false));
		}
	}

	@Override
	public Identifier getTextureLocation(LivingEntityRenderState state) {
		return texture;
	}

	@Override
	public LivingEntityRenderState createRenderState() {
		return new LivingEntityRenderState();
	}

	@Override
	public void extractRenderState(ModFish fish, LivingEntityRenderState state, float partialTicks) {
		super.extractRenderState(fish, state, partialTicks);
		if (fish.kind().isLava()) {
			state.isInWater = fish.isInLava();
		}
	}

	@Override
	protected void setupRotations(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float entityScale) {
		super.setupRotations(state, poseStack, bodyRot, entityScale);
		float amplitude = state.isInWater ? 1.0f : 1.3f;
		float pace = state.isInWater ? 1.0f : 1.7f;
		poseStack.rotateDegrees(Axis.YP, amplitude * 4.3f * Mth.sin(pace * 0.6f * state.ageInTicks));
		if (!state.isInWater) {
			// On its side, centred where it lay and resting on its flank rather than half through the ground.
			poseStack.translate(shape.height() / 2.0f, shape.halfWidth(), 0.0f);
			poseStack.rotateDegrees(Axis.ZP, 90.0f);
		}
	}
}
