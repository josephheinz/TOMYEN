package com.jheinz.mymod.client.uranium;

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
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.uranium.CentrifugeBlockEntity;

/**
 * The Centrifuge's rotor, sitting in the open top of the housing (the block model): a hub and a cross of two arms
 * with the sample tubes at their ends, spinning about the hub. UVs match tools/gen_ores_tu.py.
 */
public class CentrifugeRenderer implements BlockEntityRenderer<CentrifugeBlockEntity, CentrifugeRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(MyMod.id("centrifuge"), "main");
	private static final Identifier TEXTURE = MyMod.id("textures/entity/uranium/centrifuge_rotor.png");
	private static final RenderType RENDER_TYPE = RenderTypes.entityCutout(TEXTURE);

	public static class State extends BlockEntityRenderState {
		float angle;
	}

	private final Rotor rotor;

	public CentrifugeRenderer(BlockEntityRendererProvider.Context context) {
		rotor = new Rotor(context.bakeLayer(LAYER));
	}

	/** Block space in pixels, y up, about the block's vertical centre line: the housing's top is at 12. */
	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("rotor", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 3.0f, 2.0f)
				.texOffs(0, 6).addBox(-6.0f, 1.0f, -1.0f, 12.0f, 1.0f, 2.0f)
				.texOffs(0, 10).addBox(-1.0f, 1.0f, -6.0f, 2.0f, 1.0f, 12.0f),
				PartPose.offset(0.0f, 12.0f, 0.0f));
		return LayerDefinition.create(mesh, 32, 32);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(CentrifugeBlockEntity centrifuge, State state, float partialTicks, Vec3 cameraPosition,
			ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(centrifuge, state, partialTicks, cameraPosition, breakProgress);
		state.angle = centrifuge.rotor(partialTicks);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(0.5f, 0.0f, 0.5f);
		poseStack.rotate(Axis.YP.rotationDegrees(state.angle));
		collector.submitModel(rotor, state, poseStack, RENDER_TYPE, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}

	private static class Rotor extends Model<State> {
		Rotor(ModelPart root) {
			super(root, RenderTypes::entityCutout);
		}
	}
}
