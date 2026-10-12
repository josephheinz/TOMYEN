package com.jheinz.mymod.client.tank;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class TankRenderState extends LivingEntityRenderState {
	/** How far back the barrel has kicked from the last shot, 0 to 1. */
	public float recoil;
}
