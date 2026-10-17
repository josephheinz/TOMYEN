package com.jheinz.tomyen.client.fusion;

import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.client.bigredboots.BigRedBootsModel;
import com.jheinz.tomyen.fusion.Fusions;

/**
 * Draws worn Rocket Boots: the Big Red Boots they were (their model and texture), with a soda-bottle rocket strapped
 * up the back of each, nozzle down ({@link RocketNozzleModel}). Both copy the wearer's leg poses.
 */
public final class RocketBootsRenderer {
	private static final Identifier BOOTS = MyMod.id("textures/entity/big_red_boots.png");
	private static final Identifier ROCKETS = MyMod.id("textures/entity/rocket_boots_rockets.png");

	private RocketBootsRenderer() {
	}

	static void initialize() {
		ModelLayerRegistry.registerModelLayer(RocketNozzleModel.LAYER, RocketNozzleModel::createLayer);
		ArmorRenderer.register(context -> {
			BigRedBootsModel boots = new BigRedBootsModel(context.bakeLayer(BigRedBootsModel.LAYER));
			RocketNozzleModel rockets = new RocketNozzleModel(context.bakeLayer(RocketNozzleModel.LAYER));
			return (poseStack, collector, stack, state, slot, light, wearerModel) -> {
				if (slot != EquipmentSlot.FEET || state.isBaby) {
					return;
				}
				boolean foil = stack.hasFoil();
				RenderType bootType = foil ? RenderTypes.armorCutoutNoCullGlint(BOOTS) : RenderTypes.armorCutoutNoCull(BOOTS);
				RenderType rocketType = foil ? RenderTypes.armorCutoutNoCullGlint(ROCKETS) : RenderTypes.armorCutoutNoCull(ROCKETS);
				ArmorRenderer.submitTransformCopyingModel(wearerModel, state, boots, state, false, collector, poseStack, bootType, light,
						OverlayTexture.NO_OVERLAY, state.outlineColor);
				ArmorRenderer.submitTransformCopyingModel(wearerModel, state, rockets, state, false, collector, poseStack, rocketType, light,
						OverlayTexture.NO_OVERLAY, state.outlineColor);
			};
		}, Fusions.ROCKET_BOOTS);
	}
}
