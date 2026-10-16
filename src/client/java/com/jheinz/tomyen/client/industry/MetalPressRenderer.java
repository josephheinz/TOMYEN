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
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.industry.MetalPressBlockEntity;

/**
 * The Metal Press's ram, between the frame's pillars (the block model): a heavy die on a piston rod from the
 * crosshead, which slams down on a stroke and lifts again. The ingots waiting lie on the anvil bed, the stamped
 * plates beside them. UVs match tools/gen_industry.py.
 */
public class MetalPressRenderer implements BlockEntityRenderer<MetalPressBlockEntity, MetalPressRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(MyMod.id("metal_press"), "main");
	private static final Identifier TEXTURE = MyMod.id("textures/entity/industry/metal_press_ram.png");
	private static final RenderType RENDER_TYPE = RenderTypes.entityCutout(TEXTURE);
	/** Pixels the ram travels from the top of its stroke down onto the bed. */
	private static final float STROKE = 3.5f;

	public static class State extends BlockEntityRenderState {
		float drop;
		final ItemStackRenderState bed = new ItemStackRenderState();
		final ItemStackRenderState tray = new ItemStackRenderState();
		int bedCount;
		int trayCount;
	}

	private final Ram ram;
	private final ItemModelResolver itemModelResolver;

	public MetalPressRenderer(BlockEntityRendererProvider.Context context) {
		ram = new Ram(context.bakeLayer(LAYER));
		itemModelResolver = context.itemModelResolver();
	}

	/**
	 * Block space in pixels, y up: at rest the die's face is 3.5 above the bed (whose top is at 6), and its rod runs
	 * up through the crosshead into the cylinder on top.
	 */
	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("ram", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-5.0f, 0.0f, -5.0f, 10.0f, 3.0f, 10.0f)
				.texOffs(40, 0).addBox(-1.0f, 3.0f, -1.0f, 2.0f, 6.0f, 2.0f),
				PartPose.offset(0.0f, 6.0f + STROKE, 0.0f));
		return LayerDefinition.create(mesh, 64, 32);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(MetalPressBlockEntity press, State state, float partialTicks, Vec3 cameraPosition,
			ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(press, state, partialTicks, cameraPosition, breakProgress);
		state.drop = press.ramDrop(partialTicks);
		int seed = (int) press.getBlockPos().asLong();
		itemModelResolver.updateForTopItem(state.bed, press.bed(), ItemDisplayContext.FIXED, press.getLevel(), null, seed);
		itemModelResolver.updateForTopItem(state.tray, press.tray(), ItemDisplayContext.FIXED, press.getLevel(), null, seed + 1);
		state.bedCount = press.bed().getCount();
		state.trayCount = press.tray().getCount();
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(0.5f, 0.0f, 0.5f);
		collector.submitModel(ram, state, poseStack, RENDER_TYPE, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
		// Ingots in a little stack in the middle of the bed, plates off to one side.
		submitPile(state.bed, Math.min(4, (state.bedCount + 15) / 16), 0.0f, poseStack, collector, state.lightCoords);
		submitPile(state.tray, Math.min(4, (state.trayCount + 15) / 16), 0.27f, poseStack, collector, state.lightCoords);
		poseStack.popPose();
	}

	private static void submitPile(ItemStackRenderState item, int layers, float offset, PoseStack poseStack, SubmitNodeCollector collector,
			int light) {
		if (item.isEmpty()) {
			return;
		}
		for (int i = 0; i < layers; i++) {
			poseStack.pushPose();
			poseStack.translate(offset, 6.1f / 16.0f + i * 0.02f, -offset * 0.5f);
			poseStack.rotate(Axis.XP.rotationDegrees(90.0f));
			poseStack.rotate(Axis.ZP.rotationDegrees(i * 17.0f));
			poseStack.scale(0.3f, 0.3f, 0.3f);
			item.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
		}
	}

	private static class Ram extends Model<State> {
		private final ModelPart ram;

		Ram(ModelPart root) {
			super(root, RenderTypes::entityCutout);
			ram = root.getChild("ram");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			ram.y -= STROKE * state.drop;
		}
	}
}
