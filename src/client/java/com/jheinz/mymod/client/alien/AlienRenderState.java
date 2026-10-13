package com.jheinz.mymod.client.alien;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class AlienRenderState extends LivingEntityRenderState {
	/** Per-alien offset for its fidgeting, so a crew doesn't move in lockstep. */
	public float seed;
	public boolean dazed;
	/** Raising its ray gun, 0 to 1 and back. */
	public float attackTime;
}
