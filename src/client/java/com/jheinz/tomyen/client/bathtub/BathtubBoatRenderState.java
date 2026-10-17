package com.jheinz.tomyen.client.bathtub;

import net.minecraft.client.renderer.entity.state.BoatRenderState;

public class BathtubBoatRenderState extends BoatRenderState {
	/** How far the bow's up at speed, 0 to 1, and how far it's sunk into the water, 0 (ashore) to 1. */
	public float lift;
	public float sink;
	/** The motor tilted up ashore (0 to 1), the steering (-1 right to 1 left) and the propeller's turn in radians. */
	public float tilt;
	public float steer;
	public float propeller;
	/** The feet's step cycle and how much they're stepping. */
	public float waddle;
	public float waddleAmount;
	/** The duck's squash, 1 just squeezed. */
	public float duck;
	/** Rowed with an empty tank: the oars are out, at these points in their strokes. */
	public boolean rowing;
	public float rowLeft;
	public float rowRight;
}
