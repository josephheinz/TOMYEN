package com.jheinz.tomyen.client.scarecrow;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/** Armor on a scarecrow: the usual humanoid armor, posed as the scarecrow is (see {@link ScarecrowModel#pose}). */
public class ScarecrowArmorModel extends HumanoidModel<ScarecrowRenderState> {
	public ScarecrowArmorModel(ModelPart root) {
		super(root);
	}

	@Override
	public void setupAnim(ScarecrowRenderState state) {
		super.setupAnim(state);
		ScarecrowModel.pose(this, state);
	}
}
