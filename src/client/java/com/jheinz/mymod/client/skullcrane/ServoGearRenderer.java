package com.jheinz.mymod.client.skullcrane;

import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.skullcrane.Skullcranes;

/**
 * Draws the Optic Monocle and the Servo Arm Backpack on whoever wears them (or has them socketed in an exoskeleton,
 * which vanilla's armor layer is pointed at), as Fabric armor renderers following the wearer's head and body.
 * <ul>
 * <li>The monocle: a steel-rimmed red lens over the right eye on a thin band round the head, the lens glowing.
 * <li>The backpack: a steel casing on the back with a glowing power cell, and two servo arms that rise from it and
 * bend forward over the shoulders, swaying and twitching their claws.
 * </ul>
 */
public final class ServoGearRenderer {
	private static final Identifier TEXTURE = MyMod.id("textures/entity/skullcrane/servo_gear.png");
	private static final RenderType RENDER_TYPE = RenderTypes.entityCutout(TEXTURE);
	private static final RenderType GLOW = RenderTypes.eyes(MyMod.id("textures/entity/skullcrane/servo_gear_glow.png"));
	static final ModelLayerLocation MONOCLE = new ModelLayerLocation(MyMod.id("optic_monocle"), "main");
	static final ModelLayerLocation BACKPACK = new ModelLayerLocation(MyMod.id("servo_arm_backpack"), "main");

	private ServoGearRenderer() {
	}

	static void initialize() {
		ModelLayerRegistry.registerModelLayer(MONOCLE, ServoGearRenderer::createMonocle);
		ModelLayerRegistry.registerModelLayer(BACKPACK, ServoGearRenderer::createBackpack);
		ArmorRenderer.register(context -> {
			Gear model = new Gear(context.bakeLayer(MONOCLE));
			return new ArmorRenderer() {
				@Override
				public void render(PoseStack poseStack, SubmitNodeCollector collector, ItemStack stack, HumanoidRenderState state, EquipmentSlot slot,
						int light, HumanoidModel<HumanoidRenderState> wearerModel) {
					if (slot == EquipmentSlot.HEAD) {
						submit(poseStack, collector, state, light, wearerModel, model);
					}
				}

				// It's drawn here, not as an item stuck on the head.
				@Override
				public boolean shouldRenderDefaultHeadItem(LivingEntity entity, ItemStack stack) {
					return false;
				}
			};
		}, Skullcranes.OPTIC_MONOCLE);
		ArmorRenderer.register(context -> {
			Gear model = new Gear(context.bakeLayer(BACKPACK));
			return (poseStack, collector, stack, state, slot, light, wearerModel) -> {
				if (slot == EquipmentSlot.CHEST) {
					model.animateArms(state.ageInTicks);
					submit(poseStack, collector, state, light, wearerModel, model);
				}
			};
		}, Skullcranes.SERVO_ARM_BACKPACK);
	}

	private static void submit(PoseStack poseStack, SubmitNodeCollector collector, HumanoidRenderState state, int light,
			HumanoidModel<HumanoidRenderState> wearerModel, Gear model) {
		ArmorRenderer.submitTransformCopyingModel(wearerModel, state, model, state, false, collector, poseStack, RENDER_TYPE, light,
				OverlayTexture.NO_OVERLAY, state.outlineColor);
		ArmorRenderer.submitTransformCopyingModel(wearerModel, state, model, state, false, collector, poseStack, GLOW, light,
				OverlayTexture.NO_OVERLAY, 0);
	}

	/** A humanoid with nothing on it but the gear, so its parts can copy the wearer's pose. */
	private static class Gear extends HumanoidModel<HumanoidRenderState> {
		private final ModelPart[] arms = new ModelPart[2];
		private final ModelPart[] elbows = new ModelPart[2];
		private final ModelPart[][] fingers = new ModelPart[2][3];

		Gear(ModelPart root) {
			super(root);
			if (body.hasChild("arm_0")) {
				for (int side = 0; side < 2; side++) {
					arms[side] = body.getChild("arm_" + side);
					elbows[side] = arms[side].getChild("elbow");
					for (int f = 0; f < 3; f++) {
						fingers[side][f] = elbows[side].getChild("finger_" + f);
					}
				}
			}
		}

		@Override
		public void setupAnim(HumanoidRenderState state) {
			super.setupAnim(state);
			animateArms(state.ageInTicks);
		}

