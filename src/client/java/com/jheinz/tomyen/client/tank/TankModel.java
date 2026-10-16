package com.jheinz.tomyen.client.tank;

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
 * A modern main battle tank, front at -z, about 7.8 blocks long, 3.6 wide and 2.4 to the turret roof.
 * <ul>
 * <li>Hull: a long deck over the fenders, a sloped glacis and lower front plate, exhaust grilles at the back,
 * headlights, and armoured side skirts over the running gear.
 * <li>Running gear, each side: seven road wheels, a front idler and a raised rear drive sprocket, all of which
 * turn as the tank moves, and a track whose cleats run along the ground under them. The two sides run on their
 * own, so a pivot turn drives them in opposite directions.
 * <li>Turret (turns with the tank's turret yaw): angular cheeks, a long rear bustle with antennas, the gunner's
 * sight and the commander's viewer on the roof, a commander's cupola whose hatch swings open on the crew's climb
 * (and for a collector standing in it), the loader's hatch, a roof machine gun, smoke launchers and stowage bins.
 * <li>Gun (pitches with the gun): a long tube with a fume extractor and a muzzle reference sensor; the tube slides
 * back when it fires, and the whole hull rocks back with it.
 * </ul>
 * The UV layout matches tools/gen_tank.py: big boxes have their own areas; small ones borrow from plain swatches.
 */
public class TankModel extends EntityModel<TankRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(MyMod.id("tank"), "main");

	private static final int ROAD_WHEELS = 7;
	private static final int CLEATS = 23;
	/** Cleat spacing, and the length of the run along the ground they circulate over. */
	private static final float CLEAT_PITCH = 4.0f;
	private static final float RUN_LENGTH = CLEATS * CLEAT_PITCH;
	private static final float RUN_START = -46.0f;
	private static final float WHEEL_RADIUS = 5.0f;
	private static final float SPROCKET_RADIUS = 6.0f;

	private final ModelPart hull;
	private final ModelPart turret;
	private final ModelPart gun;
	private final ModelPart tube;
	private final ModelPart commanderHatch;
	private final Track left;
	private final Track right;

	/** One side's running gear. */
	private record Track(ModelPart[] wheels, ModelPart idler, ModelPart sprocket, ModelPart[] cleats) {
		static Track of(ModelPart part) {
			ModelPart[] wheels = new ModelPart[ROAD_WHEELS];
			for (int i = 0; i < ROAD_WHEELS; i++) {
				wheels[i] = part.getChild("wheel_" + i);
			}
			ModelPart[] cleats = new ModelPart[CLEATS];
			for (int i = 0; i < CLEATS; i++) {
				cleats[i] = part.getChild("cleat_" + i);
			}
			return new Track(wheels, part.getChild("idler"), part.getChild("sprocket"), cleats);
		}

		/** Turns the wheels and runs the cleats along for {@code travel} blocks of movement. */
		void animate(float travel) {
			float pixels = travel * 16.0f;
			for (ModelPart wheel : wheels) {
				wheel.xRot = pixels / WHEEL_RADIUS;
			}
			idler.xRot = pixels / WHEEL_RADIUS;
			sprocket.xRot = pixels / SPROCKET_RADIUS;
			float shift = pixels % RUN_LENGTH;
			for (int i = 0; i < cleats.length; i++) {
				float along = (i * CLEAT_PITCH + shift) % RUN_LENGTH;
				if (along < 0.0f) {
					along += RUN_LENGTH;
				}
				cleats[i].z = RUN_START + along;
			}
		}
	}

	public TankModel(ModelPart root) {
		super(root);
		hull = root.getChild("hull");
		turret = hull.getChild("turret");
		gun = turret.getChild("gun");
		tube = gun.getChild("tube");
		commanderHatch = turret.getChild("commander_hatch");
		left = Track.of(hull.getChild("left_track"));
		right = Track.of(hull.getChild("right_track"));
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition hull = root.addOrReplaceChild("hull", CubeListBuilder.create()
				// The deck over hull and fenders, with the hull proper below it between the tracks.
				.texOffs(0, 0).addBox(-28.0f, -26.0f, -54.0f, 56.0f, 10.0f, 110.0f)
				.texOffs(0, 230).addBox(-15.0f, -16.0f, -48.0f, 30.0f, 9.0f, 96.0f)
				// Exhaust grilles out the back, headlights up front.
				.texOffs(300, 320).addBox(-12.0f, -24.0f, 56.0f, 24.0f, 4.0f, 2.0f)
				.texOffs(450, 300).addBox(-26.0f, -27.0f, -57.0f, 4.0f, 2.0f, 2.0f)
				.texOffs(450, 300).addBox(22.0f, -27.0f, -57.0f, 4.0f, 2.0f, 2.0f)
				// Side skirts.
				.texOffs(300, 120).addBox(-29.0f, -17.0f, -50.0f, 1.0f, 9.0f, 96.0f)
				.texOffs(300, 120).addBox(28.0f, -17.0f, -50.0f, 1.0f, 9.0f, 96.0f),
				PartPose.offset(0.0f, 24.0f, 0.0f));
		// The glacis slopes down from the deck's front edge, and the lower plate back under to the belly.
		hull.addOrReplaceChild("glacis", CubeListBuilder.create().texOffs(0, 436).addBox(-27.0f, 0.0f, -12.0f, 54.0f, 2.0f, 12.0f),
				PartPose.offsetAndRotation(0.0f, -26.0f, -54.0f, 0.5f, 0.0f, 0.0f));
		hull.addOrReplaceChild("lower_glacis", CubeListBuilder.create().texOffs(140, 436).addBox(-26.0f, 0.0f, 0.0f, 52.0f, 14.0f, 2.0f),
				PartPose.offsetAndRotation(0.0f, -20.3f, -64.4f, 0.55f, 0.0f, 0.0f));
		track(hull, "left_track", 22.5f);
		track(hull, "right_track", -22.5f);

		PartDefinition turret = hull.addOrReplaceChild("turret", CubeListBuilder.create()
				.texOffs(0, 120).addBox(-22.0f, -12.0f, -26.0f, 44.0f, 12.0f, 50.0f)
				.texOffs(192, 144).addBox(-18.0f, -11.0f, -34.0f, 36.0f, 10.0f, 8.0f)
				.texOffs(192, 120).addBox(-19.0f, -11.0f, 24.0f, 38.0f, 10.0f, 14.0f)
				.texOffs(350, 330).addBox(-6.0f, -9.0f, -38.0f, 12.0f, 8.0f, 4.0f)
				// Gunner's primary sight (front right) and the commander's viewer (front left).
				.texOffs(330, 280).addBox(-15.0f, -16.0f, -24.0f, 7.0f, 4.0f, 7.0f)
				.texOffs(360, 280).addBox(6.0f, -17.0f, -14.0f, 5.0f, 5.0f, 5.0f)
				// Commander's cupola ring (right, behind the sight).
				.texOffs(300, 300).addBox(-17.0f, -14.0f, 2.0f, 12.0f, 2.0f, 12.0f)
				// Smoke grenade launchers and stowage bins on the sides.
				.texOffs(300, 280).addBox(-26.0f, -10.0f, -24.0f, 4.0f, 5.0f, 8.0f)
				.texOffs(300, 280).addBox(22.0f, -10.0f, -24.0f, 4.0f, 5.0f, 8.0f)
				.texOffs(300, 330).addBox(-24.0f, -9.0f, -2.0f, 2.0f, 7.0f, 20.0f)
				.texOffs(300, 330).addBox(22.0f, -9.0f, -2.0f, 2.0f, 7.0f, 20.0f)
				// Antennas on the bustle and the wind sensor mast.
				.texOffs(0, 344).addBox(-16.0f, -33.0f, 34.0f, 1.0f, 22.0f, 1.0f)
				.texOffs(0, 344).addBox(15.0f, -33.0f, 34.0f, 1.0f, 22.0f, 1.0f)
				.texOffs(0, 344).addBox(-1.0f, -18.0f, 26.0f, 1.0f, 7.0f, 1.0f),
				PartPose.offset(0.0f, -26.0f, 4.0f));
		// The hatch lid hinges on its rear edge and swings up and back.
		turret.addOrReplaceChild("commander_hatch", CubeListBuilder.create().texOffs(350, 300).addBox(-6.0f, -1.0f, -12.0f, 12.0f, 1.0f, 12.0f),
				PartPose.offset(-11.0f, -14.0f, 14.0f));
		turret.addOrReplaceChild("loader_hatch", CubeListBuilder.create().texOffs(400, 300).addBox(-5.0f, -1.0f, -10.0f, 10.0f, 1.0f, 10.0f),
				PartPose.offset(11.0f, -12.0f, 14.0f));
		// The roof machine gun on its mount in front of the cupola.
		turret.addOrReplaceChild("roof_mg", CubeListBuilder.create()
				.texOffs(0, 344).addBox(-0.5f, -3.0f, -0.5f, 1.0f, 3.0f, 1.0f)
				.texOffs(0, 344).addBox(-1.0f, -5.0f, -6.0f, 2.0f, 2.0f, 8.0f)
				.texOffs(0, 344).addBox(-0.5f, -4.5f, -16.0f, 1.0f, 1.0f, 10.0f)
				.texOffs(150, 364).addBox(1.0f, -5.0f, -3.0f, 2.0f, 2.0f, 3.0f),
				PartPose.offset(-11.0f, -14.0f, 2.0f));
		PartDefinition gun = turret.addOrReplaceChild("gun", CubeListBuilder.create(), PartPose.offset(0.0f, -5.0f, -36.0f));
		gun.addOrReplaceChild("tube", CubeListBuilder.create()
				.texOffs(0, 344).addBox(-1.5f, -1.5f, -72.0f, 3.0f, 3.0f, 70.0f)
				.texOffs(150, 344).addBox(-2.5f, -2.5f, -46.0f, 5.0f, 5.0f, 12.0f)
				.texOffs(204, 344).addBox(-2.0f, -2.0f, -75.0f, 4.0f, 4.0f, 3.0f)
				.texOffs(150, 364).addBox(-1.0f, -3.0f, -73.0f, 2.0f, 1.0f, 2.0f),
				PartPose.ZERO);
		return LayerDefinition.create(mesh, 512, 512);
	}

	/** One side's running gear, centred {@code x} across. */
	private static void track(PartDefinition hull, String name, float x) {
		PartDefinition track = hull.addOrReplaceChild(name, CubeListBuilder.create()
				// The belt along the ground and back along the top under the fender.
				.texOffs(0, 230).addBox(-5.5f, -3.0f, -46.0f, 11.0f, 2.0f, 92.0f)
				.texOffs(0, 230).addBox(-5.5f, -17.0f, -52.0f, 11.0f, 2.0f, 104.0f),
				PartPose.offset(x, 0.0f, 0.0f));
		// Up from the ground to the idler at the front, and to the sprocket at the back.
		track.addOrReplaceChild("front_run", CubeListBuilder.create().texOffs(0, 230).addBox(-5.5f, -1.0f, -15.0f, 11.0f, 2.0f, 15.0f),
				PartPose.offsetAndRotation(0.0f, -2.0f, -46.0f, -0.738f, 0.0f, 0.0f));
		track.addOrReplaceChild("rear_run", CubeListBuilder.create().texOffs(0, 230).addBox(-5.5f, -1.0f, 0.0f, 11.0f, 2.0f, 16.0f),
				PartPose.offsetAndRotation(0.0f, -2.0f, 46.0f, 0.848f, 0.0f, 0.0f));
		for (int i = 0; i < ROAD_WHEELS; i++) {
			wheel(track, "wheel_" + i, -8.0f, -39.0f + 13.0f * i, 260, 230, 8.0f, WHEEL_RADIUS * 2.0f);
		}
		wheel(track, "idler", -9.0f, -55.0f, 260, 230, 8.0f, WHEEL_RADIUS * 2.0f);
		wheel(track, "sprocket", -11.0f, 54.0f, 260, 252, 9.0f, SPROCKET_RADIUS * 2.0f);
		for (int i = 0; i < CLEATS; i++) {
			track.addOrReplaceChild("cleat_" + i, CubeListBuilder.create().texOffs(0, 230).addBox(-6.0f, 0.0f, -1.0f, 12.0f, 1.0f, 2.0f),
					PartPose.offset(0.0f, -1.0f, RUN_START + i * CLEAT_PITCH));
		}
	}

	/** A wheel: two squares a turn of 45 degrees apart, so it reads as round from the side. */
	private static void wheel(PartDefinition parent, String name, float y, float z, int u, int v, float width, float size) {
		PartDefinition wheel = parent.addOrReplaceChild(name, CubeListBuilder.create()
				.texOffs(u, v).addBox(-width / 2.0f, -size / 2.0f, -size / 2.0f, width, size, size),
				PartPose.offset(0.0f, y, z));
		wheel.addOrReplaceChild("rim", CubeListBuilder.create()
				.texOffs(u, v).addBox(-width / 2.0f + 0.1f, -size / 2.0f, -size / 2.0f, width - 0.2f, size, size),
				PartPose.rotation(Mth.PI / 4.0f, 0.0f, 0.0f));
	}

	@Override
	public void setupAnim(TankRenderState state) {
		super.setupAnim(state);
		turret.yRot = state.turretYaw * Mth.DEG_TO_RAD;
		gun.xRot = state.gunPitch * Mth.DEG_TO_RAD;
		tube.z += 6.0f * state.recoil;
		commanderHatch.xRot = -1.9f * state.hatch;
		left.animate(state.trackLeft);
		right.animate(state.trackRight);
		// The hull rocks a touch as it drives, and its nose kicks up when the gun fires.
		hull.y += Mth.sin(state.ageInTicks * 1.3f) * 0.2f * Math.min(1.0f, state.walkAnimationSpeed);
		hull.xRot = -0.035f * state.recoil;
	}
}
