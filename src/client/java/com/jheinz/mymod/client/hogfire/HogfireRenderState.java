package com.jheinz.mymod.client.hogfire;

import net.minecraft.client.renderer.entity.state.PigRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

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
	/** The gun mount on its shoulders, if it has one: the Hogfire Gun Mount's item model, turning with its gun. */
	public boolean gunMount;
	public final ItemStackRenderState mountedGun = new ItemStackRenderState();
}