		/** The backpack's arms sway a little and their claws twitch open and shut. */
		void animateArms(float age) {
			if (arms[0] == null) {
				return;
			}
			for (int side = 0; side < 2; side++) {
				float sign = side == 0 ? -1.0f : 1.0f;
				float phase = side * 2.3f;
				arms[side].xRot = 0.35f + 0.06f * Mth.sin(age * 0.07f + phase);
				arms[side].zRot = sign * (0.3f + 0.05f * Mth.sin(age * 0.09f + phase));
				elbows[side].xRot = 1.5f + 0.12f * Mth.sin(age * 0.11f + phase) + (Mth.floor(age / 13.0f + side) % 3 == 0 ? 0.08f : 0.0f);
				float open = 0.25f + 0.2f * Math.max(0.0f, Mth.sin(age * 0.05f + phase));
				for (int f = 0; f < 3; f++) {
					// Negative opens: out from the forearm.
					fingers[side][f].xRot = -open;
				}
			}
		}
	}

	/** Every humanoid part, empty and where vanilla puts it. */
	private static PartDefinition[] emptyHumanoid(MeshDefinition mesh) {
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5.0f, 2.0f, 0.0f));
		root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5.0f, 2.0f, 0.0f));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9f, 12.0f, 0.0f));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9f, 12.0f, 0.0f));
		return new PartDefinition[] {head, body};
	}

	private static LayerDefinition createMonocle() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition head = emptyHumanoid(mesh)[0];
		head.addOrReplaceChild("monocle", CubeListBuilder.create()
				// The band round the head, at eye level.
				.texOffs(0, 0).addBox(-4.3f, -4.6f, -4.3f, 8.6f, 0.6f, 8.6f)
				// Rim round the right eye, and the lens in it.
				.texOffs(36, 0).addBox(-3.6f, -5.6f, -4.7f, 3.0f, 0.5f, 0.5f)
				.texOffs(36, 0).addBox(-3.6f, -2.6f, -4.7f, 3.0f, 0.5f, 0.5f)
				.texOffs(44, 0).addBox(-3.6f, -5.1f, -4.7f, 0.5f, 2.5f, 0.5f)
				.texOffs(44, 0).addBox(-1.1f, -5.1f, -4.7f, 0.5f, 2.5f, 0.5f)
				.texOffs(48, 0).addBox(-3.1f, -5.1f, -4.5f, 2.0f, 2.5f, 0.3f),
				PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 32);
	}

	private static LayerDefinition createBackpack() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition body = emptyHumanoid(mesh)[1];
		body.addOrReplaceChild("pack", CubeListBuilder.create()
				.texOffs(0, 10).addBox(-3.0f, 1.0f, 2.0f, 6.0f, 7.0f, 2.5f)
				.texOffs(18, 10).addBox(-1.5f, 2.5f, 4.5f, 3.0f, 4.0f, 1.0f),
				PartPose.ZERO);
		for (int side = 0; side < 2; side++) {
			float x = side == 0 ? -2.3f : 2.3f;
			PartDefinition arm = body.addOrReplaceChild("arm_" + side, CubeListBuilder.create()
					.texOffs(38, 10).addBox(-0.9f, -0.9f, -0.9f, 1.8f, 1.8f, 1.8f)
					.texOffs(26, 10).addBox(-0.6f, -5.5f, -0.6f, 1.2f, 5.0f, 1.2f),
					PartPose.offset(x, 1.5f, 3.6f));
			PartDefinition elbow = arm.addOrReplaceChild("elbow", CubeListBuilder.create()
					.texOffs(38, 10).addBox(-0.9f, -0.9f, -0.9f, 1.8f, 1.8f, 1.8f)
					.texOffs(34, 10).addBox(-0.5f, -4.5f, -0.5f, 1.0f, 4.0f, 1.0f),
					PartPose.offset(0.0f, -5.5f, 0.0f));
			for (int f = 0; f < 3; f++) {
				float angle = f * Mth.TWO_PI / 3.0f;
				// Fingers point on along the forearm (up, in its frame), splayed by xRot.
				elbow.addOrReplaceChild("finger_" + f, CubeListBuilder.create().texOffs(46, 10).addBox(-0.3f, -2.0f, -0.3f, 0.6f, 2.0f, 0.6f),
						PartPose.offsetAndRotation(Mth.sin(angle) * 0.4f, -4.5f, Mth.cos(angle) * 0.4f, 0.0f, angle, 0.0f));
			}
		}
		return LayerDefinition.create(mesh, 64, 32);
	}
}
