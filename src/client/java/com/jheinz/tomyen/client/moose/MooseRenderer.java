package com.jheinz.tomyen.client.moose;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.moose.Moose;

/**
 * Draws a moose at full size (a calf at half, its head a little big for its body), in its coat: the bull's near-black
 * brown, the cow's warmer brown, or the calf's reddish fawn. The saddle is part of the model, shown when there's one on.
 */
public class MooseRenderer extends MobRenderer<Moose, MooseRenderState, MooseModel> {
	private static final Identifier BULL = MyMod.id("textures/entity/moose/moose_bull.png");
	private static final Identifier COW = MyMod.id("textures/entity/moose/moose_cow.png");
	private static final Identifier CALF = MyMod.id("textures/entity/moose/moose_calf.png");

	public MooseRenderer(EntityRendererProvider.Context context) {
		super(context, new MooseModel(context.bakeLayer(MooseModel.LAYER)), 1.0f);
	}

	@Override
	public Identifier getTextureLocation(MooseRenderState state) {
		return state.isBaby ? CALF : state.bull ? BULL : COW;
	}

	@Override
	public MooseRenderState createRenderState() {
		return new MooseRenderState();
	}

	@Override
	public void extractRenderState(Moose moose, MooseRenderState state, float partialTicks) {
		super.extractRenderState(moose, state, partialTicks);
		state.bull = moose.isBull();
		state.saddled = moose.isSaddled();
		state.antlers = moose.antlerGrowth();
		state.browse = moose.browseAmount(partialTicks);
		state.windup = moose.windupAmount(partialTicks);
		state.pawClock = moose.pawClock(partialTicks);
		state.charge = moose.chargeAmount(partialTicks);
		state.stomp = moose.stompProgress(partialTicks);
	}

	@Override
	protected void scale(MooseRenderState state, PoseStack poseStack) {
		float scale = state.ageScale;
		poseStack.scale(scale, scale, scale);
	}
}
