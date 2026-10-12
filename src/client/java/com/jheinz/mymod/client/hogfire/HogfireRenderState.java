package com.jheinz.mymod.client.hogfire;

import net.minecraft.client.renderer.entity.state.PigRenderState;

/** A pig's render state, plus its flight gear and attitude. */
public class HogfireRenderState extends PigRenderState {
	/** HARNESS, WINGS or ENGINE (see Hogfire). */
	public int gear;
	/** Nose-down pitch and right-wing-down bank, in degrees. */
	public float pitch;
	public float bank;
	public boolean engineRunning;
	public boolean afterburner;
	public float throttle;
	public boolean flying;
}
