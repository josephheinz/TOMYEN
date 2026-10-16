package com.jheinz.tomyen.client.dragonscale;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.dragonscale.Dragonscales;
import com.jheinz.tomyen.exoskeleton.ExoSockets;

/**
 * The dragonscale helmet's horns, which the flat armor texture can't give it: two ridged horns rising from the top
 * corners of the helmet and sweeping back and out, four cubes in all. They follow the wearer's head, worn or with the helmet socketed in an exoskeleton. Not on babies,
 * whose armor is a different shape.
 */
public class DragonHornsLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends RenderLayer<S, M> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(MyMod.id("dragonscale_horns"), "main");
	private static final Identifier TEXTURE = MyMod.id("textures/entity/equipment/dragonscale_horns.png");
	private static final RenderType RENDER_TYPE = RenderTypes.armorCutoutNoCull(TEXTURE);

	private final ModelPart horns;

	public DragonHornsLayer(RenderLayerParent<S, M> renderer, EntityModelSet models) {
		super(renderer);
		this.horns = models.bakeLayer(LAYER);
	}

	/**
	 * In head space: the head is x -4..4, y -8..0 (up is -y), z -4..4 (the face at -z), and the helmet stands a pixel
	 * out from it. Each horn is a thick base and a thinner tip, each bent further back.
	 */
	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		for (int sign = -1; sign <= 1; sign += 2) {
			PartDefinition base = root.addOrReplaceChild(sign < 0 ? "right_horn" : "left_horn",
					CubeListBuilder.create().texOffs(0, 0).addBox(-1.0f, -4.0f, -1.0f, 2.0f, 4.0f, 2.0f),
					PartPose.offsetAndRotation(sign * 3.6f, -8.2f, 0.5f, -0.75f, 0.0f, sign * 0.35f));
			base.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(0, 6).addBox(-0.5f, -3.0f, -0.5f, 1.0f, 3.0f, 1.0f),
					PartPose.offsetAndRotation(0.0f, -3.7f, 0.0f, -0.65f, 0.0f, sign * 0.15f));
		}
		return LayerDefinition.create(mesh, 16, 16);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, S state, float yRot, float xRot) {
		if (state.isBaby || !getParentModel().head.visible || !wearsHelmet(state)) {
			return;
		}
		poseStack.pushPose();
		getParentModel().head.translateAndRotate(poseStack);
		collector.submitModelPart(horns, poseStack, RENDER_TYPE, light, OverlayTexture.NO_OVERLAY, null, -1, state.outlineColor);
		poseStack.popPose();
	}

	private static boolean wearsHelmet(HumanoidRenderState state) {
		return state.headEquipment.is(Dragonscales.HELMET) || ExoSockets.armor(state.chestEquipment, EquipmentSlot.HEAD).is(Dragonscales.HELMET);
	}
}
