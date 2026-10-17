package com.jheinz.tomyen.client.shrimp;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.shrimp.Shrimp;

/** Draws the Shrimp with a Gun. */
public class ShrimpRenderer extends MobRenderer<Shrimp, ShrimpRenderState, ShrimpModel> {
	private static final Identifier TEXTURE = MyMod.id("textures/entity/shrimp/shrimp.png");

	public ShrimpRenderer(EntityRendererProvider.Context context) {
		super(context, new ShrimpModel(context.bakeLayer(ShrimpModel.LAYER)), 0.6f);
	}

	@Override
	public Identifier getTextureLocation(ShrimpRenderState state) {
		return TEXTURE;
	}

	@Override
	public ShrimpRenderState createRenderState() {
		return new ShrimpRenderState();
	}

	@Override
	public void extractRenderState(Shrimp shrimp, ShrimpRenderState state, float partialTicks) {
		super.extractRenderState(shrimp, state, partialTicks);
		state.attackAnim = shrimp.getSwingAnimation(partialTicks);
		state.aim = shrimp.aim(partialTicks);
		state.recoil = shrimp.recoil(partialTicks);
		state.reload = shrimp.reloadProgress(partialTicks);
	}
}
