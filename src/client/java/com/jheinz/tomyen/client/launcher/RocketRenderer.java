package com.jheinz.tomyen.client.launcher;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.launcher.Launchers;
import com.jheinz.tomyen.launcher.PG7Rocket;

/**
 * A rocket in flight: its item model with the fins out and the motor lit (see tomyen:rocket_in_flight), pointed
 * along its velocity and drawn at the same scale as the launcher in a player's hands.
 */
public class RocketRenderer extends EntityRenderer<PG7Rocket, RocketRenderer.State> {
	private static final float SCALE = 0.58f;
	/** The middle of the flight model along its length, in pixels (tools/gen_rpg.py). */
	private static final float CENTRE_Z = -1.8f;

	public static class State extends EntityRenderState {
		final ItemStackRenderState item = new ItemStackRenderState();
		float yaw;
		float pitch;
	}

	private final ItemModelResolver itemModelResolver;

	public RocketRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.itemModelResolver = context.getItemModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(PG7Rocket rocket, State state, float partialTicks) {
		super.extractRenderState(rocket, state, partialTicks);
		ItemStack flying = rocket.getItem().copy();
		flying.set(Launchers.ROCKET_IN_FLIGHT, Unit.INSTANCE);
		itemModelResolver.updateForNonLiving(state.item, flying, ItemDisplayContext.NONE, rocket);
		Vec3 velocity = rocket.getDeltaMovement();
		if (velocity.lengthSqr() > 1.0e-6) {
			state.yaw = (float) Math.toDegrees(Mth.atan2(velocity.x, velocity.z));
			state.pitch = (float) Math.toDegrees(Mth.atan2(velocity.y, velocity.horizontalDistance()));
		} else {
			state.yaw = rocket.getViewYRot(partialTicks);
			state.pitch = rocket.getViewXRot(partialTicks);
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(0.0f, 0.15f, 0.0f);
		// The model's warhead points toward -z.
		poseStack.rotateDegrees(Axis.YP, state.yaw + 180.0f);
		poseStack.rotateDegrees(Axis.XP, state.pitch);
		poseStack.scale(SCALE, SCALE, SCALE);
		// Centre the model (bore at x 8, y 9) on the entity; item rendering shifts it by half a block itself.
		poseStack.translate(0.0f, -1.0f / 16.0f, 0.5f - CENTRE_Z / 16.0f);
		state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
