package com.jheinz.tomyen.client.isle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

import com.jheinz.tomyen.isle.GravestoneBlock;
import com.jheinz.tomyen.isle.GravestoneBlockEntity;

/**
 * The owner's name cut into a gravestone's face, on the sunken panel below the carving (laid out by tools/gen_isle.py:
 * for a stone facing north the panel's face is at z {@value #PANEL_Z_PX}/16, between y 5.5 and 9.5), turned to the
 * stone's facing. Long names shrink to fit the panel.
 */
public class GravestoneRenderer implements BlockEntityRenderer<GravestoneBlockEntity, GravestoneRenderer.State> {
	private static final float PANEL_Z_PX = 7.5f;
	private static final float PANEL_Z = PANEL_Z_PX / 16.0f - 0.5f;
	private static final float NAME_Y = 7.6f / 16.0f - 0.5f;
	private static final float RIP_Y = 9.9f / 16.0f - 0.5f;
	private static final float PANEL_WIDTH = 7.0f / 16.0f;
	/** Cut deep into grey stone: dark, with a faint soul-blue cast. */
	private static final int CARVED = 0xFF1C2328;

	public static class State extends BlockEntityRenderState {
		Direction facing = Direction.NORTH;
		@Nullable FormattedCharSequence name;
	}

	private final Font font;

	public GravestoneRenderer(BlockEntityRendererProvider.Context context) {
		font = context.font();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(GravestoneBlockEntity grave, State state, float partialTicks, Vec3 cameraPosition,
			ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(grave, state, partialTicks, cameraPosition, breakProgress);
		state.facing = grave.getBlockState().hasProperty(GravestoneBlock.FACING) ? grave.getBlockState().getValue(GravestoneBlock.FACING)
				: Direction.NORTH;
		state.name = grave.ownerName().isEmpty() ? null : Component.literal(grave.ownerName()).getVisualOrderText();
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.name == null) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(0.5f, 0.5f, 0.5f);
		poseStack.rotate(Axis.YP.rotationDegrees(-yaw(state.facing)));
		line(state, poseStack, collector, state.name, NAME_Y, 0.0085f);
		line(state, poseStack, collector, Component.translatable("block.tomyen.gravestone.rip").getVisualOrderText(), RIP_Y, 0.0055f);
		poseStack.popPose();
	}

	private void line(State state, PoseStack poseStack, SubmitNodeCollector collector, FormattedCharSequence text, float y, float maxScale) {
		int width = font.width(text);
		float scale = Math.min(maxScale, PANEL_WIDTH / Math.max(1, width));
		poseStack.pushPose();
		poseStack.translate(0.0f, y, PANEL_Z - 0.003f);
		// Text reads along +x with +y down, toward a viewer at +z: turned to face out of the stone, which faces -z.
		poseStack.rotate(Axis.YP.rotationDegrees(180.0f));
		poseStack.scale(scale, -scale, scale);
		collector.submitText(poseStack, -width / 2.0f, -4.0f, text, false, Font.DisplayMode.POLYGON_OFFSET, state.lightCoords, CARVED, 0, 0);
		poseStack.popPose();
	}

	private static float yaw(Direction facing) {
		return switch (facing) {
			case EAST -> 90.0f;
			case SOUTH -> 180.0f;
			case WEST -> 270.0f;
			default -> 0.0f;
		};
	}
}
