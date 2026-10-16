package com.jheinz.mymod.client.trombone;

/**
 * What a living entity's render state carries of the Trombone's spells (added by LivingEntityRenderStateTromboneMixin),
 * each 0 (none) to 1: how far it hangs its head under the Sad Trombone, how far over it's slumped asleep under the
 * Lullaby.
 */
public interface TromboneMoodState {
	float mymod$droop();

	float mymod$sleep();

	void mymod$setMoods(float droop, float sleep);
}
