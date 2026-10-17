package com.jheinz.tomyen.client.shrimp;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class ShrimpRenderState extends LivingEntityRenderState {
	/** Progress through a claw snap, 0 to 1 (0 when not snapping). */
	public float attackAnim;
	/** How far the pistol is up and levelled, 0 (held low) to 1. */
	public float aim;
	/** The kick of the last shot: 1 as it fires, gone in a few ticks. */
	public float recoil;
	/** How far through a reload, 0 to 1 (0 when not reloading). */
	public float reload;
}
