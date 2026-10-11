package com.jheinz.mymod;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;

import com.jheinz.mymod.vampire.BleedingEffect;

public final class ModEffects {
	/** From a vampire's bite: steady damage, and the smell of blood draws vampires and shopping carts from far off. */
	public static final Holder<MobEffect> BLEEDING = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, MyMod.id("bleeding"),
			new BleedingEffect());

	private ModEffects() {
	}

	public static void initialize() {
	}
}
