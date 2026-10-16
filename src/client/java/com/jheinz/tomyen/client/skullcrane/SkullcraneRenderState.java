package com.jheinz.tomyen.client.skullcrane;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Directions and velocities are in model space (see SkullcraneRenderer#toModel). */
public class SkullcraneRenderState extends LivingEntityRenderState {
	/** Per-skull offset for its twitches and writhing, so a pack doesn't move in lockstep. */
	public float seed;
	/** Arms flared out to hunt, 0 to 1. */
	public float flare;
	/** Claws clamped shut on a victim, 0 to 1. */
	public float clamp;
	/** Jaw chattering, 0 to 1. */
	public float chatter;
	/** Claw jab progress, 0 to 1. */
	public float attackAnim;
	/** 0 alive, rising to 1 as it finishes dying. */
	public float deathProgress;
	/** How bright its lenses and emitter glow, 0 to 1. */
	public float glow;
	/** How far the arms straighten toward the target (or the victim's shoulders), 0 to 1. */
	public float reach;
	public float reachX;
	public float reachY = 1.0f;
	public float reachZ;
	/** Blocks a tick. */
	public float velocityX;
	public float velocityY;
	public float velocityZ;
}
