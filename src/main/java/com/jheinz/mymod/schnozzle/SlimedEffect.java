package com.jheinz.mymod.schnozzle;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import com.jheinz.mymod.MyMod;

/** Gummed up with snot (see {@link NoseHairWhipItem}): 35% slower per level, and dripping slime. */
public class SlimedEffect extends MobEffect {
	private static final int COLOR = 0x80A83E;
	private static final double SLOW = -0.35;

	public SlimedEffect() {
		super(MobEffectCategory.HARMFUL, COLOR, ParticleTypes.ITEM_SLIME);
		addAttributeModifier(Attributes.MOVEMENT_SPEED, MyMod.id("effect.slimed"), SLOW, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}
}
