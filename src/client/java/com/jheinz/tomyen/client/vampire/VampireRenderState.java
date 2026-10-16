package com.jheinz.tomyen.client.vampire;

import net.minecraft.client.renderer.entity.state.UndeadRenderState;

public class VampireRenderState extends UndeadRenderState {
	/** How much of it shows: 0 in the dark (just its eyes) to 1 in good light. */
	public float visibility;
	/** Lunging in for a bite: claws out. */
	public boolean lunging;
	/** Floating above the ground (at night). */
	public boolean hovering;
}
