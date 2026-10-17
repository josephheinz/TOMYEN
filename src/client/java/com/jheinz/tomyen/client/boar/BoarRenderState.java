package com.jheinz.tomyen.client.boar;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class BoarRenderState extends LivingEntityRenderState {
	/** A big boar: big tusks. */
	public boolean tusker;
	/** How far into each pose it is, 0 to 1: snout in the dirt, pawing the ground, charging, dazed. */
	public float root;
	public float paw;
	public float charge;
	public float stun;
	/** Progress through a gore (a toss of the tusks), 0 to 1 (0 when not goring). */
	public float attackAnim;
	/** Hackles up: crest raised, tail high. */
	public float agitated;
}
