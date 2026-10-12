package com.jheinz.mymod;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;

import net.minecraft.world.effect.MobEffectCategory;

import com.jheinz.mymod.neuralink.NeuralEffect;
import com.jheinz.mymod.vampire.BleedingEffect;

public final class ModEffects {
	/** From a vampire's bite: steady damage, and the smell of blood draws vampires and shopping carts from far off. */
	public static final Holder<MobEffect> BLEEDING = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, MyMod.id("bleeding"),
			new BleedingEffect());
	/** Hit by an EMP Pulse: every Neuralink module is off until it wears off. */
	public static final Holder<MobEffect> NEURAL_STATIC = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, MyMod.id("neural_static"),
			new NeuralEffect(MobEffectCategory.HARMFUL, 0x7A8A99));
	/** Running the Overclock module: bullet time around the player, and faster gun reloads. */
	public static final Holder<MobEffect> OVERCLOCKED = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, MyMod.id("overclocked"),
			new NeuralEffect(MobEffectCategory.BENEFICIAL, 0xFF9100));

	private ModEffects() {
	}

	public static void initialize() {
	}
}
