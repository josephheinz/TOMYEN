package com.jheinz.tomyen.client.fishing.catfish;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class GiantCatfishRenderState extends LivingEntityRenderState {
	/** Set for the taxidermy pose on a Giant Fish Mount. */
	public boolean mounted;
	/** Out of the water and not breaching: it lies on its side and bends. */
	public boolean beached;
	/** How hard it's swimming, 0 to 1: the size of the swimming wave. */
	public float swim;
	/** The swimming wave's phase, in radians. */
	public float tail;
	/** Progress through a tail slap, 0 to 1 (0 when not slapping), and the side it strikes: 1 its right, -1 its left. */
	public float slap;
	public float slapSide = 1.0f;
	/** Shaking what it's dragging, or itself on a harpoon line, 0 to 1. */
	public float thrash;
	/** How far its mouth is open, 0 to 1. */
	public float mouth;
	/** Head up, roaring, 0 to 1. */
	public float roar;
	/** Nose up (positive) or down, in degrees, as it climbs or dives. */
	public float pitch;
	/** Its size against a typical fish (its weight's). */
	public float sizeScale = 1.0f;
}
