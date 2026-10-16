package com.jheinz.tomyen.client.mech;

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
import com.jheinz.tomyen.mech.Mech;
import com.jheinz.tomyen.mech.MechArm;
import com.jheinz.tomyen.mech.MechLoadout;
import com.jheinz.tomyen.mech.MechShoulder;
import com.jheinz.tomyen.mech.MechWeapon;

/**
 * The Bulwark Walker, front at -z, 4.5 blocks to the tops of its pauldrons and about 2.8 across them.
 * <ul>
 * <li>Legs (turn with the walker): a pelvis with hip anchors, and each leg a thigh with layered plates and hip and
 * knee pistons, a shin with a stepped knee plate and greave, and a broad foot that stays flat as the leg swings.
 * Rungs up the front of the left leg are the pilot's ladder.
 * <li>Torso (turns with its twist on a segmented waist): the armoured sarcophagus with the viewport slit across its
 * top under a brow, flank plates, the chest hatch (hinged along its top edge, lifting for the pilot), a low head with
 * cheek guards, a glowing visor band and a sensor mast, two stepped pauldrons, exhaust stacks and the ammunition
 * bins and feed chutes to the arms on the back, the power plant with its core window and jet nozzles, and the
 * shoulder slot above it: a Cargo Pod or a Smoke Launcher.
 * <li>Arms (hang under the pauldrons; the forearms pitch with the aim, an elbow piston sliding as they do): each
 * forearm carries one of the weapon groups, only the fitted one shown: the autocannon (its barrel, with a muzzle
 * brake, kicks back), the rotary cannon (its six barrels spin), the power fist (it punches forward, and its fingers
 * open to grip), the rocket pod, the flamer, or a bare stub.
 * </ul>
 * No two faces share a plane facing the same way (tools/gen_mech.py checks), so nothing z-fights; the glow layer is
 * the same geometry drawn with an equal-depth test, as vanilla's glowing eyes are.
 * Parts that aren't fitted aren't drawn, so a walker half-built in its gantry shows what it has. Geometry and UVs
 * come from tools/gen_mech.py (which also paints the texture): change them there and paste its --java output.
 */
