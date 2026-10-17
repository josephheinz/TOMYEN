package com.jheinz.tomyen.client.banshee;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class BansheeRenderState extends LivingEntityRenderState {
	/** Through the wail's wind-up (jaw dropping, hair lifting), 0 to 1. */
	public float windup;
	/** Screaming, 0 to 1. */
	public float scream;
	/** Chill-touch swing progress, 0 to 1. */
	public float attackAnim;
	/** 0 alive, rising to 1 as she finishes dying. */
	public float deathProgress;
	/** Struck by something holy: she dies flying apart rather than fading. */
	public boolean banished;
	/** How much of her shows, 0 to 1: a slow ghostly flicker, and fading out as she dies. */
	public float alpha = 1.0f;
}
