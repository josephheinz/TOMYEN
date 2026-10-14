package com.jheinz.mymod.fishing.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.projectile.FishingHook;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The bobber's private bite timers, which a {@link com.jheinz.mymod.fishing.RodHook} watches and resets. */
@Mixin(FishingHook.class)
public interface FishingHookAccessor {
	@Accessor("nibble")
	int mymod$getNibble();

	@Accessor("nibble")
	void mymod$setNibble(int ticks);

	@Accessor("timeUntilLured")
	void mymod$setTimeUntilLured(int ticks);

	@Accessor("timeUntilHooked")
	int mymod$getTimeUntilHooked();

	@Accessor("timeUntilHooked")
	void mymod$setTimeUntilHooked(int ticks);

	@Accessor("luck")
	int mymod$getLuck();

	@Accessor("DATA_BITING")
	static EntityDataAccessor<Boolean> mymod$dataBiting() {
		throw new AssertionError();
	}
}