public class MechModel extends EntityModel<MechRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(MyMod.id("mech"), "main");
	/** The texture the walker and the gantry share (tools/gen_mech.py's TEX_W and TEX_H). */
	public static final int TEXTURE_WIDTH = 512;
	public static final int TEXTURE_HEIGHT = 256;

	private final ModelPart legs;
	private final ModelPart leftLeg;
	private final ModelPart rightLeg;
	private final ModelPart leftShin;
	private final ModelPart rightShin;
	private final ModelPart leftFoot;
	private final ModelPart rightFoot;
	private final ModelPart torso;
	private final ModelPart hatch;
	private final ModelPart[] pauldrons;
	private final ModelPart powerPlant;
	private final ModelPart jetFlames;
	private final ModelPart cargoPod;
	private final ModelPart smokeLauncher;
	private final ModelPart[] arms = new ModelPart[2];
	private final ModelPart[] forearms = new ModelPart[2];
	private final ModelPart[][] weapons = new ModelPart[2][MechWeapon.values().length];
	private final ModelPart[] autocannonBarrels = new ModelPart[2];
	private final ModelPart[] rotaryBarrels = new ModelPart[2];
	private final ModelPart[] fingers = new ModelPart[2];

	public MechModel(ModelPart root) {
		super(root);
		legs = root.getChild("legs");
		leftLeg = legs.getChild("left_leg");
		rightLeg = legs.getChild("right_leg");
		leftShin = leftLeg.getChild("left_shin");
		rightShin = rightLeg.getChild("right_shin");
		leftFoot = leftShin.getChild("left_foot");
		rightFoot = rightShin.getChild("right_foot");
		torso = root.getChild("torso");
		hatch = torso.getChild("hatch");
		pauldrons = new ModelPart[] {torso.getChild("left_pauldron"), torso.getChild("right_pauldron")};
		powerPlant = torso.getChild("power_plant");
		jetFlames = powerPlant.getChild("jet_flames");
		cargoPod = torso.getChild("cargo_pod");
		smokeLauncher = torso.getChild("smoke_launcher");
		for (int side = 0; side < 2; side++) {
			String prefix = side == MechArm.LEFT ? "left_" : "right_";
			arms[side] = torso.getChild(prefix + "arm");
			forearms[side] = arms[side].getChild(prefix + "forearm");
			for (MechWeapon weapon : MechWeapon.values()) {
				weapons[side][weapon.ordinal()] = forearms[side].getChild(prefix + weapon.id());
			}
			autocannonBarrels[side] = weapons[side][MechWeapon.AUTOCANNON.ordinal()].getChild(prefix + "autocannon_barrel");
			rotaryBarrels[side] = weapons[side][MechWeapon.ROTARY_CANNON.ordinal()].getChild(prefix + "rotary_barrels");
			fingers[side] = weapons[side][MechWeapon.POWER_FIST.ordinal()].getChild(prefix + "palm").getChild(prefix + "fingers");
		}
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		// BEGIN GENERATED (tools/gen_mech.py --java)
		PartDefinition legs = root.addOrReplaceChild("legs", CubeListBuilder.create()
				.texOffs(338, 60).addBox(-11.0f, -37.0f, -7.0f, 22.0f, 8.0f, 14.0f)
				.texOffs(340, 112).addBox(-7.0f, -35.5f, -8.5f, 14.0f, 6.0f, 1.5f)
				.texOffs(492, 111).addBox(11.0f, -36.0f, -2.5f, 5.0f, 3.5f, 5.0f)
				.texOffs(492, 111).addBox(-16.0f, -36.0f, -2.5f, 5.0f, 3.5f, 5.0f),
				PartPose.offset(0.0f, 24.0f, 0.0f));
		PartDefinition leftLeg = legs.addOrReplaceChild("left_leg", CubeListBuilder.create()
				.texOffs(338, 35).addBox(-5.5f, 0.0f, -6.0f, 11.0f, 13.0f, 12.0f)
				.texOffs(276, 100).addBox(-4.5f, 1.0f, -7.5f, 9.0f, 10.0f, 1.5f)
				.texOffs(476, 80).addBox(5.5f, 4.0f, -4.5f, 1.5f, 8.0f, 9.0f)
				.texOffs(284, 112).addBox(5.5f, -1.0f, -1.25f, 2.0f, 5.0f, 2.5f)
				.texOffs(76, 114).addBox(5.75f, -4.5f, -0.75f, 1.5f, 4.0f, 1.5f)
				.texOffs(298, 100).addBox(-1.5f, 5.0f, 6.0f, 3.0f, 7.0f, 2.5f)
				.texOffs(146, 114).addBox(-3.0f, 3.0f, -9.5f, 6.0f, 1.0f, 2.0f)
				.texOffs(146, 114).addBox(-3.0f, 8.0f, -9.5f, 6.0f, 1.0f, 2.0f),
				PartPose.offset(8.0f, -31.0f, 0.0f));
		PartDefinition leftShin = leftLeg.addOrReplaceChild("left_shin", CubeListBuilder.create()
				.texOffs(450, 0).addBox(-6.0f, 0.0f, -6.5f, 12.0f, 16.0f, 13.0f)
				.texOffs(250, 100).addBox(-5.0f, -3.0f, -9.5f, 10.0f, 9.0f, 3.0f)
				.texOffs(22, 114).addBox(-3.5f, -3.5f, -10.5f, 7.0f, 5.0f, 1.0f)
				.texOffs(24, 104).addBox(-4.5f, 7.0f, -8.0f, 9.0f, 8.0f, 1.5f)
				.texOffs(298, 110).addBox(-0.75f, -4.0f, 6.75f, 1.5f, 7.0f, 1.5f)
				.texOffs(386, 82).addBox(6.0f, 3.0f, -3.0f, 1.5f, 9.0f, 6.0f)
				.texOffs(146, 114).addBox(-3.0f, 11.0f, -10.0f, 6.0f, 1.0f, 2.0f),
				PartPose.offset(0.0f, 12.0f, 0.0f));
		leftShin.addOrReplaceChild("left_foot", CubeListBuilder.create()
				.texOffs(20, 67).addBox(-7.0f, 0.0f, -11.0f, 14.0f, 3.0f, 18.0f)
				.texOffs(250, 112).addBox(-5.5f, -1.5f, -11.5f, 11.0f, 2.0f, 6.0f)
				.texOffs(0, 114).addBox(-4.0f, -1.0f, 7.0f, 8.0f, 3.5f, 2.5f),
				PartPose.offset(0.0f, 16.0f, 0.0f));
		PartDefinition rightLeg = legs.addOrReplaceChild("right_leg", CubeListBuilder.create()
				.texOffs(338, 35).addBox(-5.5f, 0.0f, -6.0f, 11.0f, 13.0f, 12.0f)
				.texOffs(276, 100).addBox(-4.5f, 1.0f, -7.5f, 9.0f, 10.0f, 1.5f)
				.texOffs(476, 80).addBox(-7.0f, 4.0f, -4.5f, 1.5f, 8.0f, 9.0f)
				.texOffs(284, 112).addBox(-7.5f, -1.0f, -1.25f, 2.0f, 5.0f, 2.5f)
				.texOffs(76, 114).addBox(-7.25f, -4.5f, -0.75f, 1.5f, 4.0f, 1.5f)
				.texOffs(298, 100).addBox(-1.5f, 5.0f, 6.0f, 3.0f, 7.0f, 2.5f),
				PartPose.offset(-8.0f, -31.0f, 0.0f));
		PartDefinition rightShin = rightLeg.addOrReplaceChild("right_shin", CubeListBuilder.create()
				.texOffs(450, 0).addBox(-6.0f, 0.0f, -6.5f, 12.0f, 16.0f, 13.0f)
				.texOffs(250, 100).addBox(-5.0f, -3.0f, -9.5f, 10.0f, 9.0f, 3.0f)
				.texOffs(22, 114).addBox(-3.5f, -3.5f, -10.5f, 7.0f, 5.0f, 1.0f)
				.texOffs(24, 104).addBox(-4.5f, 7.0f, -8.0f, 9.0f, 8.0f, 1.5f)
				.texOffs(298, 110).addBox(-0.75f, -4.0f, 6.75f, 1.5f, 7.0f, 1.5f)
				.texOffs(386, 82).addBox(-7.5f, 3.0f, -3.0f, 1.5f, 9.0f, 6.0f),
				PartPose.offset(0.0f, 12.0f, 0.0f));
		rightShin.addOrReplaceChild("right_foot", CubeListBuilder.create()
				.texOffs(20, 67).addBox(-7.0f, 0.0f, -11.0f, 14.0f, 3.0f, 18.0f)
				.texOffs(250, 112).addBox(-5.5f, -1.5f, -11.5f, 11.0f, 2.0f, 6.0f)
				.texOffs(0, 114).addBox(-4.0f, -1.0f, 7.0f, 8.0f, 3.5f, 2.5f),
				PartPose.offset(0.0f, 16.0f, 0.0f));
		PartDefinition torso = root.addOrReplaceChild("torso", CubeListBuilder.create()
				.texOffs(322, 82).addBox(-8.5f, -3.5f, -7.5f, 17.0f, 1.5f, 15.0f)
				.texOffs(52, 88).addBox(-7.5f, -2.0f, -6.5f, 15.0f, 1.5f, 13.0f)
				.texOffs(322, 82).addBox(-8.5f, -0.5f, -7.5f, 17.0f, 1.5f, 15.0f)
				.texOffs(20, 0).addBox(-12.0f, -27.0f, -10.0f, 24.0f, 24.0f, 20.0f)
				.texOffs(88, 114).addBox(-9.0f, -27.5f, -11.5f, 18.0f, 1.5f, 1.5f)
				.texOffs(350, 0).addBox(11.5f, -25.0f, -8.0f, 2.0f, 18.0f, 16.0f)
				.texOffs(350, 0).addBox(-13.5f, -25.0f, -8.0f, 2.0f, 18.0f, 16.0f)
				.texOffs(272, 83).addBox(-5.0f, -33.5f, -10.5f, 10.0f, 6.5f, 9.5f)
				.texOffs(306, 112).addBox(-5.5f, -34.5f, -11.0f, 11.0f, 1.5f, 6.0f)
				.texOffs(128, 114).addBox(-4.0f, -31.5f, -11.5f, 8.0f, 2.0f, 1.0f)
				.texOffs(460, 111).addBox(5.0f, -31.0f, -10.0f, 1.5f, 4.0f, 6.0f)
				.texOffs(460, 111).addBox(-6.5f, -31.0f, -10.0f, 1.5f, 4.0f, 6.0f)
				.texOffs(294, 112).addBox(3.0f, -38.0f, -3.0f, 1.0f, 4.5f, 1.0f)
				.texOffs(322, 62).addBox(10.0f, -37.0f, 10.0f, 2.5f, 16.0f, 3.0f)
				.texOffs(386, 97).addBox(11.0f, -17.0f, 10.0f, 7.0f, 9.0f, 5.0f)
				.texOffs(196, 103).addBox(14.0f, -16.5f, 3.5f, 3.5f, 3.0f, 7.5f)
				.texOffs(322, 62).addBox(-12.5f, -37.0f, 10.0f, 2.5f, 16.0f, 3.0f)
				.texOffs(386, 97).addBox(-18.0f, -17.0f, 10.0f, 7.0f, 9.0f, 5.0f)
				.texOffs(196, 103).addBox(-17.5f, -16.5f, 3.5f, 3.5f, 3.0f, 7.5f),
				PartPose.offset(0.0f, -13.0f, 0.0f));
		torso.addOrReplaceChild("hatch", CubeListBuilder.create()
				.texOffs(280, 62).addBox(-9.0f, 0.0f, -3.0f, 18.0f, 18.0f, 3.0f)
				.texOffs(448, 98).addBox(-6.0f, 3.0f, -4.0f, 12.0f, 12.0f, 1.0f),
				PartPose.offset(0.0f, -23.0f, -10.0f));
		torso.addOrReplaceChild("left_pauldron", CubeListBuilder.create()
				.texOffs(284, 0).addBox(9.5f, -32.0f, -10.5f, 12.0f, 13.5f, 21.0f)
				.texOffs(84, 68).addBox(10.5f, -35.0f, -8.5f, 10.0f, 3.0f, 17.0f)
				.texOffs(450, 29).addBox(21.5f, -31.0f, -9.5f, 1.5f, 10.0f, 19.0f)
				.texOffs(108, 43).addBox(8.5f, -20.0f, -11.5f, 14.0f, 2.0f, 23.0f),
				PartPose.offset(0.0f, 0.0f, 0.0f));
		torso.addOrReplaceChild("right_pauldron", CubeListBuilder.create()
				.texOffs(284, 0).addBox(-21.5f, -32.0f, -10.5f, 12.0f, 13.5f, 21.0f)
				.texOffs(84, 68).addBox(-20.5f, -35.0f, -8.5f, 10.0f, 3.0f, 17.0f)
				.texOffs(450, 29).addBox(-23.0f, -31.0f, -9.5f, 1.5f, 10.0f, 19.0f)
				.texOffs(108, 43).addBox(-22.5f, -20.0f, -11.5f, 14.0f, 2.0f, 23.0f),
				PartPose.offset(0.0f, 0.0f, 0.0f));
		PartDefinition powerPlant = torso.addOrReplaceChild("power_plant", CubeListBuilder.create()
				.texOffs(182, 43).addBox(-9.0f, -25.0f, 10.0f, 18.0f, 18.0f, 7.0f)
				.texOffs(380, 111).addBox(-7.5f, -7.0f, 11.0f, 5.0f, 5.0f, 5.0f)
				.texOffs(380, 111).addBox(2.5f, -7.0f, 11.0f, 5.0f, 5.0f, 5.0f),
				PartPose.offset(0.0f, 0.0f, 0.0f));
		powerPlant.addOrReplaceChild("jet_flames", CubeListBuilder.create()
				.texOffs(0, 92).addBox(-7.0f, -2.0f, 12.0f, 4.0f, 7.0f, 4.0f)
				.texOffs(0, 92).addBox(3.0f, -2.0f, 12.0f, 4.0f, 7.0f, 4.0f),
				PartPose.offset(0.0f, 0.0f, 0.0f));
		torso.addOrReplaceChild("cargo_pod", CubeListBuilder.create()
				.texOffs(442, 58).addBox(-9.0f, -35.0f, 4.0f, 18.0f, 10.0f, 12.0f),
				PartPose.offset(0.0f, 0.0f, 0.0f));
		torso.addOrReplaceChild("smoke_launcher", CubeListBuilder.create()
				.texOffs(312, 99).addBox(-7.0f, -4.0f, -4.0f, 14.0f, 5.0f, 8.0f),
				PartPose.offsetAndRotation(0.0f, -30.0f, 11.0f, -0.35f, 0.0f, 0.0f));
		PartDefinition leftArm = torso.addOrReplaceChild("left_arm", CubeListBuilder.create()
				.texOffs(448, 80).addBox(-3.5f, 0.0f, -3.5f, 7.0f, 11.0f, 7.0f)
				.texOffs(220, 103).addBox(-4.25f, 7.5f, -3.0f, 8.5f, 4.0f, 6.0f)
				.texOffs(372, 112).addBox(-1.0f, 1.5f, -5.0f, 2.0f, 5.5f, 1.5f),
				PartPose.offset(16.0f, -19.0f, 0.0f));
		PartDefinition leftForearm = leftArm.addOrReplaceChild("left_forearm", CubeListBuilder.create()
				.texOffs(84, 114).addBox(-0.5f, -7.0f, -4.75f, 1.0f, 5.0f, 1.0f),
				PartPose.offset(0.0f, 10.0f, 0.0f));
		leftForearm.addOrReplaceChild("left_none", CubeListBuilder.create()
				.texOffs(232, 67).addBox(-4.0f, -4.0f, -10.0f, 8.0f, 8.0f, 12.0f),
				PartPose.offset(0.0f, 0.0f, 0.0f));
		PartDefinition leftAutocannon = leftForearm.addOrReplaceChild("left_autocannon", CubeListBuilder.create()
				.texOffs(386, 32).addBox(-5.0f, -5.0f, -14.0f, 10.0f, 10.0f, 18.0f)
				.texOffs(410, 99).addBox(-3.0f, -9.0f, -10.0f, 6.0f, 4.0f, 8.0f)
				.texOffs(200, 88).addBox(5.0f, -3.0f, -9.0f, 3.0f, 6.0f, 6.0f),
				PartPose.offset(0.0f, 0.0f, 0.0f));
		leftAutocannon.addOrReplaceChild("left_autocannon_barrel", CubeListBuilder.create()
				.texOffs(138, 68).addBox(-2.0f, -2.0f, -16.0f, 4.0f, 4.0f, 15.5f)
				.texOffs(400, 111).addBox(-3.0f, -3.0f, -4.0f, 6.0f, 6.0f, 4.0f)
				.texOffs(236, 113).addBox(-2.5f, -2.5f, -10.5f, 5.0f, 5.0f, 1.5f)
				.texOffs(420, 111).addBox(-3.0f, -3.0f, -20.0f, 6.0f, 6.0f, 4.0f),
				PartPose.offset(0.0f, 0.0f, -14.0f));
		PartDefinition leftRotaryCannon = leftForearm.addOrReplaceChild("left_rotary_cannon", CubeListBuilder.create()
				.texOffs(20, 44).addBox(-5.0f, -5.0f, -10.5f, 10.0f, 10.0f, 12.5f)
				.texOffs(440, 111).addBox(-4.0f, -4.0f, -12.0f, 8.0f, 8.0f, 2.0f)
				.texOffs(0, 104).addBox(-2.5f, -8.0f, -8.0f, 5.0f, 3.0f, 7.0f)
				.texOffs(476, 111).addBox(5.0f, -2.0f, -7.0f, 2.0f, 4.0f, 6.0f),
				PartPose.offset(0.0f, 0.0f, 0.0f));
		leftRotaryCannon.addOrReplaceChild("left_rotary_barrels", CubeListBuilder.create()
				.texOffs(218, 87).addBox(1.55f, -0.75f, -14.0f, 1.5f, 1.5f, 14.0f)
				.texOffs(218, 87).addBox(0.4f, 1.24f, -14.0f, 1.5f, 1.5f, 14.0f)
				.texOffs(218, 87).addBox(-1.9f, 1.24f, -14.0f, 1.5f, 1.5f, 14.0f)
				.texOffs(218, 87).addBox(-3.05f, -0.75f, -14.0f, 1.5f, 1.5f, 14.0f)
				.texOffs(218, 87).addBox(-1.9f, -2.74f, -14.0f, 1.5f, 1.5f, 14.0f)
				.texOffs(218, 87).addBox(0.4f, -2.74f, -14.0f, 1.5f, 1.5f, 14.0f)
				.texOffs(20, 88).addBox(-0.8f, -0.8f, -14.5f, 1.6f, 1.6f, 14.0f)
				.texOffs(220, 113).addBox(-3.4f, -3.4f, -10.0f, 6.8f, 6.8f, 1.0f)
				.texOffs(220, 113).addBox(-3.4f, -3.4f, -13.0f, 6.8f, 6.8f, 1.0f),
				PartPose.offset(0.0f, 0.0f, -12.0f));
		PartDefinition leftPowerFist = leftForearm.addOrReplaceChild("left_power_fist", CubeListBuilder.create()
				.texOffs(232, 43).addBox(-5.0f, -5.0f, -12.0f, 10.0f, 10.0f, 14.0f)
				.texOffs(162, 103).addBox(-4.0f, -6.5f, -11.0f, 8.0f, 1.5f, 9.0f)
				.texOffs(356, 99).addBox(5.0f, -1.5f, -11.0f, 1.5f, 3.0f, 10.0f)
				.texOffs(356, 99).addBox(-6.5f, -1.5f, -11.0f, 1.5f, 3.0f, 10.0f),
				PartPose.offset(0.0f, 0.0f, 0.0f));
		PartDefinition leftPalm = leftPowerFist.addOrReplaceChild("left_palm", CubeListBuilder.create()
				.texOffs(410, 80).addBox(-6.0f, -6.0f, -7.0f, 12.0f, 12.0f, 7.0f),
				PartPose.offset(0.0f, 0.0f, -12.0f));
		leftPalm.addOrReplaceChild("left_fingers", CubeListBuilder.create()
				.texOffs(108, 88).addBox(-6.0f, 0.0f, -4.0f, 12.0f, 11.0f, 4.0f),
				PartPose.offset(0.0f, -6.0f, -7.0f));
		leftForearm.addOrReplaceChild("left_rocket_pod", CubeListBuilder.create()
				.texOffs(284, 35).addBox(-6.0f, -6.5f, -13.0f, 12.0f, 12.0f, 15.0f)
				.texOffs(140, 88).addBox(-6.5f, -7.25f, -11.0f, 13.0f, 13.0f, 2.0f)
				.texOffs(38, 114).addBox(-1.5f, -7.5f, -6.0f, 3.0f, 1.5f, 4.0f),
				PartPose.offset(0.0f, 0.0f, 0.0f));
		leftForearm.addOrReplaceChild("left_flamer", CubeListBuilder.create()
				.texOffs(178, 68).addBox(-4.0f, -4.0f, -10.0f, 8.0f, 8.0f, 12.0f)
				.texOffs(476, 97).addBox(-2.5f, -9.0f, -8.0f, 5.0f, 5.0f, 9.0f)
				.texOffs(170, 88).addBox(-1.5f, -1.5f, -22.0f, 3.0f, 3.0f, 12.0f)
				.texOffs(52, 114).addBox(-2.5f, -2.5f, -18.0f, 5.0f, 5.0f, 1.0f)
				.texOffs(64, 114).addBox(-2.0f, -2.0f, -24.0f, 4.0f, 4.0f, 2.0f),
				PartPose.offset(0.0f, 0.0f, 0.0f));
		PartDefinition rightArm = torso.addOrReplaceChild("right_arm", CubeListBuilder.create()
				.texOffs(448, 80).addBox(-3.5f, 0.0f, -3.5f, 7.0f, 11.0f, 7.0f)
				.texOffs(220, 103).addBox(-4.25f, 7.5f, -3.0f, 8.5f, 4.0f, 6.0f)
				.texOffs(372, 112).addBox(-1.0f, 1.5f, -5.0f, 2.0f, 5.5f, 1.5f),
				PartPose.offset(-16.0f, -19.0f, 0.0f));
		PartDefinition rightForearm = rightArm.addOrReplaceChild("right_forearm", CubeListBuilder.create()
				.texOffs(84, 114).addBox(-0.5f, -7.0f, -4.75f, 1.0f, 5.0f, 1.0f),
				PartPose.offset(0.0f, 10.0f, 0.0f));
		rightForearm.addOrReplaceChild("right_none", CubeListBuilder.create()
				.texOffs(232, 67).addBox(-4.0f, -4.0f, -10.0f, 8.0f, 8.0f, 12.0f),
				PartPose.offset(0.0f, 0.0f, 0.0f));
		PartDefinition rightAutocannon = rightForearm.addOrReplaceChild("right_autocannon", CubeListBuilder.create()
				.texOffs(386, 32).addBox(-5.0f, -5.0f, -14.0f, 10.0f, 10.0f, 18.0f)
				.texOffs(410, 99).addBox(-3.0f, -9.0f, -10.0f, 6.0f, 4.0f, 8.0f)
				.texOffs(200, 88).addBox(-8.0f, -3.0f, -9.0f, 3.0f, 6.0f, 6.0f),
				PartPose.offset(0.0f, 0.0f, 0.0f));
		rightAutocannon.addOrReplaceChild("right_autocannon_barrel", CubeListBuilder.create()
				.texOffs(138, 68).addBox(-2.0f, -2.0f, -16.0f, 4.0f, 4.0f, 15.5f)
				.texOffs(400, 111).addBox(-3.0f, -3.0f, -4.0f, 6.0f, 6.0f, 4.0f)
				.texOffs(236, 113).addBox(-2.5f, -2.5f, -10.5f, 5.0f, 5.0f, 1.5f)
				.texOffs(420, 111).addBox(-3.0f, -3.0f, -20.0f, 6.0f, 6.0f, 4.0f),
				PartPose.offset(0.0f, 0.0f, -14.0f));
		PartDefinition rightRotaryCannon = rightForearm.addOrReplaceChild("right_rotary_cannon", CubeListBuilder.create()
				.texOffs(20, 44).addBox(-5.0f, -5.0f, -10.5f, 10.0f, 10.0f, 12.5f)
				.texOffs(440, 111).addBox(-4.0f, -4.0f, -12.0f, 8.0f, 8.0f, 2.0f)
				.texOffs(0, 104).addBox(-2.5f, -8.0f, -8.0f, 5.0f, 3.0f, 7.0f)
				.texOffs(476, 111).addBox(-7.0f, -2.0f, -7.0f, 2.0f, 4.0f, 6.0f),
				PartPose.offset(0.0f, 0.0f, 0.0f));
		rightRotaryCannon.addOrReplaceChild("right_rotary_barrels", CubeListBuilder.create()
				.texOffs(218, 87).addBox(1.55f, -0.75f, -14.0f, 1.5f, 1.5f, 14.0f)
				.texOffs(218, 87).addBox(0.4f, 1.24f, -14.0f, 1.5f, 1.5f, 14.0f)
				.texOffs(218, 87).addBox(-1.9f, 1.24f, -14.0f, 1.5f, 1.5f, 14.0f)
				.texOffs(218, 87).addBox(-3.05f, -0.75f, -14.0f, 1.5f, 1.5f, 14.0f)
				.texOffs(218, 87).addBox(-1.9f, -2.74f, -14.0f, 1.5f, 1.5f, 14.0f)
				.texOffs(218, 87).addBox(0.4f, -2.74f, -14.0f, 1.5f, 1.5f, 14.0f)
				.texOffs(20, 88).addBox(-0.8f, -0.8f, -14.5f, 1.6f, 1.6f, 14.0f)
				.texOffs(220, 113).addBox(-3.4f, -3.4f, -10.0f, 6.8f, 6.8f, 1.0f)
				.texOffs(220, 113).addBox(-3.4f, -3.4f, -13.0f, 6.8f, 6.8f, 1.0f),
				PartPose.offset(0.0f, 0.0f, -12.0f));
		PartDefinition rightPowerFist = rightForearm.addOrReplaceChild("right_power_fist", CubeListBuilder.create()
				.texOffs(232, 43).addBox(-5.0f, -5.0f, -12.0f, 10.0f, 10.0f, 14.0f)
				.texOffs(162, 103).addBox(-4.0f, -6.5f, -11.0f, 8.0f, 1.5f, 9.0f)
				.texOffs(356, 99).addBox(5.0f, -1.5f, -11.0f, 1.5f, 3.0f, 10.0f)
				.texOffs(356, 99).addBox(-6.5f, -1.5f, -11.0f, 1.5f, 3.0f, 10.0f),
				PartPose.offset(0.0f, 0.0f, 0.0f));
		PartDefinition rightPalm = rightPowerFist.addOrReplaceChild("right_palm", CubeListBuilder.create()
				.texOffs(410, 80).addBox(-6.0f, -6.0f, -7.0f, 12.0f, 12.0f, 7.0f),
				PartPose.offset(0.0f, 0.0f, -12.0f));
		rightPalm.addOrReplaceChild("right_fingers", CubeListBuilder.create()
				.texOffs(108, 88).addBox(-6.0f, 0.0f, -4.0f, 12.0f, 11.0f, 4.0f),
				PartPose.offset(0.0f, -6.0f, -7.0f));
		rightForearm.addOrReplaceChild("right_rocket_pod", CubeListBuilder.create()
				.texOffs(284, 35).addBox(-6.0f, -6.5f, -13.0f, 12.0f, 12.0f, 15.0f)
				.texOffs(140, 88).addBox(-6.5f, -7.25f, -11.0f, 13.0f, 13.0f, 2.0f)
				.texOffs(38, 114).addBox(-1.5f, -7.5f, -6.0f, 3.0f, 1.5f, 4.0f),
				PartPose.offset(0.0f, 0.0f, 0.0f));
		rightForearm.addOrReplaceChild("right_flamer", CubeListBuilder.create()
				.texOffs(178, 68).addBox(-4.0f, -4.0f, -10.0f, 8.0f, 8.0f, 12.0f)
				.texOffs(476, 97).addBox(-2.5f, -9.0f, -8.0f, 5.0f, 5.0f, 9.0f)
				.texOffs(170, 88).addBox(-1.5f, -1.5f, -22.0f, 3.0f, 3.0f, 12.0f)
				.texOffs(52, 114).addBox(-2.5f, -2.5f, -18.0f, 5.0f, 5.0f, 1.0f)
				.texOffs(64, 114).addBox(-2.0f, -2.0f, -24.0f, 4.0f, 4.0f, 2.0f),
				PartPose.offset(0.0f, 0.0f, 0.0f));
		// END GENERATED
		return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
	}

	/**
	 * For the cockpit view (see MechFirstPerson): an arm's forearm and its weapon, posed by {@link #setupAnim} (the
	 * barrels' kick and spin, the punch, the grip), set to draw from its elbow, unpitched.
	 */
	ModelPart firstPersonForearm(int side) {
		ModelPart forearm = forearms[side];
		forearm.x = 0.0f;
		forearm.y = 0.0f;
		forearm.xRot = 0.0f;
		return forearm;
	}

	@Override
	public void setupAnim(MechRenderState state) {
		super.setupAnim(state);
		int look = state.look;
		// What's fitted. The torso's own plate is skipped (but not its arms and the rest) if only the torso is missing.
		legs.visible = MechLoadout.has(look, MechLoadout.LEGS);
		torso.skipDraw = !MechLoadout.has(look, MechLoadout.TORSO);
		hatch.visible = !torso.skipDraw;
		for (ModelPart pauldron : pauldrons) {
			pauldron.visible = !torso.skipDraw;
		}
		powerPlant.visible = MechLoadout.has(look, MechLoadout.POWER_PLANT);
		jetFlames.visible = state.jetFlame > 0.0f;
		jetFlames.yScale = 0.4f + 0.6f * state.jetFlame;
		MechShoulder shoulder = MechLoadout.shoulder(look);
		cargoPod.visible = shoulder == MechShoulder.CARGO_POD;
		smokeLauncher.visible = shoulder == MechShoulder.SMOKE_LAUNCHER;

		torso.yRot = state.twist * Mth.DEG_TO_RAD;
		hatch.xRot = -1.9f * state.hatch;
		for (int side = 0; side < 2; side++) {
			arms[side].visible = MechLoadout.has(look, side == MechArm.LEFT ? MechLoadout.LEFT_ARM : MechLoadout.RIGHT_ARM);
			MechWeapon fitted = MechLoadout.weapon(look, side);
			for (MechWeapon weapon : MechWeapon.values()) {
				weapons[side][weapon.ordinal()].visible = weapon == fitted;
			}
			forearms[side].xRot = state.armPitch * Mth.DEG_TO_RAD;
			float kick = state.recoil[side];
			switch (fitted) {
				case AUTOCANNON -> autocannonBarrels[side].z += 5.0f * kick;
				case ROCKET_POD, FLAMER -> forearms[side].z += 2.0f * kick;
				// A punch drives the whole forearm out and back.
				case POWER_FIST -> forearms[side].z -= 7.0f * Mth.sin(kick * Mth.PI);
				default -> {
				}
			}
			rotaryBarrels[side].zRot = state.spin[side];
			fingers[side].xRot = -1.1f * state.grip[side];
		}

		walk(state.walkAnimationPos * Mech.WALK_CYCLE, Math.min(1.0f, state.walkAnimationSpeed * 1.6f));
	}

	/**
	 * A heavy stride. The thighs swing, the trailing knee lifts the foot clear, and the feet land flat. A foot plants
	 * at each half turn of {@code phase} (the right at whole turns: see Mech#tickFootfalls) and the body drops onto it:
	 * a sharp dip at the plant that eases back up through the step, with the weight swaying over the planted leg.
	 */
	private void walk(float phase, float stride) {
		float swing = Mth.cos(phase) * 0.5f * stride;
		leftLeg.xRot = swing;
		rightLeg.xRot = -swing;
		leftShin.xRot = Math.max(0.0f, Mth.sin(phase)) * 0.75f * stride;
		rightShin.xRot = Math.max(0.0f, -Mth.sin(phase)) * 0.75f * stride;
		leftFoot.xRot = -(leftLeg.xRot + leftShin.xRot);
		rightFoot.xRot = -(rightLeg.xRot + rightShin.xRot);
		// Time since the last plant, 0 to 1 over the half turn: the drop is sharp, the recovery slow.
		float sincePlant = (phase / Mth.PI) - Mth.floor(phase / Mth.PI);
		float drop = (1.0f - sincePlant) * (1.0f - sincePlant) * (1.0f - sincePlant);
		float dip = (0.4f + 2.2f * drop) * stride;
		torso.y += dip;
		legs.y += dip * 0.6f;
		// The weight rides over the planted foot (the right one through the first half turn); +x is the walker's left.
		float sway = -Mth.cos(phase);
		torso.x += sway * 1.1f * stride;
		torso.zRot = sway * 0.04f * stride;
	}
}
