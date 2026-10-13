package com.jheinz.mymod.client.alien;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

public class UfoRenderState extends EntityRenderState {
	/** The hull's turn, and which way it faces (its legs, ramp and pilot), in degrees. */
	public float spin;
	public float yaw;
	/** Leaning into the way it's flying: about this horizontal axis (x, z), by this many radians. */
	public float leanX;
	public float leanZ;
	public float lean;
	/** Legs and ramp out, 0 to 1. */
	public float legs;
	public float door;
	public boolean beaming;
	public boolean crashing;
	public boolean hurt;
}
