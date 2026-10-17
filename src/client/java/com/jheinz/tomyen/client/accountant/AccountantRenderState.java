package com.jheinz.tomyen.client.accountant;

import net.minecraft.client.renderer.entity.state.SlimeRenderState;

public class AccountantRenderState extends SlimeRenderState {
	/** Progress through an audit, 0 to 1 (0 when he isn't auditing). */
	public float audit;
}
