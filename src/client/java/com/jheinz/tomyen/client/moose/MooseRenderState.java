package com.jheinz.tomyen.client.moose;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class MooseRenderState extends LivingEntityRenderState {
	public boolean bull;
	public boolean saddled;
	/** The rack's growth, 0 (just shed) to 1 (full); 0 for a cow or a calf. */
	public float antlers;
	/** Neck stretched up into the leaves, chewing: 0 to 1. */
	public float browse;
	/** Head down, pawing the ground before a charge: 0 to 1. */
	public float windup;
	/** The pawing's own clock (ageInTicks keeps running too, but this one starts when the wind-up does). */
	public float pawClock;
	/** Head down, galloping: 0 to 1. */
	public float charge;
	/** How far through a stomp, 0 to 1. */
	public float stomp;
}
