package com.jheinz.tomyen.client.exoskeleton;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

import com.jheinz.tomyen.MyMod;

/**
 * The exoskeleton's frame, in the humanoid model's parts so it takes the wearer's pose: a chest harness with a
 * glowing core, a spine, power pack and exhausts on the back, a belt, shoulder pads, struts down the outside of
 * each arm and leg with a joint at the elbow, hip and knee, wrist cuffs, thigh bands, knee caps and boots.
 * Everything stands clear of a skin's outer layer (0.25 px out), and the head is left bare.
 *
 * <p>The texture is four 32x32 materials, one per quadrant (frame, plate, joint, light), and every cube's
 * unwrap fits inside its material's quadrant. Slim (Alex) arms are a pixel narrower, so they get their own layer.
 */
public class ExoskeletonModel extends HumanoidModel<HumanoidRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(MyMod.id("exoskeleton"), "main");
	public static final ModelLayerLocation SLIM_LAYER = new ModelLayerLocation(MyMod.id("exoskeleton"), "slim");

	// Texture offsets of the four materials.
	private static final int[] FRAME = {0, 0};
	private static final int[] PLATE = {32, 0};
	private static final int[] JOINT = {0, 32};
	private static final int[] LIGHT = {32, 32};

	public ExoskeletonModel(ModelPart root) {
		super(root);
	}

	public static LayerDefinition createLayer(boolean slim) {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		// Same parts and pivots as HumanoidModel.createMesh, so poses copy straight across.
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("body", body(), PartPose.ZERO);
		// The outside of the right arm is at x = -3 from its pivot (-2 for slim); the left arm is its mirror image.
		float outer = slim ? -2.0f : -3.0f;
		root.addOrReplaceChild("right_arm", arm(outer, false), PartPose.offset(-5.0f, 2.0f, 0.0f));
		root.addOrReplaceChild("left_arm", arm(outer, true), PartPose.offset(5.0f, 2.0f, 0.0f));
		root.addOrReplaceChild("right_leg", leg(false), PartPose.offset(-1.9f, 12.0f, 0.0f));
		root.addOrReplaceChild("left_leg", leg(true), PartPose.offset(1.9f, 12.0f, 0.0f));
		return LayerDefinition.create(mesh, 64, 64);
	}

	/** Torso: x -4..4, y 0..12 (down), z -2..2 (the back at +z). */
	private static CubeListBuilder body() {
		CubeListBuilder body = CubeListBuilder.create();
		// Chest harness: two straps, a plate between them and the glowing core on it.
		box(body, FRAME, false, -3.5f, -2.3f, 0.0f, 9.5f, -2.7f, -2.2f);
		box(body, FRAME, true, -3.5f, -2.3f, 0.0f, 9.5f, -2.7f, -2.2f);
		box(body, PLATE, false, -2.3f, 2.3f, 1.5f, 5.0f, -2.9f, -2.2f);
		box(body, LIGHT, false, -1.0f, 1.0f, 2.5f, 4.0f, -3.2f, -2.9f);
		// Belt.
		box(body, FRAME, false, -4.5f, 4.5f, 9.5f, 11.5f, -2.6f, 2.6f);
		// Back: a bar across the shoulders, the spine down from it, the power pack over the spine with two
		// light strips, and an exhaust each side on top.
		box(body, FRAME, false, -4.0f, 4.0f, 0.0f, 1.2f, 2.4f, 3.2f);
		box(body, FRAME, false, -1.0f, 1.0f, 0.5f, 11.0f, 2.4f, 3.4f);
		box(body, PLATE, false, -3.0f, 3.0f, 1.0f, 8.0f, 3.4f, 5.6f);
		box(body, LIGHT, false, -2.0f, -1.0f, 2.0f, 6.0f, 5.6f, 6.0f);
		box(body, LIGHT, true, -2.0f, -1.0f, 2.0f, 6.0f, 5.6f, 6.0f);
		box(body, JOINT, false, -2.6f, -1.4f, -0.6f, 1.0f, 4.6f, 5.5f);
		box(body, JOINT, true, -2.6f, -1.4f, -0.6f, 1.0f, 4.6f, 5.5f);
		return body;
	}

	/**
	 * The right arm from its pivot: x outer..1, y -2..10, z -2..2; mirrored for the left. A pad over the
	 * shoulder, a strut to the elbow joint and on to the wrist cuff.
	 */
	private static CubeListBuilder arm(float outer, boolean mirror) {
		CubeListBuilder arm = CubeListBuilder.create();
		box(arm, PLATE, mirror, outer - 0.6f, 1.4f, -2.6f, 0.6f, -2.7f, 2.7f);
		box(arm, FRAME, mirror, outer - 0.9f, outer - 0.3f, 0.6f, 3.8f, -0.6f, 0.6f);
		box(arm, JOINT, mirror, outer - 1.1f, outer - 0.1f, 3.8f, 5.8f, -1.3f, 1.3f);
		box(arm, FRAME, mirror, outer - 0.9f, outer - 0.3f, 5.8f, 8.2f, -0.6f, 0.6f);
		box(arm, PLATE, mirror, outer - 0.5f, 1.5f, 7.2f, 9.2f, -2.5f, 2.5f);
		return arm;
	}

	/**
	 * The right leg from its pivot: x -2..2, y 0..12, z -2..2; mirrored for the left. Bands, caps and boots stop
	 * short of the inner side (x 1.4) so the two legs never overlap. A hip joint, a band round the thigh, a
	 * strut to the knee joint (with a cap at the front) and on down to the boot.
	 */
	private static CubeListBuilder leg(boolean mirror) {
		CubeListBuilder leg = CubeListBuilder.create();
		box(leg, JOINT, mirror, -3.1f, -2.1f, -0.5f, 2.1f, -1.4f, 1.4f);
		box(leg, PLATE, mirror, -2.5f, 1.4f, 2.1f, 3.6f, -2.5f, 2.5f);
		box(leg, FRAME, mirror, -2.9f, -2.3f, 3.6f, 5.4f, -0.6f, 0.6f);
		box(leg, JOINT, mirror, -3.1f, -2.1f, 5.4f, 7.4f, -1.3f, 1.3f);
		box(leg, PLATE, mirror, -1.5f, 1.2f, 5.2f, 7.6f, -2.8f, -2.3f);
		box(leg, FRAME, mirror, -2.9f, -2.3f, 7.4f, 10.0f, -0.6f, 0.6f);
		box(leg, PLATE, mirror, -2.5f, 1.4f, 10.0f, 12.0f, -2.7f, 2.5f);
		return leg;
	}

	/** Adds a box spanning x0..x1, y0..y1, z0..z1; mirrored, it's flipped across x. */
	private static void box(CubeListBuilder builder, int[] material, boolean mirror, float x0, float x1, float y0, float y1,
			float z0, float z1) {
		float left = mirror ? -x1 : x0;
		builder.texOffs(material[0], material[1]).addBox(left, y0, z0, x1 - x0, y1 - y0, z1 - z0);
	}
}
