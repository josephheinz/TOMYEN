package com.jheinz.tomyen.neuralink;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * A status the Neuralink puts on its wearer that does nothing by itself: code checks for it. Effects sync to the
 * player's own client, so the HUD can react to them too.
 */
public class NeuralEffect extends MobEffect {
	public NeuralEffect(MobEffectCategory category, int color) {
		super(category, color);
	}
}
