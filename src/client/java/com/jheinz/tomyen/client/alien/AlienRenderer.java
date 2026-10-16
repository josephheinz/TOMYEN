package com.jheinz.tomyen.client.alien;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.alien.Alien;

public class AlienRenderer extends MobRenderer<Alien, AlienRenderState, AlienModel> {
	private static final Identifier TEXTURE = MyMod.id("textures/entity/alien/alien.png");

	public AlienRenderer(EntityRendererProvider.Context context) {
		super(context, new AlienModel(context.bakeLayer(AlienModel.LAYER)), 0.35f);
	}

	@Override
	public Identifier getTextureLocation(AlienRenderState state) {
		return TEXTURE;
	}

	@Override
	public AlienRenderState createRenderState() {
		return new AlienRenderState();
	}

	@Override
	public void extractRenderState(Alien alien, AlienRenderState state, float partialTicks) {
		super.extractRenderState(alien, state, partialTicks);
		state.seed = (alien.getId() % 53) * 0.71f;
		state.dazed = alien.isDazed();
		state.earsCovered = alien.earsCovered();
		state.attackTime = alien.getSwingAnimation(partialTicks);
	}
}
