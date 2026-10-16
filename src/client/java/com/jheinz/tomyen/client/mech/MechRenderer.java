package com.jheinz.tomyen.client.mech;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.mech.Mech;
import com.jheinz.tomyen.mech.MechArm;
import com.jheinz.tomyen.mech.MechWeapon;

/**
 * Draws a Bulwark Walker, with its viewport, visor and power plant window glowing (dim when its core is dead) and
 * its jet flames full-bright. It doesn't tip over when it dies (it burns where it stands), it stays drawn while its
 * head and pauldrons are on screen (they stand above its collision box), and its own pilot doesn't see it from the
 * cockpit.
 */
public class MechRenderer extends MobRenderer<Mech, MechRenderState, MechModel> {
	public static final Identifier TEXTURE = MyMod.id("textures/entity/mech/mech.png");
	public static final Identifier GLOW_TEXTURE = MyMod.id("textures/entity/mech/mech_glow.png");
	public static final RenderType GLOW = RenderTypes.eyes(GLOW_TEXTURE);
	/** The glow's tint with the core lit, and dead. */
	public static final int LIT = 0xFFFFFFFF;
	public static final int DARK = 0xFF2A2A2A;

	public MechRenderer(EntityRendererProvider.Context context) {
		super(context, new MechModel(context.bakeLayer(MechModel.LAYER)), 1.6f);
		addLayer(new GlowLayer(this));
	}

	@Override
	public Identifier getTextureLocation(MechRenderState state) {
		return TEXTURE;
	}

	@Override
	public MechRenderState createRenderState() {
		return new MechRenderState();
	}

	@Override
	public boolean shouldRender(Mech mech, Frustum culler, double camX, double camY, double camZ, float partialTicks) {
		if (mech == MechClient.cockpitMech()) {
			return false;
		}
		return super.shouldRender(mech, culler, camX, camY, camZ, partialTicks);
	}

	@Override
	protected AABB getBoundingBoxForCulling(Mech mech, float partialTicks) {
		return super.getBoundingBoxForCulling(mech, partialTicks).inflate(1.5, 1.0, 1.5);
	}

	@Override
	protected float getFlipDegrees() {
		return 0.0f;
	}

	@Override
	public void extractRenderState(Mech mech, MechRenderState state, float partialTicks) {
		super.extractRenderState(mech, state, partialTicks);
		state.look = mech.look();
		state.twist = mech.twist(partialTicks);
		state.armPitch = mech.armPitch(partialTicks);
		state.hatch = mech.hatchOpen(partialTicks);
		state.jetFlame = mech.jetFlame(partialTicks);
		state.powered = mech.hasPower();
		for (int side = 0; side < 2; side++) {
			state.recoil[side] = mech.recoil(side, partialTicks);
			state.spin[side] = mech.barrelSpin(side, partialTicks);
			int arm = mech.armState(side);
			boolean fist = mech.weapon(side) == MechWeapon.POWER_FIST;
			state.grip[side] = !fist ? 0.0f : mech.holding(side) ? 0.6f
					: MechArm.status(arm) == MechArm.READY && MechArm.active(arm) ? MechArm.progress(arm) : 0.0f;
		}
	}

	/** The glowing parts, drawn again full-bright over the rest. */
	static final class GlowLayer extends RenderLayer<MechRenderState, MechModel> {
		GlowLayer(RenderLayerParent<MechRenderState, MechModel> parent) {
			super(parent);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, MechRenderState state, float yRot, float xRot) {
			collector.order(1).submitModel(getParentModel(), state, poseStack, GLOW, lightCoords, OverlayTexture.NO_OVERLAY,
					state.powered ? LIT : DARK, null, state.outlineColor);
		}
	}
}
