package com.jheinz.tomyen.client.trombone.mixin;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import com.jheinz.tomyen.client.trombone.TromboneMoodState;

/** Gives every living entity's render state the Trombone's spells (see TromboneMoodState). */
@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateTromboneMixin implements TromboneMoodState {
	@Unique
	private float tomyen$droop;
	@Unique
	private float tomyen$sleep;

	@Override
	public float tomyen$droop() {
		return tomyen$droop;
	}

	@Override
	public float tomyen$sleep() {
		return tomyen$sleep;
	}

	@Override
	public void tomyen$setMoods(float droop, float sleep) {
		tomyen$droop = droop;
		tomyen$sleep = sleep;
	}
}
