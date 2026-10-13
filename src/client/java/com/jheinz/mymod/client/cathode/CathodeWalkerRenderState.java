package com.jheinz.mymod.client.cathode;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;

public class CathodeWalkerRenderState extends LivingEntityRenderState {
	/** Per-walker offset for its idle tilts and blinks, so a pair doesn't move in lockstep. */
	public float seed;
	/** After a target: leaning into a sprint. */
	public boolean hunting;
	/** Kick progress, 0 to 1. */
	public float attackAnim;
	/** Screen brightening for a Static Burst, 0 to 1 (and the white flash once it's gone off). */
	public float charge;
	/** What's on the screen, drawn full bright over the dark glass, or nothing. */
	public @Nullable RenderType screen;
	/** How bright the screen's drawn, 0 to 1. */
	public float screenBrightness = 1.0f;
}
