package com.jheinz.mymod.client.mech;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.mech.MechArm;
import com.jheinz.mymod.mech.MechLoadout;
import com.jheinz.mymod.mech.MechShoulder;
import com.jheinz.mymod.mech.MechWeapon;

/**
 * The Bulwark Walker, front at -z, 4.5 blocks to the tops of its pauldrons and about 2.8 across them.
 * <ul>
 * <li>Legs (turn with the walker): a pelvis, and each leg a thigh, a shin with a knee plate and a broad foot that
 * stays flat as the leg swings. Rungs up the front of the left leg are the pilot's ladder.
 * <li>Torso (turns with its twist on the waist ring): the armoured sarcophagus with the viewport slit across its
 * top, the chest hatch (hinged along its top edge, lifting for the pilot), a low head with a visor, two huge
 * pauldrons, the power plant on the back with its core window and jet nozzles, and the shoulder slot above it:
 * a Cargo Pod or a Smoke Launcher.
 * <li>Arms (hang under the pauldrons; the forearms pitch with the aim): each forearm carries one of the weapon
 * groups, only the fitted one shown: the autocannon (its barrel kicks back), the rotary cannon (its barrels spin),
 * the power fist (it punches forward, and its fingers open to grip), the rocket pod, the flamer, or a bare stub.
 * </ul>
 * Parts that aren't fitted aren't drawn, so a walker half-built in its gantry shows what it has. Geometry and UVs
 * come from tools/gen_mech.py (which also paints the texture): change them there and paste its --java output.
 */
