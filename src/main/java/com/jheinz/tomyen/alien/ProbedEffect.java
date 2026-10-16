package com.jheinz.tomyen.alien;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Probed: what being carried off by aliens leaves you with. It does nothing itself; it comes with Glowing and a spell of
 * Nausea (see {@link Abductions#deliver}), and it's there so everyone can see what happened.
 */
public class ProbedEffect extends MobEffect {
	public ProbedEffect() {
		super(MobEffectCategory.NEUTRAL, 0x7CFF4A);
	}
}
