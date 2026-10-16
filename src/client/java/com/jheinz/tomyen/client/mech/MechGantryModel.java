package com.jheinz.tomyen.client.mech;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Unit;

import com.jheinz.tomyen.MyMod;

/**
 * The Mech Gantry's frame, drawn round its deck block: a deck three blocks wide with brass edging, two pillars
 * at the back holding a brass crossbeam five and a half blocks up, and two hoist arms reaching forward over the
 * walker's pauldrons. Same texture as the walker; geometry from tools/gen_mech.py.
 */
public class MechGantryModel extends Model<Unit> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(MyMod.id("mech_gantry"), "main");

	public MechGantryModel(ModelPart root) {
		super(root, RenderTypes::entityCutout);
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		// BEGIN GENERATED (tools/gen_mech.py --java)
		root.addOrReplaceChild("frame", CubeListBuilder.create()
				.texOffs(108, 0).addBox(-24.0f, -3.0f, -20.0f, 48.0f, 3.0f, 40.0f)
				.texOffs(0, 0).addBox(-24.0f, -90.0f, 16.0f, 5.0f, 87.0f, 5.0f)
				.texOffs(0, 0).addBox(19.0f, -90.0f, 16.0f, 5.0f, 87.0f, 5.0f)
				.texOffs(52, 103).addBox(-24.0f, -94.0f, 15.0f, 48.0f, 4.0f, 7.0f)
				.texOffs(386, 0).addBox(-24.0f, -80.0f, -12.0f, 4.0f, 4.0f, 28.0f)
				.texOffs(386, 0).addBox(20.0f, -80.0f, -12.0f, 4.0f, 4.0f, 28.0f),
				PartPose.offset(0.0f, 24.0f, 0.0f));
		// END GENERATED
		return LayerDefinition.create(mesh, MechModel.TEXTURE_WIDTH, MechModel.TEXTURE_HEIGHT);
	}
}
