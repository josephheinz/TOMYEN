package com.jheinz.tomyen.herbalism;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * The herbal remedies' own effects. They do nothing each tick; other code asks whether they're on:
 * <ul>
 * <li>Garlic Breath: vampires won't bite (see {@link HerbalWards#repelsVampires}).</li>
 * <li>Radioprotection: new radiation doses are halved (quartered at II), see {@link HerbalRemedies#radiationDose}.</li>
 * <li>Calm: nausea can't take hold, and a Cathode Walker's Static Burst doesn't get through.</li>
 * </ul>
 */
public class HerbalEffect extends MobEffect {
	public HerbalEffect(int color) {
		super(MobEffectCategory.BENEFICIAL, color);
	}
}
