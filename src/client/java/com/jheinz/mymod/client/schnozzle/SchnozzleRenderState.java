package com.jheinz.mymod.client.schnozzle;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Directions and velocities are in model space (see SchnozzleRenderer#toModel). */
public class SchnozzleRenderState extends LivingEntityRenderState {
	/** Hunting: tentacles writhe faster. */
	public boolean aggressive;
	/** Nostril flare, 0 to 1. */
	public float flare;
	/** Firing a volley (rather than sniffing in). */
	public boolean firing;
	/** Winding up a sneeze. */
	public boolean sneezing;
	/** The jolt of a sneeze going off, 1 decaying to 0. */
	public float sneezeJolt;
	/** Tentacle lash progress, 0 to 1. */
	public float attackAnim;
	/** 0 alive, rising to 1 as it finishes dying. */
	public float deathProgress;
	/** How far the tentacles straighten toward the target, 0 to 1. */
	public float reach;
	public float reachX;
	public float reachY = 1.0f;
	public float reachZ;
	/** Blocks a tick. */
	public float velocityX;
	public float velocityY;
	public float velocityZ;
}
