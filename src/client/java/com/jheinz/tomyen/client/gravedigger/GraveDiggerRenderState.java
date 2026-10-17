package com.jheinz.tomyen.client.gravedigger;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

import com.jheinz.tomyen.gravedigger.GraveDigger;

public class GraveDiggerRenderState extends LivingEntityRenderState {
	/** Progress through a shovel chop, 0 to 1 (0 when not swinging). */
	public float attackAnim;
	/** After someone: hunched lower, reaching harder, jaw hanging. */
	public boolean aggressive;
	/** Which way he's digging, if he is. */
	public GraveDigger.DigMode dig = GraveDigger.DigMode.NONE;
}
