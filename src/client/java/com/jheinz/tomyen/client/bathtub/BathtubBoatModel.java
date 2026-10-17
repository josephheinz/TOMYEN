package com.jheinz.tomyen.client.bathtub;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

import com.jheinz.tomyen.MyMod;

/**
 * The Bathtub Boat, front (the faucet end) at -z, about 34 pixels of tub on brass claw feet with the outboard motor
 * clamped over the back rim. The geometry and its UVs come from tools/gen_bathtub_boat.py (`--java-into` rewrites the
 * generated part below); its texture, textures/block/bathtub.png, is shared with the Bathtub block and both item models.
 * <ul>
 * <li>tub: the flared basin, stepped rounded walls and rolled rim; the faucet, taps, overflow and plug on its chain;
 * the transom bracket and the kerosene can with its fuel line.</li>
 * <li>Four claw feet, which step when it waddles ashore.</li>
 * <li>The rubber duck, squashed when it's squeezed.</li>
 * <li>motor_tilt (tilts the motor up ashore) and motor (swivels with the steering), carrying the cowling, the tiller,
 * the leg and the propeller, whose three blades spin with the throttle.</li>
 * <li>Two oars, out only while it's rowed with an empty tank.</li>
 * </ul>
 */
public class BathtubBoatModel extends EntityModel<BathtubBoatRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(MyMod.id("bathtub_boat"), "main");

	private final ModelPart[] feet;
	private final ModelPart duck;
	private final ModelPart motorTilt;
	private final ModelPart motor;
	private final ModelPart propeller;
	private final ModelPart leftOar;
	private final ModelPart rightOar;

	public BathtubBoatModel(ModelPart root) {
		super(root);
		feet = new ModelPart[] {root.getChild("front_left_foot"), root.getChild("back_right_foot"), root.getChild("front_right_foot"),
				root.getChild("back_left_foot")};
		duck = root.getChild("duck");
		motorTilt = root.getChild("motor_tilt");
		motor = motorTilt.getChild("motor");
		propeller = motor.getChild("propeller");
		leftOar = root.getChild("left_oar");
		rightOar = root.getChild("right_oar");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		// BEGIN GENERATED (tools/gen_bathtub_boat.py)
		root.addOrReplaceChild("tub", CubeListBuilder.create()
				.texOffs(0, 31).addBox(-6.5f, 16.0f, -12.5f, 13.0f, 3.5f, 25.0f)
				.texOffs(46, 87).addBox(-5.5f, 16.0f, 12.5f, 11.0f, 3.5f, 1.5f)
				.texOffs(46, 87).addBox(-5.5f, 16.0f, -14.0f, 11.0f, 3.5f, 1.5f)
				.texOffs(108, 80).addBox(-4.0f, 16.0f, 14.0f, 8.0f, 3.5f, 1.25f)
				.texOffs(108, 80).addBox(-4.0f, 16.0f, -15.25f, 8.0f, 3.5f, 1.25f)
				.texOffs(0, 0).addBox(6.5f, 10.5f, -12.5f, 1.0f, 5.5f, 25.0f)
				.texOffs(52, 0).addBox(-7.5f, 10.5f, -12.5f, 1.0f, 5.5f, 25.0f)
				.texOffs(116, 50).addBox(5.5f, 10.5f, 12.5f, 1.0f, 5.5f, 1.5f)
				.texOffs(122, 50).addBox(5.5f, 10.5f, -14.0f, 1.0f, 5.5f, 1.5f)
				.texOffs(108, 58).addBox(-6.5f, 10.5f, 12.5f, 1.0f, 5.5f, 1.5f)
				.texOffs(114, 58).addBox(-6.5f, 10.5f, -14.0f, 1.0f, 5.5f, 1.5f)
				.texOffs(120, 34).addBox(4.0f, 10.5f, 14.0f, 1.5f, 5.5f, 1.25f)
				.texOffs(108, 42).addBox(4.0f, 10.5f, -15.25f, 1.5f, 5.5f, 1.25f)
				.texOffs(116, 42).addBox(-5.5f, 10.5f, 14.0f, 1.5f, 5.5f, 1.25f)
				.texOffs(108, 50).addBox(-5.5f, 10.5f, -15.25f, 1.5f, 5.5f, 1.25f)
				.texOffs(108, 18).addBox(-4.0f, 10.5f, 15.25f, 8.0f, 5.5f, 1.25f)
				.texOffs(108, 26).addBox(-4.0f, 10.5f, -16.5f, 8.0f, 5.5f, 1.25f)
				.texOffs(0, 60).addBox(6.5f, 9.0f, -12.5f, 1.5f, 1.5f, 25.0f)
				.texOffs(54, 60).addBox(-8.0f, 9.0f, -12.5f, 1.5f, 1.5f, 25.0f)
				.texOffs(56, 98).addBox(5.5f, 9.0f, 12.5f, 1.5f, 1.5f, 1.5f)
				.texOffs(64, 98).addBox(5.5f, 9.0f, -14.0f, 1.5f, 1.5f, 1.5f)
				.texOffs(0, 99).addBox(-7.0f, 9.0f, 12.5f, 1.5f, 1.5f, 1.5f)
				.texOffs(8, 99).addBox(-7.0f, 9.0f, -14.0f, 1.5f, 1.5f, 1.5f)
				.texOffs(16, 99).addBox(4.0f, 9.0f, 14.0f, 2.0f, 1.5f, 1.25f)
				.texOffs(72, 101).addBox(4.0f, 9.0f, -15.25f, 2.0f, 1.5f, 1.25f)
				.texOffs(80, 101).addBox(-6.0f, 9.0f, 14.0f, 2.0f, 1.5f, 1.25f)
				.texOffs(88, 101).addBox(-6.0f, 9.0f, -15.25f, 2.0f, 1.5f, 1.25f)
				.texOffs(24, 94).addBox(-4.5f, 9.0f, 15.25f, 9.0f, 1.5f, 1.75f)
				.texOffs(24, 98).addBox(-4.5f, 9.0f, -17.0f, 9.0f, 1.5f, 1.75f)
				.texOffs(38, 102).addBox(-3.25f, 8.5f, -16.75f, 6.5f, 0.5f, 1.5f)
				.texOffs(30, 102).addBox(-0.5f, 5.5f, -16.5f, 1.0f, 3.0f, 1.0f)
				.texOffs(96, 101).addBox(-0.5f, 5.5f, -15.5f, 1.0f, 1.0f, 2.25f)
				.texOffs(12, 106).addBox(-0.5f, 6.5f, -14.0f, 1.0f, 1.0f, 0.75f)
				.texOffs(80, 105).addBox(1.875f, 7.25f, -16.375f, 0.75f, 1.25f, 0.75f)
				.texOffs(116, 105).addBox(1.25f, 6.75f, -16.25f, 2.0f, 0.5f, 0.5f)
				.texOffs(16, 103).addBox(2.0f, 6.65f, -17.0f, 0.5f, 0.65f, 2.0f)
				.texOffs(16, 106).addBox(1.95f, 6.4f, -16.3f, 0.6f, 0.25f, 0.6f)
				.texOffs(80, 105).addBox(-2.625f, 7.25f, -16.375f, 0.75f, 1.25f, 0.75f)
				.texOffs(116, 105).addBox(-3.25f, 6.75f, -16.25f, 2.0f, 0.5f, 0.5f)
				.texOffs(16, 103).addBox(-2.5f, 6.65f, -17.0f, 0.5f, 0.65f, 2.0f)
				.texOffs(20, 106).addBox(-2.55f, 6.4f, -16.3f, 0.6f, 0.25f, 0.6f)
				.texOffs(38, 105).addBox(-0.75f, 11.5f, -15.25f, 1.5f, 1.5f, 0.25f)
				.texOffs(34, 102).addBox(-0.15f, 13.0f, -15.0f, 0.3f, 2.7f, 0.3f)
				.texOffs(44, 105).addBox(-0.15f, 15.7f, -15.0f, 0.3f, 0.3f, 1.6f)
				.texOffs(0, 103).addBox(-0.75f, 15.4f, -13.4f, 1.5f, 0.6f, 1.2f)
				.texOffs(108, 73).addBox(-1.75f, 8.25f, 17.0f, 3.5f, 5.75f, 0.75f)
				.texOffs(56, 102).addBox(-1.75f, 8.25f, 15.5f, 3.5f, 0.75f, 1.5f)
				.texOffs(24, 106).addBox(-1.5f, 9.75f, 14.75f, 1.0f, 0.5f, 0.5f)
				.texOffs(24, 106).addBox(0.5f, 9.75f, 14.75f, 1.0f, 0.5f, 0.5f)
				.texOffs(108, 66).addBox(0.25f, 12.25f, 10.75f, 3.5f, 3.75f, 2.5f)
				.texOffs(122, 105).addBox(1.25f, 11.5f, 11.5f, 1.5f, 0.75f, 1.0f)
				.texOffs(28, 106).addBox(0.5f, 11.75f, 11.0f, 0.5f, 0.5f, 0.5f)
				.texOffs(50, 105).addBox(2.25f, 13.2f, 13.25f, 0.3f, 0.3f, 2.0f)
				.texOffs(98, 87).addBox(2.25f, 8.7f, 14.95f, 0.3f, 4.5f, 0.3f)
				.texOffs(56, 105).addBox(2.25f, 8.7f, 15.25f, 0.3f, 0.3f, 1.5f)
				.texOffs(74, 97).addBox(-8.5f, 8.0f, 8.0f, 17.0f, 1.0f, 2.75f)
				.texOffs(74, 97).addBox(-8.5f, 8.0f, 11.25f, 17.0f, 1.0f, 2.75f)
				.texOffs(8, 103).addBox(5.0f, 7.25f, 8.5f, 2.0f, 0.75f, 1.5f),
				PartPose.offset(0.0f, 0.0f, 0.0f));
		root.addOrReplaceChild("front_left_foot", CubeListBuilder.create()
				.texOffs(102, 92).addBox(-1.5f, -0.25f, -1.5f, 3.0f, 1.25f, 3.0f)
				.texOffs(104, 101).addBox(-0.875f, 1.0f, -0.875f, 1.75f, 1.75f, 1.75f)
				.texOffs(114, 97).addBox(-1.125f, 2.75f, -1.125f, 2.25f, 0.5f, 2.25f)
				.texOffs(114, 92).addBox(-1.25f, 3.25f, -1.25f, 2.5f, 1.25f, 2.5f)
				.texOffs(84, 105).addBox(-0.5f, 2.5f, -1.75f, 1.0f, 2.0f, 0.5f)
				.texOffs(88, 105).addBox(1.25f, 2.5f, -0.5f, 0.5f, 2.0f, 1.0f),
				PartPose.offsetAndRotation(4.75f, 19.5f, -10.5f, 0.2f, 0.0f, -0.24f));
		root.addOrReplaceChild("front_right_foot", CubeListBuilder.create()
				.texOffs(102, 92).addBox(-1.5f, -0.25f, -1.5f, 3.0f, 1.25f, 3.0f)
				.texOffs(104, 101).addBox(-0.875f, 1.0f, -0.875f, 1.75f, 1.75f, 1.75f)
				.texOffs(114, 97).addBox(-1.125f, 2.75f, -1.125f, 2.25f, 0.5f, 2.25f)
				.texOffs(114, 92).addBox(-1.25f, 3.25f, -1.25f, 2.5f, 1.25f, 2.5f)
				.texOffs(84, 105).addBox(-0.5f, 2.5f, -1.75f, 1.0f, 2.0f, 0.5f)
				.texOffs(88, 105).addBox(-1.75f, 2.5f, -0.5f, 0.5f, 2.0f, 1.0f),
				PartPose.offsetAndRotation(-4.75f, 19.5f, -10.5f, 0.2f, 0.0f, 0.24f));
		root.addOrReplaceChild("back_left_foot", CubeListBuilder.create()
				.texOffs(102, 92).addBox(-1.5f, -0.25f, -1.5f, 3.0f, 1.25f, 3.0f)
				.texOffs(104, 101).addBox(-0.875f, 1.0f, -0.875f, 1.75f, 1.75f, 1.75f)
				.texOffs(114, 97).addBox(-1.125f, 2.75f, -1.125f, 2.25f, 0.5f, 2.25f)
				.texOffs(114, 92).addBox(-1.25f, 3.25f, -1.25f, 2.5f, 1.25f, 2.5f)
				.texOffs(84, 105).addBox(-0.5f, 2.5f, -1.75f, 1.0f, 2.0f, 0.5f)
				.texOffs(88, 105).addBox(1.25f, 2.5f, -0.5f, 0.5f, 2.0f, 1.0f),
				PartPose.offsetAndRotation(4.75f, 19.5f, 10.5f, -0.2f, 0.0f, -0.24f));
		root.addOrReplaceChild("back_right_foot", CubeListBuilder.create()
				.texOffs(102, 92).addBox(-1.5f, -0.25f, -1.5f, 3.0f, 1.25f, 3.0f)
				.texOffs(104, 101).addBox(-0.875f, 1.0f, -0.875f, 1.75f, 1.75f, 1.75f)
				.texOffs(114, 97).addBox(-1.125f, 2.75f, -1.125f, 2.25f, 0.5f, 2.25f)
				.texOffs(114, 92).addBox(-1.25f, 3.25f, -1.25f, 2.5f, 1.25f, 2.5f)
				.texOffs(84, 105).addBox(-0.5f, 2.5f, -1.75f, 1.0f, 2.0f, 0.5f)
				.texOffs(88, 105).addBox(-1.75f, 2.5f, -0.5f, 0.5f, 2.0f, 1.0f),
				PartPose.offsetAndRotation(-4.75f, 19.5f, 10.5f, -0.2f, 0.0f, 0.24f));
		root.addOrReplaceChild("duck", CubeListBuilder.create()
				.texOffs(72, 87).addBox(-1.25f, -1.75f, -1.5f, 2.5f, 1.75f, 3.25f)
				.texOffs(112, 101).addBox(-0.875f, -3.5f, -1.75f, 1.75f, 1.75f, 1.75f)
				.texOffs(32, 106).addBox(-0.5f, -2.5f, -2.5f, 1.0f, 0.5f, 0.75f)
				.texOffs(0, 106).addBox(-0.625f, -2.5f, 1.75f, 1.25f, 1.0f, 0.5f)
				.texOffs(62, 105).addBox(1.25f, -1.5f, -0.5f, 0.25f, 1.0f, 1.75f)
				.texOffs(62, 105).addBox(-1.5f, -1.5f, -0.5f, 0.25f, 1.0f, 1.75f),
				PartPose.offset(7.25f, 9.0f, -10.5f));
		PartDefinition motorTilt = root.addOrReplaceChild("motor_tilt", CubeListBuilder.create(),
				PartPose.offset(0.0f, 8.5f, 17.75f));
		PartDefinition motor = motorTilt.addOrReplaceChild("motor", CubeListBuilder.create()
				.texOffs(0, 87).addBox(-2.5f, -5.0f, -0.5f, 5.0f, 4.5f, 6.5f)
				.texOffs(24, 87).addBox(-2.25f, -6.0f, -0.25f, 4.5f, 1.0f, 6.0f)
				.texOffs(46, 93).addBox(-2.0f, -4.5f, 6.0f, 4.0f, 3.5f, 0.5f)
				.texOffs(6, 106).addBox(-0.75f, -3.5f, -1.0f, 1.5f, 0.5f, 0.5f)
				.texOffs(108, 34).addBox(-1.5f, -0.5f, 0.25f, 3.0f, 5.0f, 3.0f)
				.texOffs(108, 9).addBox(-1.0f, 4.5f, 0.75f, 2.0f, 6.5f, 2.0f)
				.texOffs(108, 86).addBox(-2.0f, 11.0f, -0.25f, 4.0f, 0.5f, 4.5f)
				.texOffs(108, 0).addBox(-1.25f, 11.5f, -0.5f, 2.5f, 2.5f, 5.25f)
				.texOffs(68, 105).addBox(-0.75f, 12.0f, -1.25f, 1.5f, 1.5f, 0.75f)
				.texOffs(66, 93).addBox(-0.375f, 14.0f, 1.75f, 0.75f, 2.0f, 2.5f),
				PartPose.offset(0.0f, 0.0f, 0.5f));
		motor.addOrReplaceChild("tiller", CubeListBuilder.create()
				.texOffs(86, 87).addBox(-0.5f, -0.5f, -5.0f, 1.0f, 1.0f, 5.0f)
				.texOffs(74, 105).addBox(-0.85f, -0.85f, -5.5f, 1.7f, 1.7f, 0.5f)
				.texOffs(56, 93).addBox(-0.75f, -0.75f, -8.25f, 1.5f, 1.5f, 2.75f),
				PartPose.offsetAndRotation(-2.0f, -2.25f, -0.5f, -0.08f, 0.5f, 0.0f));
		PartDefinition propeller = motor.addOrReplaceChild("propeller", CubeListBuilder.create()
				.texOffs(120, 101).addBox(-0.75f, -0.75f, -0.25f, 1.5f, 1.5f, 1.25f)
				.texOffs(92, 107).addBox(-0.4f, -0.4f, 1.0f, 0.8f, 0.8f, 0.5f),
				PartPose.offset(0.0f, 12.75f, 4.75f));
		propeller.addOrReplaceChild("blade_0", CubeListBuilder.create()
				.texOffs(24, 102).addBox(-0.75f, -3.5f, 0.25f, 1.5f, 2.75f, 0.5f),
				PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, 0.4f, 0.0f));
		propeller.addOrReplaceChild("blade_1", CubeListBuilder.create()
				.texOffs(24, 102).addBox(-0.75f, -3.5f, 0.25f, 1.5f, 2.75f, 0.5f),
				PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, 0.4f, 2.0944f));
		propeller.addOrReplaceChild("blade_2", CubeListBuilder.create()
				.texOffs(24, 102).addBox(-0.75f, -3.5f, 0.25f, 1.5f, 2.75f, 0.5f),
				PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, 0.4f, 4.1888f));
		root.addOrReplaceChild("left_oar", CubeListBuilder.create()
				.texOffs(92, 105).addBox(-3.0f, -0.375f, -0.375f, 11.0f, 0.75f, 0.75f)
				.texOffs(46, 98).addBox(7.5f, -1.5f, -0.25f, 3.5f, 3.0f, 0.5f),
				PartPose.offsetAndRotation(7.75f, 8.0f, 6.0f, 0.0f, 0.0f, 0.32f));
		root.addOrReplaceChild("right_oar", CubeListBuilder.create()
				.texOffs(92, 105).addBox(-8.0f, -0.375f, -0.375f, 11.0f, 0.75f, 0.75f)
				.texOffs(46, 98).addBox(-11.0f, -1.5f, -0.25f, 3.5f, 3.0f, 0.5f),
				PartPose.offsetAndRotation(-7.75f, 8.0f, 6.0f, 0.0f, 0.0f, -0.32f));
		// END GENERATED
		return LayerDefinition.create(mesh, 128, 128);
	}

	@Override
	public void setupAnim(BathtubBoatRenderState state) {
		super.setupAnim(state);
		// Ashore the motor tips up on its bracket, the propeller out of the dirt; the tiller swings with the steering.
		motorTilt.xRot = 1.15f * state.tilt;
		motor.yRot = 0.4f * state.steer;
		propeller.zRot = state.propeller;
		// The feet step in diagonal pairs, swinging fore and aft, as it waddles.
		for (int i = 0; i < feet.length; i++) {
			float phase = state.waddle + (i < 2 ? 0.0f : Mth.PI);
			feet[i].xRot += 0.45f * state.waddleAmount * Mth.sin(phase);
		}
		// Squeezed, the duck squashes flat and springs back.
		float squash = state.duck * state.duck;
		duck.yScale = 1.0f - 0.45f * squash;
		duck.xScale = 1.0f + 0.2f * squash;
		duck.zScale = 1.0f + 0.2f * squash;
		// The oars: out only when it's rowed with no fuel, sweeping like a boat's paddles.
		leftOar.visible = state.rowing;
		rightOar.visible = state.rowing;
		if (state.rowing) {
			row(leftOar, state.rowLeft, 1.0f);
			row(rightOar, state.rowRight, -1.0f);
		}
	}

	/** A stroke: the blade dips at the back of its sweep and comes up for the return. */
	private static void row(ModelPart oar, float time, float side) {
		float sweep = (Mth.sin(-time + 1.0f) + 1.0f) / 2.0f;
		float dip = (Mth.sin(-time) + 1.0f) / 2.0f;
		oar.yRot = side * Mth.lerp(sweep, 0.6f, -0.6f);
		oar.zRot = side * Mth.lerp(dip, 0.05f, 0.45f);
	}
}
