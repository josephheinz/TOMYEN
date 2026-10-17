package com.jheinz.tomyen.client.gnome;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

public class GnomeRenderState extends LivingEntityRenderState {
	/** The troop's coat colour (0 blue, 1 green, 2 ochre); unused for the captain. */
	public int variant;
	public boolean captain;
	/** Hugging a stolen crop to its chest. */
	public boolean carrying;
	/** On the march in formation: stiff, high arm swings. */
	public boolean marching;
	/** Scattering or routed: arms up, leaning into the run. */
	public boolean panic;
	/** Through a poke, 0 to 1. */
	public float attack;
	/** How far set into a ceramic statue, 0 (alive) to 1 (statue). */
	public float statue;
	/** The tremor while it sets (peaks halfway), and the live clock that drives it (ageInTicks is frozen with it). */
	public float shudder;
	public float shudderAge;
	/** What it's carrying off. */
	public final ItemStackRenderState carried = new ItemStackRenderState();
}
