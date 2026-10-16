package com.jheinz.tomyen.client.mech;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Unit;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

import com.jheinz.tomyen.mech.MechGantryBlock;
import com.jheinz.tomyen.mech.MechGantryBlockEntity;
import com.jheinz.tomyen.mech.MechLoadout;

/**
 * A Mech Gantry: its frame and deck, and the walker standing in it facing out, part by part as they're fitted.
 * Once it's complete it stands with its chest hatch up, ready to climb into. The frame is far bigger than the
 * block, so it's drawn while any of it is on screen.
 */
public class MechGantryRenderer implements BlockEntityRenderer<MechGantryBlockEntity, MechGantryRenderer.State> {
	private static final RenderType BASE = RenderTypes.entityCutout(MechRenderer.TEXTURE);
	/** Model space puts the feet this far below the origin (vanilla's offset). */
	private static final float FEET = 1.501f;

	public static class State extends BlockEntityRenderState {
		final MechRenderState mech = new MechRenderState();
		float yaw;
		boolean empty;
	}

	private final MechModel mechModel;
	private final MechGantryModel frameModel;

	public MechGantryRenderer(BlockEntityRendererProvider.Context context) {
		mechModel = new MechModel(context.bakeLayer(MechModel.LAYER));
		frameModel = new MechGantryModel(context.bakeLayer(MechGantryModel.LAYER));
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(MechGantryBlockEntity gantry, State state, float partialTicks, Vec3 cameraPosition,
			ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(gantry, state, partialTicks, cameraPosition, breakProgress);
		int look = gantry.look();
		state.empty = look == 0;
		state.yaw = MechGantryBlock.yaw(gantry.getBlockState());
		MechRenderState mech = state.mech;
		mech.look = look;
		mech.powered = MechLoadout.coreLit(look);
		mech.hatch = MechLoadout.complete(look) ? 1.0f : 0.0f;
		mech.lightCoords = state.lightCoords;
		mech.ageInTicks = gantry.getLevel() == null ? 0.0f : gantry.getLevel().getGameTime() + partialTicks;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(0.5, 0.0, 0.5);
		poseStack.rotateDegrees(Axis.YP, 180.0f - state.yaw);
		poseStack.scale(-1.0f, -1.0f, 1.0f);
		poseStack.translate(0.0f, -FEET, 0.0f);
		collector.submitModel(frameModel, Unit.INSTANCE, poseStack, BASE, state.lightCoords, OverlayTexture.NO_OVERLAY, -1, null, 0);
		if (!state.empty) {
			// The walker stands on the deck.
			poseStack.translate(0.0f, -(float) MechGantryBlock.DECK_HEIGHT, 0.0f);
			collector.submitModel(mechModel, state.mech, poseStack, BASE, state.lightCoords, OverlayTexture.NO_OVERLAY, -1, null, 0);
			collector.order(1).submitModel(mechModel, state.mech, poseStack, MechRenderer.GLOW, state.lightCoords, OverlayTexture.NO_OVERLAY,
					state.mech.powered ? MechRenderer.LIT : MechRenderer.DARK, null, 0);
		}
		poseStack.popPose();
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public int getViewDistance() {
		return 96;
	}
}
