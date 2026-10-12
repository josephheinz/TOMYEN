package com.jheinz.mymod.client.skullcrane;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.skullcrane.Skullcrane;
import com.jheinz.mymod.skullcrane.SkullcraneGrip;

/**
 * Draws a skullcrane, with its lenses and antigrav emitter glowing on top (brighter as it hunts, dark once it's dead).
 * Clamped onto a victim it's drawn exactly where its grip holds it, worked out from the victim's own position this
 * frame, so the pack stays fixed over the victim's shoulders however fast they're carried up, and its arms reach
 * down onto the nearest shoulder. Otherwise its arms reach for a target close by and trail behind it as it flies,
 * all worked out here in model space.
 */
public class SkullcraneRenderer extends MobRenderer<Skullcrane, SkullcraneRenderState, SkullcraneModel> {
	private static final Identifier TEXTURE = MyMod.id("textures/entity/skullcrane/skullcrane.png");
	private static final RenderType GLOW = RenderTypes.eyes(MyMod.id("textures/entity/skullcrane/skullcrane_glow.png"));
	/** Arms start reaching for a target this close, and are at full stretch within {@link #FULL_REACH}. */
	private static final double REACH_START = 4.0;
	private static final double FULL_REACH = 1.8;
	/** How far above its feet its arms hang from. */
	private static final double ARM_ROOT = 0.2;

	public SkullcraneRenderer(EntityRendererProvider.Context context) {
		super(context, new SkullcraneModel(context.bakeLayer(SkullcraneModel.LAYER)), 0.35f);
		addLayer(new GlowLayer(this));
	}

	@Override
	public Identifier getTextureLocation(SkullcraneRenderState state) {
		return TEXTURE;
	}

	@Override
	public SkullcraneRenderState createRenderState() {
		return new SkullcraneRenderState();
	}

	@Override
	public void extractRenderState(Skullcrane skull, SkullcraneRenderState state, float partialTicks) {
		super.extractRenderState(skull, state, partialTicks);
		state.seed = (skull.getId() % 97) * 0.37f;
		state.flare = skull.flare(partialTicks);
		state.clamp = skull.clamp(partialTicks);
		state.chatter = skull.chatter(partialTicks);
		state.attackAnim = skull.getSwingAnimation(partialTicks);
		state.deathProgress = Math.min(1.0f, state.deathTime / 20.0f);
		state.glow = state.deathTime > 0 ? 0.0f : 0.65f + 0.35f * Math.max(state.flare, state.clamp);

		Vec3 velocity = toModel(new Vec3(skull.getX() - skull.xo, skull.getY() - skull.yo, skull.getZ() - skull.zo), state.bodyRot);
		state.velocityX = (float) velocity.x;
		state.velocityY = (float) velocity.y;
		state.velocityZ = (float) velocity.z;

		state.reach = 0.0f;
		Entity target = skull.syncedTarget();
		if (target == null || !skull.isAlive()) {
			return;
		}
		Vec3 targetPos = new Vec3(Mth.lerp(partialTicks, target.xOld, target.getX()), Mth.lerp(partialTicks, target.yOld, target.getY()),
				Mth.lerp(partialTicks, target.zOld, target.getZ()));
		Vec3 to;
		if (skull.isClamped() && target instanceof LivingEntity victim) {
			float bodyYaw = Mth.rotLerp(partialTicks, victim.yBodyRotO, victim.yBodyRot);
			Vec3 offset = SkullcraneGrip.holdOffset(bodyYaw, victim.getBbWidth(), victim.getBbHeight(), skull.slot(), skull.slots());
			Vec3 at = targetPos.add(offset);
			state.x = at.x;
			state.y = at.y;
			state.z = at.z;
			// Facing in, looking down on it.
			state.bodyRot = (float) (Mth.atan2(-offset.z, -offset.x) * Mth.RAD_TO_DEG) - 90.0f;
			state.yRot = 0.0f;
			state.xRot = 35.0f;
			state.velocityX = 0.0f;
			state.velocityY = 0.0f;
			state.velocityZ = 0.0f;
			// Down onto the shoulder under it.
			Vec3 side = offset.horizontal().normalize();
			Vec3 shoulder = targetPos.add(side.scale(victim.getBbWidth() * 0.45)).add(0.0, victim.getBbHeight() * 0.8, 0.0);
			to = shoulder.subtract(at.add(0.0, ARM_ROOT, 0.0));
			state.reach = 1.0f;
		} else {
			to = targetPos.add(0.0, target.getBbHeight() * 0.6, 0.0).subtract(skull.getPosition(partialTicks).add(0.0, ARM_ROOT, 0.0));
			state.reach = (float) Mth.clamp((REACH_START - to.length()) / (REACH_START - FULL_REACH), 0.0, 1.0);
		}
		double distance = to.length();
		if (distance > 1.0e-3) {
			Vec3 direction = toModel(to.scale(1.0 / distance), state.bodyRot);
			state.reachX = (float) direction.x;
			state.reachY = (float) direction.y;
			state.reachZ = (float) direction.z;
		} else {
			state.reach = 0.0f;
		}
	}

	/** From world space into the model's: undoes the body's turn and the renderer's upside-down flip. */
	private static Vec3 toModel(Vec3 world, float bodyRot) {
		float angle = (180.0f - bodyRot) * Mth.DEG_TO_RAD;
		float cos = Mth.cos(angle);
		float sin = Mth.sin(angle);
		double x = world.x * cos - world.z * sin;
		double z = world.x * sin + world.z * cos;
		return new Vec3(-x, -world.y, z);
	}

	/** The lenses and emitter, full bright whatever the light, dimmed by how worked up it is and dark once it's dead. */
	private static class GlowLayer extends RenderLayer<SkullcraneRenderState, SkullcraneModel> {
		GlowLayer(SkullcraneRenderer renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, SkullcraneRenderState state, float yRot, float xRot) {
			if (state.glow <= 0.0f) {
				return;
			}
			collector.order(1).submitModel(getParentModel(), state, poseStack, GLOW, light, OverlayTexture.NO_OVERLAY, ARGB.gray(state.glow),
					null, state.outlineColor);
		}
	}
}
