package com.jheinz.tomyen.client.exoskeleton;

import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

import org.jspecify.annotations.Nullable;

import com.jheinz.tomyen.ModItems;
import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.exoskeleton.ExoSockets;
import com.jheinz.tomyen.exoskeleton.ExoskeletonItem;

/**
 * Draws a worn exoskeleton. In third person it's a Fabric armor renderer for the chest slot (the suit has no
 * equipment asset, so vanilla draws nothing for it): the {@link ExoskeletonModel} copies the wearer's model's
 * pose part by part, so it follows every animation, guns included. In first person the frame of the arm is
 * drawn over the player's own hand (see AvatarRendererExoskeletonArmMixin). The lights glow in the dark.
 *
 * <p>What's socketed is drawn over the frame by vanilla's own armor and elytra layers, which are pointed at the
 * socketed pieces (see the client Exoskeleton mixins). In first person a socketed chestplate's sleeve goes over
 * the frame's arm, which vanilla never draws for worn armor.
 */
public final class ExoskeletonRenderer {
	private static final Identifier TEXTURE = MyMod.id("textures/entity/exoskeleton/exoskeleton.png");
	private static final Identifier GLOW_TEXTURE = MyMod.id("textures/entity/exoskeleton/exoskeleton_glow.png");
	private static final RenderType RENDER_TYPE = RenderTypes.entityCutout(TEXTURE);
	private static final RenderType GLOW = RenderTypes.eyes(GLOW_TEXTURE);

	/** Baked on first use, for the first-person arms only, so nothing else moves their parts. */
	private static ExoskeletonModel firstPersonWide;
	private static ExoskeletonModel firstPersonSlim;
	/** Vanilla's armor renderer and the player chestplate's arms, for first person; set on every resource reload. */
	private static @Nullable EquipmentLayerRenderer equipmentRenderer;
	private static @Nullable ArmorArms wideArmorArms;
	private static @Nullable ArmorArms slimArmorArms;

	/** A chestplate's two arms on their own, posed by hand (Model.Simple doesn't animate). */
	private record ArmorArms(Model.Simple right, Model.Simple left) {
		static ArmorArms bake(EntityRendererProvider.Context context, ModelLayerLocation chest) {
			ModelPart root = context.bakeLayer(chest);
			return new ArmorArms(new Model.Simple(root.getChild("right_arm"), RenderTypes::armorCutoutNoCull),
					new Model.Simple(root.getChild("left_arm"), RenderTypes::armorCutoutNoCull));
		}
	}

	private ExoskeletonRenderer() {
	}

	public static void initialize() {
		ModelLayerRegistry.registerModelLayer(ExoskeletonModel.LAYER, () -> ExoskeletonModel.createLayer(false));
		ModelLayerRegistry.registerModelLayer(ExoskeletonModel.SLIM_LAYER, () -> ExoskeletonModel.createLayer(true));
		ArmorRenderer.register(context -> {
			ExoskeletonModel wide = new ExoskeletonModel(context.bakeLayer(ExoskeletonModel.LAYER));
			ExoskeletonModel slim = new ExoskeletonModel(context.bakeLayer(ExoskeletonModel.SLIM_LAYER));
			equipmentRenderer = context.getEquipmentRenderer();
			wideArmorArms = ArmorArms.bake(context, ModelLayers.PLAYER_ARMOR.chest());
			slimArmorArms = ArmorArms.bake(context, ModelLayers.PLAYER_SLIM_ARMOR.chest());
			return (poseStack, collector, stack, state, slot, light, wearerModel) ->
					submit(poseStack, collector, state, slot, light, wearerModel, isSlim(state) ? slim : wide);
		}, ModItems.EXOSKELETON);
	}

	private static void submit(PoseStack poseStack, SubmitNodeCollector collector, HumanoidRenderState state, EquipmentSlot slot,
			int light, HumanoidModel<HumanoidRenderState> wearerModel, ExoskeletonModel model) {
		// A baby's body is shrunk by its mesh, which the frame doesn't follow, so it would hang off a baby mob.
		if (slot != EquipmentSlot.CHEST || state.isBaby) {
			return;
		}
		ArmorRenderer.submitTransformCopyingModel(wearerModel, state, model, state, false, collector, poseStack, RENDER_TYPE, light,
				OverlayTexture.NO_OVERLAY, state.outlineColor);
		ArmorRenderer.submitTransformCopyingModel(wearerModel, state, model, state, false, collector, poseStack, GLOW, light,
				OverlayTexture.NO_OVERLAY, 0);
	}

	private static boolean isSlim(HumanoidRenderState state) {
		return state instanceof AvatarRenderState avatar && avatar.skin.model() == PlayerModelType.SLIM;
	}

	/**
	 * Draws the suit's arm over the local player's first-person hand, posed as vanilla poses the hand: from its
	 * rest pose, tilted a little outward. A socketed chestplate's sleeve goes over it, in the same pose.
	 */
	public static void submitFirstPersonArm(PoseStack poseStack, SubmitNodeCollector collector, int light, HumanoidArm side) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
		if (!(chest.getItem() instanceof ExoskeletonItem)) {
			return;
		}
		boolean slim = player.getSkin().model() == PlayerModelType.SLIM;
		ExoskeletonModel model = firstPersonModel(slim);
		ModelPart arm = side == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
		float tilt = side == HumanoidArm.RIGHT ? 0.1f : -0.1f;
		arm.resetPose();
		arm.visible = true;
		arm.zRot = tilt;
		collector.submitModelPart(arm, poseStack, RENDER_TYPE, light, OverlayTexture.NO_OVERLAY, null);
		collector.submitModelPart(arm, poseStack, GLOW, light, OverlayTexture.NO_OVERLAY, null);

		ItemStack chestplate = ExoSockets.armor(chest, EquipmentSlot.CHEST);
		Equippable equippable = chestplate.get(DataComponents.EQUIPPABLE);
		ArmorArms arms = slim ? slimArmorArms : wideArmorArms;
		// An elytra has no humanoid layers, so it draws nothing here.
		if (equippable == null || equippable.assetId().isEmpty() || arms == null || equipmentRenderer == null) {
			return;
		}
		Model.Simple sleeve = side == HumanoidArm.RIGHT ? arms.right() : arms.left();
		sleeve.root().resetPose();
		sleeve.root().zRot = tilt;
		equipmentRenderer.renderLayers(EquipmentClientInfo.LayerType.HUMANOID, equippable.assetId().get(), sleeve, Unit.INSTANCE, chestplate,
				poseStack, collector, light, 0);
	}

	private static ExoskeletonModel firstPersonModel(boolean slim) {
		if (slim) {
			if (firstPersonSlim == null) {
				firstPersonSlim = new ExoskeletonModel(Minecraft.getInstance().getEntityModels().bakeLayer(ExoskeletonModel.SLIM_LAYER));
			}
			return firstPersonSlim;
		}
		if (firstPersonWide == null) {
			firstPersonWide = new ExoskeletonModel(Minecraft.getInstance().getEntityModels().bakeLayer(ExoskeletonModel.LAYER));
		}
		return firstPersonWide;
	}
}
