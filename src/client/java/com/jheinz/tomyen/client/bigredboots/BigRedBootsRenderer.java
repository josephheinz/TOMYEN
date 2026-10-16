package com.jheinz.tomyen.client.bigredboots;

import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.bigredboots.BigRedBoots;

/**
 * Draws worn Big Red Boots: a Fabric armor renderer for the feet (the boots have no equipment asset, so vanilla's
 * armor layer draws nothing for them). The {@link BigRedBootsModel} copies the wearer's model's pose part by part, so
 * the boots swing with the legs through every walk, crouch and ride, on players, armor stands and humanoid mobs alike.
 * Socketed in an exoskeleton they're drawn the same way: the armor layer is handed the socketed feet piece
 * (HumanoidMobRendererExoSocketsMixin), and this renderer takes it from there. Enchanted, they glint like armor.
 */
public final class BigRedBootsRenderer {
	private static final Identifier TEXTURE = MyMod.id("textures/entity/big_red_boots.png");
	private static final RenderType RENDER_TYPE = RenderTypes.armorCutoutNoCull(TEXTURE);
	private static final RenderType GLINT = RenderTypes.armorCutoutNoCullGlint(TEXTURE);

	private BigRedBootsRenderer() {
	}

	public static void initialize() {
		ModelLayerRegistry.registerModelLayer(BigRedBootsModel.LAYER, BigRedBootsModel::createLayer);
		ArmorRenderer.register(context -> {
			BigRedBootsModel model = new BigRedBootsModel(context.bakeLayer(BigRedBootsModel.LAYER));
			return (poseStack, collector, stack, state, slot, light, wearerModel) -> {
				// A baby's legs are shrunk by its own mesh, which the boots wouldn't follow.
				if (slot != EquipmentSlot.FEET || state.isBaby) {
					return;
				}
				ArmorRenderer.submitTransformCopyingModel(wearerModel, state, model, state, false, collector, poseStack,
						stack.hasFoil() ? GLINT : RENDER_TYPE, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
			};
		}, BigRedBoots.BOOTS);
	}
}
