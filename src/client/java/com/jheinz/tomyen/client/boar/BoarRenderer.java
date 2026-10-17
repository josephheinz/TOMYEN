package com.jheinz.tomyen.client.boar;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.boar.Boar;

/** Draws the boar: grizzled adults, striped piglets at half size. */
public class BoarRenderer extends MobRenderer<Boar, BoarRenderState, BoarModel> {
	private static final Identifier TEXTURE = MyMod.id("textures/entity/boar/boar.png");
	private static final Identifier PIGLET_TEXTURE = MyMod.id("textures/entity/boar/boar_piglet.png");

	private final BoarModel adult;
	private final BoarModel baby;

	public BoarRenderer(EntityRendererProvider.Context context) {
		super(context, new BoarModel(context.bakeLayer(BoarModel.LAYER)), 0.6f);
		adult = getModel();
		baby = new BoarModel(context.bakeLayer(BoarModel.BABY_LAYER));
	}

	@Override
	public void submit(BoarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		model = state.isBaby ? baby : adult;
		super.submit(state, poseStack, collector, camera);
	}

	@Override
	public Identifier getTextureLocation(BoarRenderState state) {
		return state.isBaby ? PIGLET_TEXTURE : TEXTURE;
	}

	@Override
	public BoarRenderState createRenderState() {
		return new BoarRenderState();
	}

	@Override
	public void extractRenderState(Boar boar, BoarRenderState state, float partialTicks) {
		super.extractRenderState(boar, state, partialTicks);
		state.tusker = boar.isTusker();
		state.root = boar.poseAmount(Boar.ROOTING, partialTicks);
		state.paw = boar.poseAmount(Boar.PAWING, partialTicks);
		state.charge = boar.poseAmount(Boar.CHARGING, partialTicks);
		state.stun = boar.poseAmount(Boar.STUNNED, partialTicks);
		state.attackAnim = boar.getSwingAnimation(partialTicks);
		state.agitated = boar.isAggressive() ? 1.0f : Math.max(state.paw, state.charge);
	}
}
