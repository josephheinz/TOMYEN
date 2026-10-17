package com.jheinz.tomyen.client.fusion.gear;

import java.util.Map;

import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
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

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.fusion.gear.FusionGear;

/**
 * Draws the batch-3 fusion gear worn: each piece's 3D model ({@link FusionGearModel}) copying the wearer's pose, in its
 * texture (glinting when enchanted), then its glow texture full bright on top (ectoplasm, gems, the wolf's eyes, the
 * visor's stars and crystals; the ectoplasm wisps exist only there, so they're see-through). Slim-armed players get the
 * chestplates' slim layers. Like the Scrap armor, socketed in an exoskeleton they're drawn the same way.
 */
public final class FusionGearRenderer {
	private record Look(FusionGearModel wide, FusionGearModel slim, RenderType base, RenderType glint, RenderType glow) {
	}

	private FusionGearRenderer() {
	}

	static void initialize() {
		ModelLayerRegistry.registerModelLayer(FusionGearModel.ASTRAL_VISOR, FusionGearModel::createAstralVisor);
		ModelLayerRegistry.registerModelLayer(FusionGearModel.PHANTASMAL_CHESTPLATE, FusionGearModel::createPhantasmalChestplate);
		ModelLayerRegistry.registerModelLayer(FusionGearModel.PHANTASMAL_CHESTPLATE_SLIM, FusionGearModel::createPhantasmalChestplateSlim);
		ModelLayerRegistry.registerModelLayer(FusionGearModel.WOLFHIDE_CARRIER, FusionGearModel::createWolfhideCarrier);
		ModelLayerRegistry.registerModelLayer(FusionGearModel.WOLFHIDE_CARRIER_SLIM, FusionGearModel::createWolfhideCarrierSlim);
		ModelLayerRegistry.registerModelLayer(FusionGearModel.REVENANT_MAIL, FusionGearModel::createRevenantMail);
		ModelLayerRegistry.registerModelLayer(FusionGearModel.REVENANT_MAIL_SLIM, FusionGearModel::createRevenantMailSlim);
		ArmorRenderer.register(context -> {
			Map<Item, Look> looks = Map.of(
					FusionGear.ASTRAL_VISOR, look(context, "astral_visor", FusionGearModel.ASTRAL_VISOR, FusionGearModel.ASTRAL_VISOR),
					FusionGear.PHANTASMAL_CHESTPLATE, look(context, "phantasmal_chestplate", FusionGearModel.PHANTASMAL_CHESTPLATE,
							FusionGearModel.PHANTASMAL_CHESTPLATE_SLIM),
					FusionGear.WOLFHIDE_CARRIER, look(context, "wolfhide_carrier", FusionGearModel.WOLFHIDE_CARRIER,
							FusionGearModel.WOLFHIDE_CARRIER_SLIM),
					FusionGear.REVENANT_MAIL, look(context, "revenant_mail", FusionGearModel.REVENANT_MAIL, FusionGearModel.REVENANT_MAIL_SLIM));
			return (poseStack, collector, stack, state, slot, light, wearerModel) -> {
				// A baby's parts are scaled by its own mesh, which the gear wouldn't follow; and each piece only in its slot.
				Look look = looks.get(stack.getItem());
				if (state.isBaby || look == null || !wornIn(stack, slot)) {
					return;
				}
				FusionGearModel model = isSlim(state) ? look.slim() : look.wide();
				ArmorRenderer.submitTransformCopyingModel(wearerModel, state, model, state, false, collector, poseStack,
						stack.hasFoil() ? look.glint() : look.base(), light, OverlayTexture.NO_OVERLAY, state.outlineColor);
				ArmorRenderer.submitTransformCopyingModel(wearerModel, state, model, state, false, collector.order(1), poseStack,
						look.glow(), light, OverlayTexture.NO_OVERLAY, state.outlineColor);
			};
		}, FusionGear.ASTRAL_VISOR, FusionGear.PHANTASMAL_CHESTPLATE, FusionGear.WOLFHIDE_CARRIER, FusionGear.REVENANT_MAIL);
	}

	private static Look look(EntityRendererProvider.Context context, String name, ModelLayerLocation wide, ModelLayerLocation slim) {
		Identifier texture = MyMod.id("textures/entity/fusion_gear/" + name + ".png");
		Identifier glow = MyMod.id("textures/entity/fusion_gear/" + name + "_glow.png");
		return new Look(new FusionGearModel(context.bakeLayer(wide)), new FusionGearModel(context.bakeLayer(slim)),
				RenderTypes.armorCutoutNoCull(texture), RenderTypes.armorCutoutNoCullGlint(texture), RenderTypes.eyes(glow));
	}

	private static boolean wornIn(ItemStack stack, EquipmentSlot slot) {
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		return equippable != null && equippable.slot() == slot;
	}

	private static boolean isSlim(HumanoidRenderState state) {
		return state instanceof AvatarRenderState avatar && avatar.skin.model() == PlayerModelType.SLIM;
	}
}
