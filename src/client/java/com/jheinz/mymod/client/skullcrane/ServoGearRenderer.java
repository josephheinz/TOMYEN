package com.jheinz.mymod.client.skullcrane;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.skullcrane.Skullcranes;

/**
 * Draws the Optic Monocle and the Servo Arm Backpack on whoever wears them (or has them socketed in an exoskeleton,
 * which vanilla's armor layer is pointed at), as Fabric armor renderers following the wearer's head and body.
 * <ul>
 * <li>The monocle: a steel-rimmed red lens over the right eye on a thin band round the head, the lens glowing.
 * <li>The backpack: a steel spine pack with a glowing power cell, and four long segmented arms that rise from it and
 * curl over the shoulders and head, each ending in a three-pronged claw. They sway idly, and reach out for the items
 * the backpack fetches (see {@link ServoArmsClient} and {@link ServoArmRig}).
 * </ul>
 * The arms' targets are in the world; they're brought into the wearer's body space here, through the pose the wearer
 * is drawn with, and the arms' pose for the frame goes with the wearer's render state to be applied when the model is
 * set up.
 */
public final class ServoGearRenderer {
	private static final Identifier TEXTURE = MyMod.id("textures/entity/skullcrane/servo_gear.png");
	private static final RenderType RENDER_TYPE = RenderTypes.entityCutout(TEXTURE);
	private static final RenderType GLOW = RenderTypes.eyes(MyMod.id("textures/entity/skullcrane/servo_gear_glow.png"));
	static final ModelLayerLocation MONOCLE = new ModelLayerLocation(MyMod.id("optic_monocle"), "main");
	static final ModelLayerLocation BACKPACK = new ModelLayerLocation(MyMod.id("servo_arm_backpack"), "main");
	/** This frame's arm pose, from {@link ServoArmRig#pose}. */
	private static final RenderStateDataKey<float[]> ARM_POSE = RenderStateDataKey.create(() -> "mymod:servo_arm_pose");
	/** Texture slots of the arm segments, root to tip, and of a claw prong. */
	private static final int[][] SEGMENT_UV = {{18, 10}, {24, 10}, {30, 10}, {36, 10}};
	private static final int[] PRONG_UV = {40, 10};

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
					state.setData(ARM_POSE, armPose(poseStack, state, wearerModel));
					submit(poseStack, collector, state, light, wearerModel, model);
				}
			};
		}, Skullcranes.SERVO_ARM_BACKPACK);
		ServoArmsClient.initialize();
	}

	private static void submit(PoseStack poseStack, SubmitNodeCollector collector, HumanoidRenderState state, int light,
			HumanoidModel<HumanoidRenderState> wearerModel, Gear model) {
		// The gear's own set-up runs after it's copied the wearer's pose, to put the arms where they go.
		ArmorRenderer.submitTransformCopyingModel(wearerModel, state, model, state, true, collector, poseStack, RENDER_TYPE, light,
				OverlayTexture.NO_OVERLAY, state.outlineColor);
		ArmorRenderer.submitTransformCopyingModel(wearerModel, state, model, state, true, collector, poseStack, GLOW, light,
				OverlayTexture.NO_OVERLAY, 0);
	}

	/**
	 * The arms' pose for a wearer this frame: at rest, or reaching for what the wearer's arms are after, their targets
	 * taken from the world through the pose stack (camera-relative, at the model's root) and the wearer's body part.
	 */
	private static float[] armPose(PoseStack poseStack, HumanoidRenderState state, HumanoidModel<HumanoidRenderState> wearerModel) {
		ServoArmsClient.Arms arms = state instanceof AvatarRenderState avatar ? ServoArmsClient.get(avatar.id) : null;
		if (arms == null) {
			return ServoArmRig.pose(state.ageInTicks, null, null);
		}
		Minecraft minecraft = Minecraft.getInstance();
		float partialTicks = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Matrix4f toModel = new Matrix4f(poseStack.last().pose()).invert();
		Vec3 camera = minecraft.gameRenderer.mainCamera().position();
		ModelPart body = wearerModel.body;
		Quaternionf unrotate = new Quaternionf().rotationZYX(body.zRot, body.yRot, body.xRot).conjugate();
		Vector3f[] targets = new Vector3f[ServoArmRig.ARMS];
		float[] progress = new float[ServoArmRig.ARMS];
		for (int i = 0; i < ServoArmRig.ARMS; i++) {
			Vec3 target = arms.target(i);
			progress[i] = arms.progress(i, partialTicks);
			if (target != null) {
				Vector3f local = toModel.transformPosition((float) (target.x - camera.x), (float) (target.y - camera.y), (float) (target.z - camera.z),
						new Vector3f());
				targets[i] = unrotate.transform(local.mul(16.0f).sub(body.x, body.y, body.z));
			}
		}
		return ServoArmRig.pose(state.ageInTicks, targets, progress);
	}

	/** A humanoid with nothing on it but the gear, so its parts can copy the wearer's pose. */
	private static class Gear extends HumanoidModel<HumanoidRenderState> {
		/** Each arm's segments, root to tip, then its prongs; or null for the monocle. */
		private final ModelPart[][] arms;

		Gear(ModelPart root) {
			super(root);
			if (body.hasChild("arm_0_0")) {
				arms = new ModelPart[ServoArmRig.ARMS][ServoArmRig.PARTS_PER_ARM];
				for (int a = 0; a < ServoArmRig.ARMS; a++) {
					for (int p = 0; p < ServoArmRig.PARTS_PER_ARM; p++) {
						arms[a][p] = body.getChild("arm_" + a + "_" + p);
					}
				}
			} else {
				arms = null;
			}
		}

		/** Keeps the pose copied from the wearer, and puts the backpack's arms where this frame's pose has them. */
		@Override
		public void setupAnim(HumanoidRenderState state) {
			if (arms == null) {
				return;
			}
			float[] pose = state.getData(ARM_POSE);
			if (pose == null) {
				pose = ServoArmRig.pose(state.ageInTicks, null, null);
			}
			for (int a = 0; a < ServoArmRig.ARMS; a++) {
				for (int p = 0; p < ServoArmRig.PARTS_PER_ARM; p++) {
					ModelPart part = arms[a][p];
					int at = (a * ServoArmRig.PARTS_PER_ARM + p) * ServoArmRig.FLOATS;
					part.x = pose[at];
					part.y = pose[at + 1];
					part.z = pose[at + 2];
					part.xRot = pose[at + 3];
					part.yRot = pose[at + 4];
					part.zRot = pose[at + 5];
					part.yScale = pose[at + 6];
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

	/**
	 * The spine pack and its power cell, then each arm's segments (pointing up their own -y, from their joint, and
	 * running a little back under the one before) and prongs, all directly on the body to be posed every frame.
	 */
	private static LayerDefinition createBackpack() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition body = emptyHumanoid(mesh)[1];
		body.addOrReplaceChild("pack", CubeListBuilder.create()
				.texOffs(0, 10).addBox(-2.0f, 0.5f, 2.0f, 4.0f, 8.0f, 2.0f)
				.texOffs(12, 10).addBox(-1.0f, 3.0f, 4.0f, 2.0f, 3.0f, 0.5f),
				PartPose.ZERO);
		for (int a = 0; a < ServoArmRig.ARMS; a++) {
			for (int s = 0; s < ServoArmRig.SEGMENTS; s++) {
				float thick = ServoArmRig.SEGMENT_SIZE[s][0];
				float length = ServoArmRig.SEGMENT_SIZE[s][1];
				body.addOrReplaceChild("arm_" + a + "_" + s, CubeListBuilder.create().texOffs(SEGMENT_UV[s][0], SEGMENT_UV[s][1])
						.addBox(-thick / 2.0f, -length, -thick / 2.0f, thick, length + ServoArmRig.OVERLAP, thick), PartPose.ZERO);
			}
			for (int k = 0; k < ServoArmRig.PRONGS; k++) {
				body.addOrReplaceChild("arm_" + a + "_" + (ServoArmRig.SEGMENTS + k), CubeListBuilder.create().texOffs(PRONG_UV[0], PRONG_UV[1])
						.addBox(-0.25f, -ServoArmRig.PRONG_LENGTH, -0.25f, 0.5f, ServoArmRig.PRONG_LENGTH, 0.5f), PartPose.ZERO);
			}
		}
		return LayerDefinition.create(mesh, 64, 32);
	}
}
