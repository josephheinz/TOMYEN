package com.jheinz.mymod.client.banana;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class BigBananaRenderState extends LivingEntityRenderState {
	/** Progress through a slap, 0 to 1 (0 when not slapping). */
	public float attackAnim;
	/** Progress through a happy bounce, 0 to 1 (0 when not bouncing). */
	public float happy;
}
