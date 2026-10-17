package com.jheinz.tomyen.client.isle.shade;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** A shade: the render state of the mob it was (drawn by that mob's renderer), and how ghostly to make it. */
public class ShadeRenderState extends EntityRenderState {
	/** The mob's own render state and renderer; null to draw the wisp form instead. */
	public @Nullable EntityRenderState inner;
	public @Nullable EntityRenderer<?, ?> innerRenderer;
	public float scale = 1.0f;
	/** 1 as it first forms, 0 once whole. */
	public float forming;
	public int tint = 0xFFFFFFFF;
	public float bob;
	public float wispSize = 1.0f;
}
