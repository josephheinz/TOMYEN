package com.jheinz.mymod.client.cart;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class ShoppingCartRenderState extends LivingEntityRenderState {
	/** Chasing someone: red eyes, jaw hanging open. */
	public boolean aggressive;
	/** Bite progress, 0 to 1. */
	public float swing;
	/** Leap pose, 0 to 1. */
	public float pounce;
}
