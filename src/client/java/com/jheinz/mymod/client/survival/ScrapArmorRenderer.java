package com.jheinz.mymod.client.survival;

import java.util.Map;

import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.survival.Survival;

/**
 * Draws worn Scrap armor as its 3D junk model ({@link ScrapArmorModel}) instead of a flat armor layer: a Fabric armor
 * renderer for all four pieces, which also stops vanilla's armor layer drawing them (so they show no trims either).
 * Each piece's layer copies the wearer's model's pose part by part, so it moves with every walk, swing, crouch and
 * ride, on players, armor stands and humanoid mobs alike; slim-armed players get the chestplate with narrower arm
 * pieces. Socketed in an exoskeleton the pieces are drawn the same way: the armor layer is handed the socketed pieces
 * (the client ExoSockets mixins) and this renderer takes it from there. Enchanted, they glint like armor.
 */
public final class ScrapArmorRenderer {
	private static final Identifier TEXTURE = MyMod.id("textures/entity/scrap_armor.png");
	private static final RenderType RENDER_TYPE = RenderTypes.armorCutoutNoCull(TEXTURE);
	private static final RenderType GLINT = RenderTypes.armorCutoutNoCullGlint(TEXTURE);

	private ScrapArmorRenderer() {
	}

	static void initialize() {
		ModelLayerRegistry.registerModelLayer(ScrapArmorModel.HELMET, ScrapArmorModel::createHelmet);
		ModelLayerRegistry.registerModelLayer(ScrapArmorModel.CHESTPLATE, ScrapArmorModel::createChestplate);
		ModelLayerRegistry.registerModelLayer(ScrapArmorModel.CHESTPLATE_SLIM, ScrapArmorModel::createChestplateSlim);
		ModelLayerRegistry.registerModelLayer(ScrapArmorModel.LEGGINGS, ScrapArmorModel::createLeggings);
		ModelLayerRegistry.registerModelLayer(ScrapArmorModel.BOOTS, ScrapArmorModel::createBoots);
		ArmorRenderer.register(context -> {
			Map<Item, ScrapArmorModel> models = Map.of(
					Survival.SCRAP_HELMET, new ScrapArmorModel(context.bakeLayer(ScrapArmorModel.HELMET)),
					Survival.SCRAP_CHESTPLATE, new ScrapArmorModel(context.bakeLayer(ScrapArmorModel.CHESTPLATE)),
					Survival.SCRAP_LEGGINGS, new ScrapArmorModel(context.bakeLayer(ScrapArmorModel.LEGGINGS)),
					Survival.SCRAP_BOOTS, new ScrapArmorModel(context.bakeLayer(ScrapArmorModel.BOOTS)));
			ScrapArmorModel slimChestplate = new ScrapArmorModel(context.bakeLayer(ScrapArmorModel.CHESTPLATE_SLIM));
			return (poseStack, collector, stack, state, slot, light, wearerModel) -> {
				// A baby's parts are shrunk by its own mesh, which the junk wouldn't follow; and a piece is only drawn
				// in the slot it's made for.
				if (state.isBaby || !wornIn(stack, slot)) {
					return;
				}
				ScrapArmorModel model = slot == EquipmentSlot.CHEST && isSlim(state) ? slimChestplate : models.get(stack.getItem());
				if (model == null) {
					return;
				}
				ArmorRenderer.submitTransformCopyingModel(wearerModel, state, model, state, false, collector, poseStack,
						stack.hasFoil() ? GLINT : RENDER_TYPE, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
			};
		}, Survival.SCRAP_HELMET, Survival.SCRAP_CHESTPLATE, Survival.SCRAP_LEGGINGS, Survival.SCRAP_BOOTS);
	}

	private static boolean wornIn(ItemStack stack, EquipmentSlot slot) {
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		return equippable != null && equippable.slot() == slot;
	}

	private static boolean isSlim(HumanoidRenderState state) {
		return state instanceof AvatarRenderState avatar && avatar.skin.model() == PlayerModelType.SLIM;
	}
}
