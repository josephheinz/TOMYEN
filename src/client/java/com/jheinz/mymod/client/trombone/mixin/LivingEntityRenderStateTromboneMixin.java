package com.jheinz.mymod.client.trombone.mixin;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import com.jheinz.mymod.client.trombone.TromboneMoodState;

/** Gives every living entity's render state the Trombone's spells (see TromboneMoodState). */
@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateTromboneMixin implements TromboneMoodState {
	@Unique
	private float mymod$droop;
	@Unique
	private float mymod$sleep;

	@Override
	public float mymod$droop() {
		return mymod$droop;
	}

	@Override
	public float mymod$sleep() {
		return mymod$sleep;
	}

	@Override
	public void mymod$setMoods(float droop, float sleep) {
		mymod$droop = droop;
		mymod$sleep = sleep;
	}
}
