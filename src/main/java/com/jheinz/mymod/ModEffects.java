package com.jheinz.mymod;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;

import net.minecraft.world.effect.MobEffectCategory;

import com.jheinz.mymod.napalm.NapalmEffect;
import com.jheinz.mymod.neuralink.NeuralEffect;
import com.jheinz.mymod.uranium.Radiation;
import com.jheinz.mymod.uranium.RadiationSicknessEffect;
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
	/** Burning napalm gel: stays alight in rain, burns harder than fire, and only water puts it out. */
	public static final Holder<MobEffect> NAPALM = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, MyMod.id("napalm"),
			new NapalmEffect());

	/** From carrying or digging uranium: weakness, nausea and slow damage as the dose builds (see {@link Radiation}). */
	public static final Holder<MobEffect> RADIATION_SICKNESS = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
			MyMod.id("radiation_sickness"), new RadiationSicknessEffect());

	private ModEffects() {
	}

	public static void initialize() {
	}
}