public class MechModel extends EntityModel<MechRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(MyMod.id("mech"), "main");

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
				.texOffs(0, 122).addBox(-11.0f, -37.0f, -7.0f, 22.0f, 8.0f, 14.0f),
				PartPose.offset(0.0f, 24.0f, 0.0f));
		PartDefinition leftLeg = legs.addOrReplaceChild("left_leg", CubeListBuilder.create()
				.texOffs(200, 112).addBox(-5.5f, 0.0f, -6.0f, 11.0f, 13.0f, 12.0f)
				.texOffs(232, 209).addBox(-3.0f, 3.0f, -8.0f, 6.0f, 1.0f, 2.0f)
				.texOffs(232, 209).addBox(-3.0f, 8.0f, -8.0f, 6.0f, 1.0f, 2.0f),
				PartPose.offset(8.0f, -31.0f, 0.0f));
		PartDefinition leftShin = leftLeg.addOrReplaceChild("left_shin", CubeListBuilder.create()
				.texOffs(196, 0).addBox(-6.0f, 0.0f, -6.5f, 12.0f, 16.0f, 13.0f)
				.texOffs(0, 200).addBox(-5.0f, -3.0f, -9.5f, 10.0f, 9.0f, 3.0f)
				.texOffs(232, 209).addBox(-3.0f, 11.0f, -8.5f, 6.0f, 1.0f, 2.0f),
				PartPose.offset(0.0f, 12.0f, 0.0f));
		leftShin.addOrReplaceChild("left_foot", CubeListBuilder.create()
				.texOffs(0, 144).addBox(-7.0f, 0.0f, -11.0f, 14.0f, 3.0f, 18.0f),
				PartPose.offset(0.0f, 16.0f, 0.0f));
		PartDefinition rightLeg = legs.addOrReplaceChild("right_leg", CubeListBuilder.create()
				.texOffs(200, 112).addBox(-5.5f, 0.0f, -6.0f, 11.0f, 13.0f, 12.0f),
				PartPose.offset(-8.0f, -31.0f, 0.0f));
		PartDefinition rightShin = rightLeg.addOrReplaceChild("right_shin", CubeListBuilder.create()
				.texOffs(196, 0).addBox(-6.0f, 0.0f, -6.5f, 12.0f, 16.0f, 13.0f)
				.texOffs(0, 200).addBox(-5.0f, -3.0f, -9.5f, 10.0f, 9.0f, 3.0f),
				PartPose.offset(0.0f, 12.0f, 0.0f));
		rightShin.addOrReplaceChild("right_foot", CubeListBuilder.create()
				.texOffs(0, 144).addBox(-7.0f, 0.0f, -11.0f, 14.0f, 3.0f, 18.0f),
				PartPose.offset(0.0f, 16.0f, 0.0f));
		PartDefinition torso = root.addOrReplaceChild("torso", CubeListBuilder.create()
				.texOffs(174, 179).addBox(-8.0f, -3.0f, -7.0f, 16.0f, 4.0f, 14.0f)
				.texOffs(20, 0).addBox(-12.0f, -27.0f, -10.0f, 24.0f, 24.0f, 20.0f)
				.texOffs(40, 184).addBox(-5.0f, -34.0f, -11.0f, 10.0f, 7.0f, 10.0f),
				PartPose.offset(0.0f, -13.0f, 0.0f));
		torso.addOrReplaceChild("hatch", CubeListBuilder.create()
				.texOffs(72, 143).addBox(-9.0f, 0.0f, -3.0f, 18.0f, 18.0f, 3.0f),
				PartPose.offset(0.0f, -23.0f, -10.0f));
		torso.addOrReplaceChild("left_pauldron", CubeListBuilder.create()
				.texOffs(20, 87).addBox(9.5f, -32.0f, -10.5f, 12.0f, 14.0f, 21.0f)
				.texOffs(114, 159).addBox(10.5f, -35.0f, -8.5f, 10.0f, 3.0f, 17.0f)
				.texOffs(150, 87).addBox(8.5f, -20.0f, -11.5f, 14.0f, 2.0f, 23.0f),
				PartPose.offset(0.0f, 0.0f, 0.0f));
		torso.addOrReplaceChild("right_pauldron", CubeListBuilder.create()
				.texOffs(20, 87).addBox(-21.5f, -32.0f, -10.5f, 12.0f, 14.0f, 21.0f)
				.texOffs(114, 159).addBox(-20.5f, -35.0f, -8.5f, 10.0f, 3.0f, 17.0f)
				.texOffs(150, 87).addBox(-22.5f, -20.0f, -11.5f, 14.0f, 2.0f, 23.0f),
				PartPose.offset(0.0f, 0.0f, 0.0f));
		PartDefinition powerPlant = torso.addOrReplaceChild("power_plant", CubeListBuilder.create()
				.texOffs(150, 112).addBox(-9.0f, -25.0f, 10.0f, 18.0f, 18.0f, 7.0f)
				.texOffs(234, 190).addBox(-7.5f, -7.0f, 11.0f, 5.0f, 5.0f, 5.0f)
				.texOffs(234, 190).addBox(2.5f, -7.0f, 11.0f, 5.0f, 5.0f, 5.0f),
				PartPose.offset(0.0f, 0.0f, 0.0f));
		powerPlant.addOrReplaceChild("jet_flames", CubeListBuilder.create()
				.texOffs(234, 179).addBox(-7.0f, -2.0f, 12.0f, 4.0f, 7.0f, 4.0f)
				.texOffs(234, 179).addBox(3.0f, -2.0f, 12.0f, 4.0f, 7.0f, 4.0f),
				PartPose.offset(0.0f, 0.0f, 0.0f));
		torso.addOrReplaceChild("cargo_pod", CubeListBuilder.create()
				.texOffs(134, 137).addBox(-9.0f, -35.0f, 4.0f, 18.0f, 10.0f, 12.0f),
				PartPose.offset(0.0f, 0.0f, 0.0f));
		torso.addOrReplaceChild("smoke_launcher", CubeListBuilder.create()
				.texOffs(80, 198).addBox(-7.0f, -4.0f, -4.0f, 14.0f, 5.0f, 8.0f),
				PartPose.offsetAndRotation(0.0f, -30.0f, 11.0f, -0.35f, 0.0f, 0.0f));
		PartDefinition leftArm = torso.addOrReplaceChild("left_arm", CubeListBuilder.create()
				.texOffs(142, 179).addBox(-4.0f, 0.0f, -4.0f, 8.0f, 11.0f, 8.0f),
				PartPose.offset(16.0f, -19.0f, 0.0f));
		PartDefinition leftForearm = leftArm.addOrReplaceChild("left_forearm", CubeListBuilder.create(),
				PartPose.offset(0.0f, 10.0f, 0.0f));
		leftForearm.addOrReplaceChild("left_none", CubeListBuilder.create()
				.texOffs(168, 159).addBox(-4.0f, -4.0f, -10.0f, 8.0f, 8.0f, 12.0f),
				PartPose.offset(0.0f, 0.0f, 0.0f));
		PartDefinition leftAutocannon = leftForearm.addOrReplaceChild("left_autocannon", CubeListBuilder.create()
				.texOffs(196, 29).addBox(-5.0f, -5.0f, -14.0f, 10.0f, 10.0f, 18.0f)
				.texOffs(124, 198).addBox(-3.0f, -9.0f, -10.0f, 6.0f, 4.0f, 8.0f),
				PartPose.offset(0.0f, 0.0f, 0.0f));
		leftAutocannon.addOrReplaceChild("left_autocannon_barrel", CubeListBuilder.create()
				.texOffs(208, 159).addBox(-2.0f, -2.0f, -16.0f, 4.0f, 4.0f, 16.0f)
				.texOffs(232, 200).addBox(-3.0f, -3.0f, -19.0f, 6.0f, 6.0f, 3.0f),
				PartPose.offset(0.0f, 0.0f, -14.0f));
		PartDefinition leftRotaryCannon = leftForearm.addOrReplaceChild("left_rotary_cannon", CubeListBuilder.create()
				.texOffs(194, 137).addBox(-5.0f, -5.0f, -10.0f, 10.0f, 10.0f, 12.0f)
				.texOffs(152, 198).addBox(-4.0f, -4.0f, -12.0f, 8.0f, 8.0f, 2.0f),
				PartPose.offset(0.0f, 0.0f, 0.0f));
		leftRotaryCannon.addOrReplaceChild("left_rotary_barrels", CubeListBuilder.create()
				.texOffs(64, 164).addBox(-3.0f, -3.0f, -14.0f, 6.0f, 6.0f, 14.0f),
				PartPose.offset(0.0f, 0.0f, -12.0f));
		PartDefinition leftPowerFist = leftForearm.addOrReplaceChild("left_power_fist", CubeListBuilder.create()
				.texOffs(86, 119).addBox(-5.0f, -5.0f, -12.0f, 10.0f, 10.0f, 14.0f),
				PartPose.offset(0.0f, 0.0f, 0.0f));
		PartDefinition leftPalm = leftPowerFist.addOrReplaceChild("left_palm", CubeListBuilder.create()
				.texOffs(104, 179).addBox(-6.0f, -6.0f, -7.0f, 12.0f, 12.0f, 7.0f),
				PartPose.offset(0.0f, 0.0f, -12.0f));
		leftPalm.addOrReplaceChild("left_fingers", CubeListBuilder.create()
				.texOffs(0, 185).addBox(-6.0f, 0.0f, -4.0f, 12.0f, 11.0f, 4.0f),
				PartPose.offset(0.0f, -6.0f, -7.0f));
		leftForearm.addOrReplaceChild("left_rocket_pod", CubeListBuilder.create()
				.texOffs(196, 57).addBox(-6.0f, -6.0f, -13.0f, 12.0f, 12.0f, 15.0f),
				PartPose.offset(0.0f, 0.0f, 0.0f));
		leftForearm.addOrReplaceChild("left_flamer", CubeListBuilder.create()
				.texOffs(0, 165).addBox(-4.0f, -4.0f, -10.0f, 8.0f, 8.0f, 12.0f)
				.texOffs(204, 197).addBox(-2.5f, -9.0f, -8.0f, 5.0f, 5.0f, 9.0f)
				.texOffs(174, 197).addBox(-1.5f, -1.5f, -22.0f, 3.0f, 3.0f, 12.0f)
				.texOffs(152, 208).addBox(-2.0f, -2.0f, -24.0f, 4.0f, 4.0f, 2.0f),
				PartPose.offset(0.0f, 0.0f, 0.0f));
		PartDefinition rightArm = torso.addOrReplaceChild("right_arm", CubeListBuilder.create()
				.texOffs(142, 179).addBox(-4.0f, 0.0f, -4.0f, 8.0f, 11.0f, 8.0f),
				PartPose.offset(-16.0f, -19.0f, 0.0f));
		PartDefinition rightForearm = rightArm.addOrReplaceChild("right_forearm", CubeListBuilder.create(),
				PartPose.offset(0.0f, 10.0f, 0.0f));
		rightForearm.addOrReplaceChild("right_none", CubeListBuilder.create()
				.texOffs(168, 159).addBox(-4.0f, -4.0f, -10.0f, 8.0f, 8.0f, 12.0f),
				PartPose.offset(0.0f, 0.0f, 0.0f));
		PartDefinition rightAutocannon = rightForearm.addOrReplaceChild("right_autocannon", CubeListBuilder.create()
				.texOffs(196, 29).addBox(-5.0f, -5.0f, -14.0f, 10.0f, 10.0f, 18.0f)
				.texOffs(124, 198).addBox(-3.0f, -9.0f, -10.0f, 6.0f, 4.0f, 8.0f),
				PartPose.offset(0.0f, 0.0f, 0.0f));
		rightAutocannon.addOrReplaceChild("right_autocannon_barrel", CubeListBuilder.create()
				.texOffs(208, 159).addBox(-2.0f, -2.0f, -16.0f, 4.0f, 4.0f, 16.0f)
				.texOffs(232, 200).addBox(-3.0f, -3.0f, -19.0f, 6.0f, 6.0f, 3.0f),
				PartPose.offset(0.0f, 0.0f, -14.0f));
		PartDefinition rightRotaryCannon = rightForearm.addOrReplaceChild("right_rotary_cannon", CubeListBuilder.create()
				.texOffs(194, 137).addBox(-5.0f, -5.0f, -10.0f, 10.0f, 10.0f, 12.0f)
				.texOffs(152, 198).addBox(-4.0f, -4.0f, -12.0f, 8.0f, 8.0f, 2.0f),
				PartPose.offset(0.0f, 0.0f, 0.0f));
		rightRotaryCannon.addOrReplaceChild("right_rotary_barrels", CubeListBuilder.create()
				.texOffs(64, 164).addBox(-3.0f, -3.0f, -14.0f, 6.0f, 6.0f, 14.0f),
				PartPose.offset(0.0f, 0.0f, -12.0f));
		PartDefinition rightPowerFist = rightForearm.addOrReplaceChild("right_power_fist", CubeListBuilder.create()
				.texOffs(86, 119).addBox(-5.0f, -5.0f, -12.0f, 10.0f, 10.0f, 14.0f),
				PartPose.offset(0.0f, 0.0f, 0.0f));
		PartDefinition rightPalm = rightPowerFist.addOrReplaceChild("right_palm", CubeListBuilder.create()
				.texOffs(104, 179).addBox(-6.0f, -6.0f, -7.0f, 12.0f, 12.0f, 7.0f),
				PartPose.offset(0.0f, 0.0f, -12.0f));
		rightPalm.addOrReplaceChild("right_fingers", CubeListBuilder.create()
				.texOffs(0, 185).addBox(-6.0f, 0.0f, -4.0f, 12.0f, 11.0f, 4.0f),
				PartPose.offset(0.0f, -6.0f, -7.0f));
		rightForearm.addOrReplaceChild("right_rocket_pod", CubeListBuilder.create()
				.texOffs(196, 57).addBox(-6.0f, -6.0f, -13.0f, 12.0f, 12.0f, 15.0f),
				PartPose.offset(0.0f, 0.0f, 0.0f));
		rightForearm.addOrReplaceChild("right_flamer", CubeListBuilder.create()
				.texOffs(0, 165).addBox(-4.0f, -4.0f, -10.0f, 8.0f, 8.0f, 12.0f)
				.texOffs(204, 197).addBox(-2.5f, -9.0f, -8.0f, 5.0f, 5.0f, 9.0f)
				.texOffs(174, 197).addBox(-1.5f, -1.5f, -22.0f, 3.0f, 3.0f, 12.0f)
				.texOffs(152, 208).addBox(-2.0f, -2.0f, -24.0f, 4.0f, 4.0f, 2.0f),
				PartPose.offset(0.0f, 0.0f, 0.0f));
		// END GENERATED
		return LayerDefinition.create(mesh, 256, 256);
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

		// A heavy stride: thighs swing, knees bend on the back swing, feet stay flat, and the torso dips at each footfall.
		float phase = state.walkAnimationPos * 0.46f;
		float stride = Math.min(1.0f, state.walkAnimationSpeed * 1.6f);
		float swing = Mth.cos(phase) * 0.5f * stride;
		leftLeg.xRot = swing;
		rightLeg.xRot = -swing;
		leftShin.xRot = Math.max(0.0f, Mth.sin(phase)) * 0.7f * stride;
		rightShin.xRot = Math.max(0.0f, -Mth.sin(phase)) * 0.7f * stride;
		leftFoot.xRot = -(leftLeg.xRot + leftShin.xRot);
		rightFoot.xRot = -(rightLeg.xRot + rightShin.xRot);
		float dip = Math.abs(Mth.cos(phase)) * 1.2f * stride;
		torso.y += dip;
		legs.y += dip * 0.5f;
		torso.zRot = Mth.sin(phase) * 0.025f * stride;
	}
}
