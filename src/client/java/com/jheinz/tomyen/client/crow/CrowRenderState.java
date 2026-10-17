package com.jheinz.tomyen.client.crow;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

public class CrowRenderState extends LivingEntityRenderState {
	/** 0 sitting, 1 wings out in flight (part-way on a hop). */
	public float fly;
	/** The wingbeat's phase (radians) and strength (0 gliding to 1 climbing hard). */
	public float flap;
	public float flapStrength;
	/** Nose up (negative) or down in flight, radians. */
	public float flightPitch;
	/** Through a peck and a caw, 0 to 1 (0 when not doing either). */
	public float peck;
	public float caw;
	public boolean sleeping;
	/** What it's carrying in its beak. */
	public final ItemStackRenderState carried = new ItemStackRenderState();
}
