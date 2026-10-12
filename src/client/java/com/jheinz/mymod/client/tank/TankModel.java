package com.jheinz.mymod.client.tank;

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

/**
 * A small tank, front at -z: two tracks either side of a boxy hull, with a turret on top that turns with
 * the tank's head yaw and a cannon that pitches with its look. The barrel kicks back when it fires. The
 * crew stands in the turret, so their legs are hidden inside it. The UV layout matches tools/gen_bank.py.
 */
public class TankModel extends EntityModel<TankRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(MyMod.id("tank"), "main");

	private final ModelPart hull;
	private final ModelPart turret;
	private final ModelPart barrel;

	public TankModel(ModelPart root) {
		super(root);
		hull = root.getChild("hull");
		turret = hull.getChild("turret");
		barrel = turret.getChild("barrel");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition hull = root.addOrReplaceChild("hull", CubeListBuilder.create()
				// Tracks, then the hull between them, with an exhaust at the back and headlights at the front.
				.texOffs(0, 0).addBox(-16.0f, -10.0f, -20.0f, 6.0f, 10.0f, 40.0f)
				.texOffs(0, 0).mirror().addBox(10.0f, -10.0f, -20.0f, 6.0f, 10.0f, 40.0f).mirror(false)
				.texOffs(0, 50).addBox(-10.0f, -14.0f, -18.0f, 20.0f, 11.0f, 36.0f)
				.texOffs(92, 8).addBox(5.0f, -16.0f, 15.0f, 2.0f, 2.0f, 4.0f)
				.texOffs(92, 16).addBox(-8.0f, -12.0f, -19.0f, 2.0f, 2.0f, 1.0f)
				.texOffs(92, 16).addBox(6.0f, -12.0f, -19.0f, 2.0f, 2.0f, 1.0f),
				PartPose.offset(0.0f, 24.0f, 0.0f));
		PartDefinition turret = hull.addOrReplaceChild("turret", CubeListBuilder.create()
				.texOffs(0, 97).addBox(-8.0f, -7.0f, -8.0f, 16.0f, 7.0f, 16.0f)
				// The hatch lid, propped open behind the hatch.
				.texOffs(92, 0).addBox(-3.0f, -13.0f, 7.0f, 6.0f, 6.0f, 1.0f),
				PartPose.offset(0.0f, -14.0f, 1.0f));
		turret.addOrReplaceChild("barrel", CubeListBuilder.create()
				.texOffs(64, 97).addBox(-3.0f, -2.5f, -2.0f, 6.0f, 5.0f, 2.0f)
				.texOffs(80, 97).addBox(-1.5f, -1.5f, -22.0f, 3.0f, 3.0f, 20.0f),
				PartPose.offset(0.0f, -3.5f, -8.0f));
		return LayerDefinition.create(mesh, 128, 128);
	}

	@Override
	public void setupAnim(TankRenderState state) {
		super.setupAnim(state);
		turret.yRot = state.yRot * Mth.DEG_TO_RAD;
		barrel.xRot = state.xRot * Mth.DEG_TO_RAD;
		barrel.z += 3.0f * state.recoil;
		// The hull rocks a touch as it drives, and rocks back when the cannon fires.
		float speed = state.walkAnimationSpeed;
		hull.y += Mth.sin(state.ageInTicks * 1.4f) * 0.25f * speed;
		hull.xRot = -0.03f * state.recoil;
	}
}
