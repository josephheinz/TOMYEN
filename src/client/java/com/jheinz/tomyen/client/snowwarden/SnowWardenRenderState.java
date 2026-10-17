package com.jheinz.tomyen.client.snowwarden;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class SnowWardenRenderState extends LivingEntityRenderState {
	/** How far through a fist swing, 0 to 1 (0 when not swinging); and which fist. */
	public float swing;
	public boolean swingLeft;
	/** How far through a ground slam, 0 to 1. */
	public float slam;
	/** Leaning back drawing in the cold for the frost blast: 0 to 1. */
	public float charge;
	/** How far through firing the frost blast, 0 to 1. */
	public float blast;
	/** How far through lobbing a snowball, 0 to 1. */
	public float throwing;
	/** How far through its roar, 0 to 1. */
	public float roar;
	/** Head down, sniffing about: 0 to 1. */
	public float sniff;
	/** How bright the chest crystals burn: a slow pulse, flaring as it charges the frost blast. */
	public float crystalGlow;
}
