package com.jheinz.mymod.client.banana;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.banana.BigBanana;

/** Draws the Big Banana. */
public class BigBananaRenderer extends MobRenderer<BigBanana, BigBananaRenderState, BigBananaModel> {
	private static final Identifier TEXTURE = MyMod.id("textures/entity/big_banana/big_banana.png");

	public BigBananaRenderer(EntityRendererProvider.Context context) {
		super(context, new BigBananaModel(context.bakeLayer(BigBananaModel.LAYER)), 0.6f);
	}

	@Override
	public Identifier getTextureLocation(BigBananaRenderState state) {
		return TEXTURE;
	}

	@Override
	public BigBananaRenderState createRenderState() {
		return new BigBananaRenderState();
	}

	@Override
	public void extractRenderState(BigBanana banana, BigBananaRenderState state, float partialTicks) {
		super.extractRenderState(banana, state, partialTicks);
		state.attackAnim = banana.getSwingAnimation(partialTicks);
		state.happy = banana.happyProgress(partialTicks);
	}
}
