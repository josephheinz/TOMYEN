package com.jheinz.tomyen.client.isle;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;

/** A Revenant, drawn as its owner: their skin, model and gear, with how ghostly it looks right now. */
public class RevenantRenderState extends AvatarRenderState {
	public boolean slim;
	/** 1 as it first forms out of the ground, 0 once whole. */
	public float forming;
	/** How solid it looks: its body's alpha. */
	public float alpha = 0.7f;
	public int depth;
}
