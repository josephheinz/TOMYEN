package com.jheinz.tomyen.client.hag;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

import com.jheinz.tomyen.hag.BogHag;

public class BogHagRenderState extends LivingEntityRenderState {
	/** Progress through a rap of the staff, 0 to 1 (0 when not swinging). */
	public float attackAnim;
	/** The spell she's casting, and how far through it she is, 0 to 1. */
	public BogHag.Spell spell = BogHag.Spell.NONE;
	public float spellProgress;
	/** After someone: she hunches lower, staff and lantern raised a little. */
	public boolean aggressive;
}
