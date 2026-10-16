package com.jheinz.tomyen.client.mech;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.mech.Mech;
import com.jheinz.tomyen.mech.MechArm;
import com.jheinz.tomyen.mech.MechCrew;
import com.jheinz.tomyen.mech.MechLoadout;
import com.jheinz.tomyen.mech.MechWeapon;

/**
 * The walker's own weapon arms in the cockpit view, where a player's hands would be: each fitted forearm, from the
 * walker's own model, held low in its corner of the view like a first-person hand, scaled down to sit in frame.
 * <ul>
 * <li>They point where the arms really point: as the torso eases after a turn of the view, the arms swing in from the
 * side, and they pitch with the arms, lagging the view the same way.
 * <li>The model's own animation runs on them: the autocannon's barrel kicks back, the rotary barrels spin, the fist
 * punches and grips. Each shot also kicks the whole arm back, and a spinning rotary cannon chatters.
 * <li>They ride the walk: a drop at every footfall, easing back up, and a little sway.
 * </ul>
 */
public final class MechFirstPerson {
	private static final RenderType BASE = RenderTypes.entityCutout(MechRenderer.TEXTURE);
	/** Model pixels to view units, and where each elbow sits (right of centre, up, forward), in view units. */
	private static final float SCALE = 0.5f;
	private static final float ELBOW_OUT = 0.74f;
	private static final float ELBOW_DOWN = 0.6f;
	private static final float ELBOW_FORWARD = 0.95f;
	/** Degrees each arm turns in towards the middle, so the two barrels lead the eye to the sight. */
	private static final float TOE_IN = 4.0f;

	private static MechModel model;
	private static final MechRenderState STATE = new MechRenderState();

	private MechFirstPerson() {
	}

	/** Draws the arms into the first-person hand pass (in place of the pilot's hands; see MechHandsMixin). */
	public static void submit(Mech mech, float partialTicks, PoseStack poseStack, SubmitNodeCollector collector) {
		Minecraft minecraft = Minecraft.getInstance();
		Player pilot = minecraft.player;
		if (pilot == null) {
			return;
		}
		if (model == null) {
			model = new MechModel(minecraft.getEntityModels().bakeLayer(MechModel.LAYER));
		}
		MechRenderState state = STATE;
		state.look = mech.look();
		state.twist = 0.0f;
		state.armPitch = 0.0f;
		state.hatch = 0.0f;
		state.jetFlame = 0.0f;
		state.powered = mech.hasPower();
		state.walkAnimationPos = 0.0f;
		state.walkAnimationSpeed = 0.0f;
		state.ageInTicks = mech.tickCount + partialTicks;
		for (int side = 0; side < 2; side++) {
			state.recoil[side] = mech.recoil(side, partialTicks);
			state.spin[side] = mech.barrelSpin(side, partialTicks);
			int arm = mech.armState(side);
			state.grip[side] = mech.weapon(side) != MechWeapon.POWER_FIST ? 0.0f : mech.holding(side) ? 0.6f
					: MechArm.status(arm) == MechArm.READY && MechArm.active(arm) ? MechArm.progress(arm) : 0.0f;
		}
		model.setupAnim(state);

		Vec3 eye = MechCrew.viewport(mech, partialTicks);
		BlockPos at = BlockPos.containing(eye);
		int light = LightCoordsUtil.pack(mech.level().getBrightness(LightLayer.BLOCK, at), mech.level().getBrightness(LightLayer.SKY, at));
		// The arms point along the torso and the arms' pitch; the view along the pilot's look. The difference is how far off
		// straight ahead they sit on screen.
		float yawOff = Mth.wrapDegrees(mech.torsoYaw(partialTicks) - pilot.getViewYRot(partialTicks));
		float pitchOff = mech.armPitch(partialTicks) - pilot.getViewXRot(partialTicks);
		// The walk: a drop at each footfall that eases back up, and a sway onto the planted foot.
		float phase = mech.walkAnimation.position(partialTicks) * Mech.WALK_CYCLE;
		float stride = Math.min(1.0f, mech.walkAnimation.speed(partialTicks) * 1.6f);
		float since = phase / Mth.PI - Mth.floor(phase / Mth.PI);
		float drop = (1.0f - since) * (1.0f - since) * (1.0f - since) * 0.05f * stride;
		float sway = -Mth.cos(phase) * 0.02f * stride;
		for (int side = 0; side < 2; side++) {
			if (!MechLoadout.has(state.look, side == MechArm.LEFT ? MechLoadout.LEFT_ARM : MechLoadout.RIGHT_ARM)) {
				continue;
			}
			float right = side == MechArm.RIGHT ? 1.0f : -1.0f;
			MechWeapon weapon = mech.weapon(side);
			int packed = mech.armState(side);
			float kick = state.recoil[side];
			if (weapon == MechWeapon.ROTARY_CANNON && MechArm.active(packed) && MechArm.progress(packed) >= 0.99f) {
				// Twenty rounds a second: a fast chatter rather than one kick.
				kick = 0.25f + 0.25f * Mth.sin(state.ageInTicks * 9.0f);
			}
			poseStack.pushPose();
			poseStack.translate(right * ELBOW_OUT + sway, -ELBOW_DOWN - drop, -ELBOW_FORWARD + kick * 0.07f);
			poseStack.rotateDegrees(Axis.YP, -yawOff + right * TOE_IN);
			poseStack.rotateDegrees(Axis.XP, -pitchOff - kick * 2.5f);
			// Model space has y down and the walker's left at +x: flip both, as the entity renderer does.
			poseStack.scale(-SCALE, -SCALE, SCALE);
			ModelPart forearm = model.firstPersonForearm(side);
			collector.submitModelPart(forearm, poseStack, BASE, light, OverlayTexture.NO_OVERLAY, null);
			poseStack.popPose();
		}
	}
}
