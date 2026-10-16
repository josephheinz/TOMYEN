package com.jheinz.tomyen.client.powerarmor;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

import com.jheinz.tomyen.powerarmor.PowerArmorStationBlock;
import com.jheinz.tomyen.powerarmor.PowerArmorStationBlockEntity;
import com.jheinz.tomyen.powerarmor.PowerArmorSuit;
import com.jheinz.tomyen.powerarmor.SuitLook;

/**
 * The suit on a station's rack, standing on the base plate facing out, piece by piece as they're fitted. Once
 * it's complete (and nobody's in it) it stands open, back hatch up and helmet raised, ready to climb into.
 */
public class PowerArmorStationRenderer implements BlockEntityRenderer<PowerArmorStationBlockEntity, PowerArmorStationRenderer.State> {
	public static class State extends BlockEntityRenderState {
		SuitLook look = SuitLook.NONE;
		float yaw;
		boolean complete;
		float time;
	}

	public PowerArmorStationRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(PowerArmorStationBlockEntity station, State state, float partialTicks, Vec3 cameraPosition,
			ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(station, state, partialTicks, cameraPosition, breakProgress);
		PowerArmorSuit suit = station.suit();
		state.look = suit.look();
		state.complete = suit.complete();
		state.yaw = PowerArmorStationBlock.yaw(station.getBlockState());
		state.time = station.getLevel() == null ? 0.0f : station.getLevel().getGameTime() + partialTicks;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.look.isEmpty()) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(0.5, PowerArmorStationBlock.BASE_HEIGHT, 0.5);
		PowerArmorRenderer.submitStanding(poseStack, collector, state.look, state.yaw, state.complete ? 1.0f : 0.35f, state.time,
				state.lightCoords, 0);
		poseStack.popPose();
	}

	/** The suit stands two blocks tall, so keep drawing it while only its top half is on screen. */
	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}
}
