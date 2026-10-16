package com.jheinz.tomyen.client.skullcrane;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.skullcrane.ServoDrone;

/**
 * Draws a Servo Drone: the skullcrane's model at {@value #SCALE} size in brass-plated, green-eyed colours, its
 * arms reaching for whatever it's after and the item it's fetching held in its claws underneath.
 */
public class ServoDroneRenderer extends MobRenderer<ServoDrone, ServoDroneRenderer.State, SkullcraneModel> {
	private static final Identifier TEXTURE = MyMod.id("textures/entity/skullcrane/servo_drone.png");
	private static final RenderType GLOW = RenderTypes.eyes(MyMod.id("textures/entity/skullcrane/servo_drone_glow.png"));
	private static final float SCALE = 0.6f;
	private static final double REACH_START = 3.0;
	private static final double FULL_REACH = 1.2;

	public static class State extends SkullcraneRenderState {
		public final ItemStackRenderState carried = new ItemStackRenderState();
	}

	public ServoDroneRenderer(EntityRendererProvider.Context context) {
		super(context, new SkullcraneModel(context.bakeLayer(SkullcraneModel.LAYER)), 0.2f);
		addLayer(new SkullcraneRenderer.GlowLayer<>(this, GLOW));
		addLayer(new CarriedLayer(this));
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(ServoDrone drone, State state, float partialTicks) {
		super.extractRenderState(drone, state, partialTicks);
		state.seed = (drone.getId() % 97) * 0.37f;
		state.flare = drone.flare(partialTicks);
		state.clamp = drone.clamp(partialTicks);
		state.chatter = drone.chatter(partialTicks);
		state.attackAnim = drone.getSwingAnimation(partialTicks);
		state.deathProgress = Math.min(1.0f, state.deathTime / 20.0f);
		state.glow = state.deathTime > 0 ? 0.0f : 0.7f + 0.3f * state.flare;
		itemModelResolver.updateForLiving(state.carried, drone.carried(), ItemDisplayContext.GROUND, drone);

		Vec3 velocity = SkullcraneRenderer.toModel(new Vec3(drone.getX() - drone.xo, drone.getY() - drone.yo, drone.getZ() - drone.zo),
				state.bodyRot);
		state.velocityX = (float) velocity.x;
		state.velocityY = (float) velocity.y;
		state.velocityZ = (float) velocity.z;
		state.reach = 0.0f;
		Entity target = drone.reaching();
		if (target != null && drone.isAlive()) {
			Vec3 to = target.getPosition(partialTicks).add(0.0, target.getBbHeight() * 0.5, 0.0).subtract(drone.getPosition(partialTicks));
			double distance = to.length();
			if (distance > 1.0e-3) {
				Vec3 direction = SkullcraneRenderer.toModel(to.scale(1.0 / distance), state.bodyRot);
				state.reachX = (float) direction.x;
				state.reachY = (float) direction.y;
				state.reachZ = (float) direction.z;
				state.reach = (float) Mth.clamp((REACH_START - distance) / (REACH_START - FULL_REACH), 0.0, 1.0);
			}
		}
	}

	@Override
	protected void scale(State state, PoseStack poseStack) {
		poseStack.scale(SCALE, SCALE, SCALE);
	}

	/** The item it's fetching, hanging in its claws under the skull. */
	private static class CarriedLayer extends RenderLayer<State, SkullcraneModel> {
		CarriedLayer(ServoDroneRenderer renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, State state, float yRot, float xRot) {
			if (state.carried.isEmpty()) {
				return;
			}
			poseStack.pushPose();
			// Model space is upside down, in blocks, with the feet at 1.5: down where the claws close.
			poseStack.translate(0.0f, 2.2f, -0.05f);
			poseStack.rotateDegrees(Axis.ZP, 180.0f);
			poseStack.scale(1.1f, 1.1f, 1.1f);
			state.carried.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
			poseStack.popPose();
		}
	}
}
