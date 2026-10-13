package com.jheinz.mymod.client.hogfire;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.animal.pig.PigModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.PigRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.hogfire.Hogfire;

/**
 * The Hogfire's gear, in the pig model's space (its back is at y 10, its rump at z 8): the harness's two belly bands,
 * spine strap, seat pad and steel flank plates; a wing out of each side, a spar along the leading edge and duralumin
 * skin behind it; and the rocket engine strapped on behind the seat, a fuel pod either side, its bell nozzle past the
 * pig's tail. With the engine lit, a flame (drawn full-bright) streams from the nozzle, longer with more throttle.
 * On the ground the wings fold up a little and twitch as it walks. UVs match tools/gen_industry.py. A gun mount is
 * its own item model, drawn on the shoulders.
 */
public class HogfireGearLayer extends RenderLayer<PigRenderState, PigModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(MyMod.id("hogfire"), "gear");
	private static final Identifier TEXTURE = MyMod.id("textures/entity/hogfire/gear.png");
	private static final RenderType BASE = RenderTypes.entityCutout(TEXTURE);
	private static final RenderType GLOW = RenderTypes.eyes(TEXTURE);
	// The gun mount: its bore and muzzle in its item model (tools/gen_minigun.py), and where they go on the pig.
	private static final float GUN_BORE_Y = 9.0f;
	private static final float GUN_MUZZLE_Z = -15.2f;
	private static final float MOUNT_SCALE = 0.5f;
	private static final float MOUNT_BORE_Y = 6.0f;
	private static final float MOUNT_MUZZLE_Z = -16.0f;

	private final Gear gear;
	private final Gear flame;

	public HogfireGearLayer(RenderLayerParent<PigRenderState, PigModel> renderer, EntityRendererProvider.Context context) {
		super(renderer);
		gear = new Gear(context.bakeLayer(LAYER), false);
		flame = new Gear(context.bakeLayer(LAYER), true);
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		CubeDeformation snug = new CubeDeformation(0.4f);
		root.addOrReplaceChild("harness", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-5.0f, 10.0f, -4.5f, 10.0f, 8.0f, 1.0f, snug)
				.texOffs(0, 0).addBox(-5.0f, 10.0f, 3.0f, 10.0f, 8.0f, 1.0f, snug)
				.texOffs(22, 0).addBox(-1.5f, 9.4f, -4.5f, 3.0f, 1.0f, 9.0f)
				.texOffs(0, 10).addBox(-4.0f, 9.0f, -3.5f, 8.0f, 1.0f, 6.0f)
				.texOffs(46, 0).addBox(-5.9f, 12.0f, -2.0f, 1.0f, 4.0f, 4.0f)
				.texOffs(46, 0).addBox(4.9f, 12.0f, -2.0f, 1.0f, 4.0f, 4.0f),
				PartPose.ZERO);
		root.addOrReplaceChild("left_wing", CubeListBuilder.create()
				.texOffs(0, 17).addBox(0.0f, -0.5f, -0.5f, 16.0f, 1.0f, 1.0f)
				.texOffs(0, 19).addBox(0.0f, -0.4f, 0.5f, 15.0f, 1.0f, 8.0f),
				PartPose.offset(5.0f, 11.5f, -1.0f));
		root.addOrReplaceChild("right_wing", CubeListBuilder.create()
				.texOffs(0, 17).mirror().addBox(-16.0f, -0.5f, -0.5f, 16.0f, 1.0f, 1.0f)
				.texOffs(0, 19).mirror().addBox(-15.0f, -0.4f, 0.5f, 15.0f, 1.0f, 8.0f),
				PartPose.offset(-5.0f, 11.5f, -1.0f));
		root.addOrReplaceChild("engine", CubeListBuilder.create()
				.texOffs(0, 28).addBox(-2.5f, 4.0f, 2.0f, 5.0f, 5.0f, 9.0f)
				.texOffs(28, 28).addBox(-1.5f, 3.0f, 3.0f, 3.0f, 1.0f, 3.0f)
				.texOffs(44, 34).addBox(-4.5f, 6.5f, 3.0f, 2.0f, 2.0f, 6.0f)
				.texOffs(44, 34).mirror().addBox(2.5f, 6.5f, 3.0f, 2.0f, 2.0f, 6.0f).mirror(false)
				.texOffs(40, 28).addBox(-2.0f, 4.5f, 11.0f, 4.0f, 4.0f, 2.0f)
				.texOffs(28, 34).addBox(-3.0f, 3.5f, 13.0f, 6.0f, 6.0f, 2.0f),
				PartPose.ZERO);
		// The flame, pivoting at the nozzle's mouth so it can be stretched backwards.
		root.addOrReplaceChild("flame", CubeListBuilder.create()
				.texOffs(0, 42).addBox(-1.5f, -1.5f, 0.0f, 3.0f, 3.0f, 5.0f)
				.texOffs(16, 42).addBox(-1.0f, -1.0f, 5.0f, 2.0f, 2.0f, 4.0f),
				PartPose.offset(0.0f, 6.5f, 15.0f));
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, PigRenderState state, float yRot, float xRot) {
		if (!(state instanceof HogfireRenderState hogState) || hogState.gear < Hogfire.HARNESS) {
			return;
		}
		collector.submitModel(gear, hogState, poseStack, BASE, light, LivingEntityRenderer.getOverlayCoords(state, 0.0f), state.outlineColor);
		if (hogState.engineRunning) {
			collector.submitModel(flame, hogState, poseStack, GLOW, light, OverlayTexture.NO_OVERLAY, 0);
		}
		if (hogState.gunMount) {
			submitGunMount(poseStack, collector, light, hogState);
		}
	}

	/**
	 * The gun mount, its saddle plate on the shoulders in front of the seat and the barrels running out over the head.
	 * Model space here is y down with the nose toward -z; the item model is y up, drawn about its centre (8, 8, 8). Half a
	 * turn round z stands it the right way up with its muzzle still forward; then at half size its bore goes to y 6 and
	 * its muzzle to z -16, just past the snout, where Hogfire fires it from (MOUNT_MUZZLE_UP and _FORWARD).
	 */
	private static void submitGunMount(PoseStack poseStack, SubmitNodeCollector collector, int light, HogfireRenderState state) {
		poseStack.pushPose();
		poseStack.translate(0.0f, (MOUNT_BORE_Y + (GUN_BORE_Y - 8.0f) * MOUNT_SCALE) / 16.0f,
				(MOUNT_MUZZLE_Z - (GUN_MUZZLE_Z - 8.0f) * MOUNT_SCALE) / 16.0f);
		poseStack.rotateDegrees(Axis.ZP, 180.0f);
		poseStack.scale(MOUNT_SCALE, MOUNT_SCALE, MOUNT_SCALE);
		state.mountedGun.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
	}

	/** The gear (or, for the glow pass, only the flame), posed for one pig. */
	private static class Gear extends Model<HogfireRenderState> {
		private final boolean flamePass;
		private final ModelPart harness;
		private final ModelPart leftWing;
		private final ModelPart rightWing;
		private final ModelPart engine;
		private final ModelPart flame;

		Gear(ModelPart root, boolean flamePass) {
			super(root, RenderTypes::entityCutout);
			this.flamePass = flamePass;
			harness = root.getChild("harness");
			leftWing = root.getChild("left_wing");
			rightWing = root.getChild("right_wing");
			engine = root.getChild("engine");
			flame = root.getChild("flame");
		}

		@Override
		public void setupAnim(HogfireRenderState state) {
			super.setupAnim(state);
			harness.visible = !flamePass;
			leftWing.visible = rightWing.visible = !flamePass && state.gear >= Hogfire.WINGS;
			engine.visible = !flamePass && state.gear >= Hogfire.ENGINE;
			flame.visible = flamePass && state.engineRunning;

			// Spread in flight with a little dihedral; folded half up on the ground, twitching as it trots.
			float fold = state.flying ? 0.08f : 0.55f + Mth.sin(state.walkAnimationPos * 0.8f) * 0.15f * state.walkAnimationSpeed;
			leftWing.zRot = -fold;
			rightWing.zRot = fold;

			float flicker = 0.85f + 0.15f * Mth.sin(state.ageInTicks * 2.7f);
			flame.zScale = (0.4f + state.throttle * 0.9f + (state.afterburner ? 0.9f : 0.0f)) * flicker;
			flame.xScale = flame.yScale = state.afterburner ? 1.3f : 1.0f;
		}
	}
}
