package com.jheinz.tomyen.hag;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import com.jheinz.tomyen.MyMod;

/**
 * A Bog Hag's curse (see {@link HagsCurse}): mining {@value #SLOW_MINING_PERCENT}% slower and a point less luck per level,
 * with witchy motes drifting off the cursed. The rest (fish biting later and fighting harder, junk, crumbling ore
 * and bog muck) is in HagsCurse, which asks whether it's on.
 */
public class HagsCurseEffect extends MobEffect {
	private static final int COLOR = 0x4E5A2C;
	public static final int SLOW_MINING_PERCENT = 30;

	public HagsCurseEffect() {
		super(MobEffectCategory.HARMFUL, COLOR, ParticleTypes.WITCH);
		addAttributeModifier(Attributes.BLOCK_BREAK_SPEED, MyMod.id("effect.hags_curse"), -SLOW_MINING_PERCENT / 100.0,
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		addAttributeModifier(Attributes.LUCK, MyMod.id("effect.hags_curse"), -1.0, AttributeModifier.Operation.ADD_VALUE);
	}
}
