package com.jheinz.tomyen.client.fae;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.fae.Fairy;

/** Draws a fairy small and always fully lit (it glows), with whatever it's stolen in its hands. */
public class FairyRenderer extends MobRenderer<Fairy, FairyRenderState, FairyModel> {
	private static final Identifier TEXTURE = MyMod.id("textures/entity/fairy.png");
	private static final float SCALE = 0.8f;

	public FairyRenderer(EntityRendererProvider.Context context) {
		super(context, new FairyModel(context.bakeLayer(FairyModel.LAYER)), 0.15f);
		addLayer(new ItemInHandLayer<>(this));
	}

	@Override
	public Identifier getTextureLocation(FairyRenderState state) {
		return TEXTURE;
	}

	@Override
	public FairyRenderState createRenderState() {
		return new FairyRenderState();
	}

	@Override
	public void extractRenderState(Fairy fairy, FairyRenderState state, float partialTicks) {
		super.extractRenderState(fairy, state, partialTicks);
		ArmedEntityRenderState.extractArmedEntityRenderState(fairy, state, itemModelResolver, partialTicks);
	}

	@Override
	protected int getBlockLightLevel(Fairy fairy, BlockPos pos) {
		return 15;
	}

	@Override
	protected void scale(FairyRenderState state, PoseStack poseStack) {
		poseStack.scale(SCALE, SCALE, SCALE);
	}
}
