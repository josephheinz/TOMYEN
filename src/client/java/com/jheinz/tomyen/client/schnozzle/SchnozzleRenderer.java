package com.jheinz.tomyen.client.schnozzle;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.schnozzle.Schnozzle;

/**
 * Draws the Schnozzle at twice its model's size. It doesn't keel over when it dies (the model deflates instead), and
 * its tentacles are pointed at its target and trail behind it as it moves, worked out here in model space.
 */
public class SchnozzleRenderer extends MobRenderer<Schnozzle, SchnozzleRenderState, SchnozzleModel> {
	private static final Identifier TEXTURE = MyMod.id("textures/entity/schnozzle/schnozzle.png");
	private static final float SCALE = 2.0f;
	/** Tentacles start reaching for a target this close, and are at full stretch within {@link #FULL_REACH}. */
	private static final double REACH_START = 9.0;
	private static final double FULL_REACH = 5.0;

	public SchnozzleRenderer(EntityRendererProvider.Context context) {
		super(context, new SchnozzleModel(context.bakeLayer(SchnozzleModel.LAYER)), 1.4f);
	}

	@Override
	public Identifier getTextureLocation(SchnozzleRenderState state) {
		return TEXTURE;
	}

	@Override
	public SchnozzleRenderState createRenderState() {
		return new SchnozzleRenderState();
	}

	@Override
	public void extractRenderState(Schnozzle schnozzle, SchnozzleRenderState state, float partialTicks) {
		super.extractRenderState(schnozzle, state, partialTicks);
		int attack = schnozzle.attackState();
		state.aggressive = schnozzle.isAggressive() || attack != Schnozzle.IDLE;
		state.flare = schnozzle.flare(partialTicks);
		state.firing = attack == Schnozzle.FIRING;
		state.sneezing = attack == Schnozzle.SNEEZE_WINDUP;
		state.sneezeJolt = schnozzle.sneezeJolt(partialTicks);
		state.attackAnim = schnozzle.getSwingAnimation(partialTicks);
		state.deathProgress = Math.min(1.0f, state.deathTime / Schnozzle.DEATH_TICKS);

		Vec3 velocity = toModel(new Vec3(schnozzle.getX() - schnozzle.xo, schnozzle.getY() - schnozzle.yo, schnozzle.getZ() - schnozzle.zo),
				state.bodyRot);
		state.velocityX = (float) velocity.x;
		state.velocityY = (float) velocity.y;
		state.velocityZ = (float) velocity.z;

		state.reach = 0.0f;
		Entity target = schnozzle.syncedTarget();
		if (target != null && schnozzle.isAlive()) {
			// From just under the nose, where the tentacles hang, to the middle of the target.
			Vec3 from = schnozzle.getPosition(partialTicks).add(0.0, -0.5, 0.0);
			Vec3 to = target.getPosition(partialTicks).add(0.0, target.getBbHeight() * 0.5, 0.0).subtract(from);
			double distance = to.length();
			if (distance > 1.0e-3) {
				Vec3 direction = toModel(to.scale(1.0 / distance), state.bodyRot);
				state.reachX = (float) direction.x;
				state.reachY = (float) direction.y;
				state.reachZ = (float) direction.z;
				state.reach = (float) Mth.clamp((REACH_START - distance) / (REACH_START - FULL_REACH), 0.0, 1.0);
			}
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

	@Override
	protected void scale(SchnozzleRenderState state, PoseStack poseStack) {
		poseStack.scale(SCALE, SCALE, SCALE);
	}

	@Override
	protected float getFlipDegrees() {
		return 0.0f;
	}
}
