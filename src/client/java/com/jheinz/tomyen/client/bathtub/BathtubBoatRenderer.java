package com.jheinz.tomyen.client.bathtub;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.bathtub.BathtubBoat;

/**
 * Draws a Bathtub Boat: settled a little into the water afloat ({@link BathtubBoat#SINK}), its bow lifting about the
 * stern at speed and leaning into its turns, rocking when it's hit and in bubble columns as a boat does.
 */
public class BathtubBoatRenderer extends EntityRenderer<BathtubBoat, BathtubBoatRenderState> {
	private static final Identifier TEXTURE = MyMod.id("textures/block/bathtub.png");
	/** The bow lifts about the stern, this far back from the middle (blocks)... */
	private static final float STERN = 1.05f;
	/** ...by up to this many degrees. */
	private static final float MAX_LIFT = 9.0f;
	/** Leaning into a hard turn, degrees. */
	private static final float MAX_ROLL = 5.0f;

	private final BathtubBoatModel model;

	public BathtubBoatRenderer(EntityRendererProvider.Context context) {
		super(context);
		model = new BathtubBoatModel(context.bakeLayer(BathtubBoatModel.LAYER));
		shadowRadius = 0.8f;
	}

	@Override
	public BathtubBoatRenderState createRenderState() {
		return new BathtubBoatRenderState();
	}

	@Override
	protected AABB getBoundingBoxForCulling(BathtubBoat boat, float partialTicks) {
		// The tub and its motor reach past the box at both ends.
		return super.getBoundingBoxForCulling(boat, partialTicks).inflate(1.0, 0.5, 1.0);
	}

	@Override
	public void extractRenderState(BathtubBoat boat, BathtubBoatRenderState state, float partialTicks) {
		super.extractRenderState(boat, state, partialTicks);
		state.yRot = boat.getYRot(partialTicks);
		state.hurtTime = boat.getHurtTime() - partialTicks;
		state.hurtDir = boat.getHurtDir();
		state.damageTime = Math.max(boat.getDamage() - partialTicks, 0.0f);
		state.bubbleAngle = boat.getBubbleAngle(partialTicks);
		state.isUnderWater = boat.isUnderWater();
		state.rowingTimeLeft = boat.getRowingTime(0, partialTicks);
		state.rowingTimeRight = boat.getRowingTime(1, partialTicks);
		state.lift = boat.lift(partialTicks);
		state.sink = boat.sink(partialTicks);
		state.tilt = boat.tilt(partialTicks);
		state.steer = boat.steer(partialTicks);
		state.propeller = boat.propeller(partialTicks);
		state.waddle = boat.waddle(partialTicks);
		state.waddleAmount = boat.waddleAmount();
		state.duck = boat.duck(partialTicks);
		state.rowing = boat.isInWater() && boat.getControllingPassenger() != null && !boat.engineRunning();
		state.rowLeft = state.rowingTimeLeft;
		state.rowRight = state.rowingTimeRight;
	}

	@Override
	public void submit(BathtubBoatRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(0.0, -BathtubBoat.SINK * state.sink, 0.0);
		// From here the bow is -z and the boat's left -x, until the model's flip.
		poseStack.rotateDegrees(Axis.YP, 180.0f - state.yRot);
		if (state.hurtTime > 0.0f) {
			poseStack.rotateDegrees(Axis.ZP, Mth.sin(state.hurtTime) * state.hurtTime * state.damageTime / 10.0f * state.hurtDir);
		}
		if (!state.isUnderWater && !Mth.equal(state.bubbleAngle, 0.0f)) {
			poseStack.rotate(new Quaternionf().setAngleAxis(state.bubbleAngle * Mth.DEG_TO_RAD, 1.0f, 0.0f, 1.0f));
		}
		if (state.lift > 0.0f) {
			poseStack.translate(0.0f, 0.0f, STERN);
			poseStack.rotateDegrees(Axis.XP, MAX_LIFT * state.lift);
			poseStack.translate(0.0f, 0.0f, -STERN);
		}
		float roll = MAX_ROLL * state.steer * Math.min(1.0f, 0.3f + state.lift * 2.0f) * state.sink;
		if (roll != 0.0f) {
			poseStack.rotateDegrees(Axis.ZP, roll);
		}
		poseStack.scale(-1.0f, -1.0f, 1.0f);
		poseStack.translate(0.0f, -1.501f, 0.0f);
		model.setupAnim(state);
		collector.submitModel(model, state, poseStack, TEXTURE, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
