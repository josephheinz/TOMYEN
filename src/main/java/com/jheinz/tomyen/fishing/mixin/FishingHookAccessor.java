package com.jheinz.tomyen.fishing.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.projectile.FishingHook;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The bobber's private bite timers, which a {@link com.jheinz.tomyen.fishing.RodHook} watches and resets. */
@Mixin(FishingHook.class)
public interface FishingHookAccessor {
	@Accessor("nibble")
	int tomyen$getNibble();

	@Accessor("nibble")
	void tomyen$setNibble(int ticks);

	@Accessor("timeUntilLured")
	void tomyen$setTimeUntilLured(int ticks);

	@Accessor("timeUntilHooked")
	int tomyen$getTimeUntilHooked();

	@Accessor("timeUntilHooked")
	void tomyen$setTimeUntilHooked(int ticks);

	@Accessor("luck")
	int tomyen$getLuck();

	@Accessor("DATA_BITING")
	static EntityDataAccessor<Boolean> tomyen$dataBiting() {
		throw new AssertionError();
	}
}
