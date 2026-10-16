package com.jheinz.tomyen.client.scarecrow;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;

import com.jheinz.tomyen.scarecrow.Scarecrow;

public class ScarecrowRenderState extends HumanoidRenderState {
	public Scarecrow.Variant variant = Scarecrow.Variant.STRAW;
	/** Offsets its sway so a row of them doesn't sway in step. */
	public float phase;
	/** How hard the wind blows: 1 on a calm day, more in rain and storms. */
	public float wind = 1.0f;
	/** Ticks since it was last punched (it wobbles for a few). */
	public float wiggle = 100.0f;
	/** How battered it is, 0 (whole) to 1 (nearly falling apart): it sags. */
	public float tatter;
	/** Up a tractor beam: it flails. */
	public boolean lifted;
	/** Something's on its head (a helmet, a pumpkin, a skull): its own hat comes off. */
	public boolean hatOff;
	/** Its glow (a jack-o'-lantern face at night, LED eyes), or null for none. */
	public @Nullable RenderType glow;
}
