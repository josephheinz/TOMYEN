package com.jheinz.tomyen.client.industry;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.industry.PumpJackBlock;
import com.jheinz.tomyen.industry.PumpJackBlockEntity;

/**
 * The nodding donkey on a Pump Jack's skid (the skid itself is the block model): an A-frame Samson post carrying a
 * walking beam, its horse head over the wellhead at the front, the polished rod down into the wellhead, and at the
 * back a gearbox turning cranks with counterweights, which drive the beam through the pitman arms. Pumping, the
 * cranks turn once every three seconds and the beam rocks with them. The rig runs a block and a half long, out
 * behind the block it stands on. UVs match tools/gen_industry.py.
 */
public class PumpJackRenderer implements BlockEntityRenderer<PumpJackBlockEntity, PumpJackRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(MyMod.id("pump_jack"), "main");
	private static final Identifier TEXTURE = MyMod.id("textures/entity/industry/pump_jack.png");
	private static final RenderType RENDER_TYPE = RenderTypes.entityCutout(TEXTURE);

	// Model space, as for an entity: pixels, y down from the top of the rig with the ground at 24, front at -z.
	private static final float PIVOT_Y = -6.0f;
	private static final float PIVOT_Z = 8.0f;
	private static final float BEAM_HALF = 12.0f;
	private static final float CRANK_Y = 14.0f;
	private static final float CRANK_Z = 17.0f;
	private static final float CRANK_RADIUS = 4.0f;
	private static final float WELLHEAD_TOP = 16.0f;
	/** How far the beam rocks either way, in radians. */
	private static final float BEAM_SWING = 0.3f;
	private static final float ARM_LENGTH = 16.0f;

	public static class State extends BlockEntityRenderState {
		Direction facing = Direction.NORTH;
		float crank;
	}

	private final Rig rig;

	public PumpJackRenderer(BlockEntityRendererProvider.Context context) {
		rig = new Rig(context.bakeLayer(LAYER));
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		// The Samson post: two splayed legs up to the beam's pivot.
		root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 0).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 26.0f, 2.0f),
				PartPose.offsetAndRotation(0.0f, PIVOT_Y, PIVOT_Z, 0.0f, 0.0f, -0.16f));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 0).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 26.0f, 2.0f),
				PartPose.offsetAndRotation(0.0f, PIVOT_Y, PIVOT_Z, 0.0f, 0.0f, 0.16f));
		// The walking beam, the horse head at its front and the equaliser bar the pitmans hang from at its back.
		root.addOrReplaceChild("beam", CubeListBuilder.create()
				.texOffs(8, 0).addBox(-1.0f, -1.5f, -BEAM_HALF, 2.0f, 3.0f, BEAM_HALF * 2.0f)
				.texOffs(60, 0).addBox(-2.0f, -2.0f, -15.0f, 4.0f, 11.0f, 3.0f)
				.texOffs(74, 0).addBox(-2.0f, -3.0f, -14.0f, 4.0f, 1.0f, 2.0f)
				.texOffs(86, 0).addBox(-3.5f, -0.5f, 11.0f, 7.0f, 1.0f, 2.0f),
				PartPose.offset(0.0f, PIVOT_Y, PIVOT_Z));
		root.addOrReplaceChild("pitmans", CubeListBuilder.create()
				.texOffs(104, 0).addBox(-3.0f, 0.0f, -0.5f, 1.0f, ARM_LENGTH, 1.0f)
				.texOffs(104, 0).addBox(2.0f, 0.0f, -0.5f, 1.0f, ARM_LENGTH, 1.0f),
				PartPose.ZERO);
		// The cranks: an arm each side, the pin end up at -y and the counterweight at +y.
		root.addOrReplaceChild("cranks", CubeListBuilder.create()
				.texOffs(108, 0).addBox(-4.0f, -5.0f, -1.0f, 1.0f, 12.0f, 2.0f)
				.texOffs(108, 0).addBox(3.0f, -5.0f, -1.0f, 1.0f, 12.0f, 2.0f)
				.texOffs(60, 14).addBox(-5.5f, 2.0f, -3.0f, 3.0f, 5.0f, 6.0f)
				.texOffs(60, 14).addBox(2.5f, 2.0f, -3.0f, 3.0f, 5.0f, 6.0f)
				.texOffs(60, 30).addBox(-4.0f, -0.5f, -0.5f, 8.0f, 1.0f, 1.0f),
				PartPose.offset(0.0f, CRANK_Y, CRANK_Z));
		root.addOrReplaceChild("rod", CubeListBuilder.create().texOffs(114, 0).addBox(-0.5f, 0.0f, -0.5f, 1.0f, ARM_LENGTH, 1.0f),
				PartPose.ZERO);
		// The fixed works: the gearbox and motor on the skid's tail behind the block, and the wellhead in front.
		root.addOrReplaceChild("base", CubeListBuilder.create()
				.texOffs(78, 14).addBox(-3.0f, 14.0f, 14.0f, 6.0f, 6.0f, 6.0f)
				.texOffs(102, 17).addBox(-2.0f, 17.0f, 20.0f, 4.0f, 3.0f, 2.0f)
				.texOffs(16, 30).addBox(-4.0f, 20.0f, 8.0f, 8.0f, 4.0f, 14.0f)
				.texOffs(0, 30).addBox(-2.0f, WELLHEAD_TOP, -8.0f, 4.0f, 4.0f, 4.0f),
				PartPose.ZERO);
		return LayerDefinition.create(mesh, 128, 64);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(PumpJackBlockEntity jack, State state, float partialTicks, Vec3 cameraPosition,
			ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(jack, state, partialTicks, cameraPosition, breakProgress);
		state.facing = jack.getBlockState().getValue(PumpJackBlock.FACING);
		state.crank = jack.crank(partialTicks);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(0.5f, 0.0f, 0.5f);
		poseStack.rotate(Axis.YP.rotationDegrees(180.0f - state.facing.toYRot()));
		poseStack.scale(-1.0f, -1.0f, 1.0f);
		poseStack.translate(0.0f, -1.5f, 0.0f);
		collector.submitModel(rig, state, poseStack, RENDER_TYPE, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}

	/** The beam reaches a block past the front, the gearbox one behind. */
	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	/** Poses the moving parts from the crank angle. */
	private static class Rig extends Model<State> {
		private final ModelPart beam;
		private final ModelPart pitmans;
		private final ModelPart cranks;
		private final ModelPart rod;

		Rig(ModelPart root) {
			super(root, RenderTypes::entityCutout);
			beam = root.getChild("beam");
			pitmans = root.getChild("pitmans");
			cranks = root.getChild("cranks");
			rod = root.getChild("rod");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			float crank = state.crank;
			// The beam's back end is lowest when the crank pin is at the bottom.
			float swing = BEAM_SWING * Mth.cos(crank);
			beam.xRot = swing;
			cranks.xRot = crank;

			// The pitmans run from the beam's back end down to the crank pins.
			float sin = Mth.sin(swing), cos = Mth.cos(swing);
			float backY = PIVOT_Y - BEAM_HALF * sin, backZ = PIVOT_Z + BEAM_HALF * cos;
			float pinY = CRANK_Y - CRANK_RADIUS * Mth.cos(crank), pinZ = CRANK_Z - CRANK_RADIUS * Mth.sin(crank);
			aim(pitmans, backY, backZ, pinY, pinZ);

			// The polished rod hangs from the bottom of the horse head straight down into the wellhead.
			float headY = PIVOT_Y + 9.0f * cos + 14.5f * sin, headZ = PIVOT_Z + 9.0f * sin - 14.5f * cos;
			rod.setPos(0.0f, headY, headZ);
			rod.yScale = Math.max(0.1f, (WELLHEAD_TOP - headY) / ARM_LENGTH);
		}

		/** Hangs an arm from one point and stretches it to reach another. */
		private static void aim(ModelPart arm, float fromY, float fromZ, float toY, float toZ) {
			float dy = toY - fromY, dz = toZ - fromZ;
			arm.setPos(0.0f, fromY, fromZ);
			arm.xRot = (float) Mth.atan2(dz, dy);
			arm.yScale = Mth.sqrt(dy * dy + dz * dz) / ARM_LENGTH;
		}
	}
}
