package com.jheinz.mymod.client.mech;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** A Bulwark Walker as drawn for one frame: on its legs, or standing in a gantry. */
public class MechRenderState extends LivingEntityRenderState {
	/** What it's built from (see MechLoadout#look). */
	public int look;
	/** The torso's twist off the legs and the arms' pitch, in degrees. */
	public float twist;
	public float armPitch;
	/** How far the chest hatch has lifted, 0 to 1. */
	public float hatch;
	/** Per arm (left, right): the kick from the last shot (0 to 1), the rotary barrels' turn (radians) and the fist's grip (0 to 1). */
	public final float[] recoil = new float[2];
	public final float[] spin = new float[2];
	public final float[] grip = new float[2];
	/** How long the jet flames still burn, 0 to 1. */
	public float jetFlame;
	/** Whether its core is lit: the viewport, visor and power plant window glow. */
	public boolean powered;
}
