package com.jheinz.mymod.client.tank;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class TankRenderState extends LivingEntityRenderState {
	/** Turret yaw relative to the hull, and gun pitch, in degrees. */
	public float turretYaw;
	public float gunPitch;
	/** How far back the gun (and hull) has kicked from the last shot, 0 to 1. */
	public float recoil;
	/** How open the commander's hatch is, 0 to 1. */
	public float hatch;
	/** Blocks each track has run, for the wheels and cleats. */
	public float trackLeft;
	public float trackRight;
}
