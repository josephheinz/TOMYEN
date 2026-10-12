package com.jheinz.mymod.client.powerarmor;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;

import com.jheinz.mymod.powerarmor.ParkedPowerArmor;
import com.jheinz.mymod.powerarmor.SuitLook;

/** A parked suit: standing where it was left, facing the way its wearer did, wide open at the back. */
public class ParkedPowerArmorRenderer extends EntityRenderer<ParkedPowerArmor, ParkedPowerArmorRenderer.State> {
	public static class State extends EntityRenderState {
		SuitLook look = SuitLook.NONE;
		float yaw;
	}

	public ParkedPowerArmorRenderer(EntityRendererProvider.Context context) {
		super(context);
		shadowRadius = 0.6f;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(ParkedPowerArmor parked, State state, float partialTicks) {
		super.extractRenderState(parked, state, partialTicks);
		state.look = parked.look();
		state.yaw = parked.getYRot();
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		PowerArmorRenderer.submitStanding(poseStack, collector, state.look, state.yaw, 1.0f, state.ageInTicks, state.lightCoords, state.outlineColor);
		super.submit(state, poseStack, collector, camera);
	}
}
