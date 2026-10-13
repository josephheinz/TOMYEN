package com.jheinz.mymod.client.werewolf;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class WerewolfRenderState extends LivingEntityRenderState {
	/** Progress through a claw swipe, 0 to 1 (0 when not swiping). */
	public float attackAnim;
	/** Which arm the current swipe is with: they take turns. */
	public boolean leftSwipe;
	/** How far its head is thrown back in a howl, 0 to 1. */
	public float howl;
	/** Hunting: arms up and claws out, ears laid back. */
	public boolean aggressive;
}
