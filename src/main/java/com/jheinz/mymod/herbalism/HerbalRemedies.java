package com.jheinz.mymod.herbalism;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import net.minecraft.world.level.Level;

import com.jheinz.mymod.ModEffects;
import com.jheinz.mymod.uranium.Radiation;

/** The remedies' hooks into other systems: radiation, so far. */
public final class HerbalRemedies {
	private HerbalRemedies() {
	}

	/** A radiation dose about to land on someone: halved under Radioprotection, quartered under Radioprotection II. */
	public static int radiationDose(LivingEntity entity, int ticks) {
		MobEffectInstance shield = entity.getEffect(Herbalism.RADIOPROTECTION);
		return shield == null ? ticks : ticks >> Math.min(2, shield.getAmplifier() + 1);
	}

	/** Draws {@code ticks} off a Radiation Sickness dose (its level falls with it), as the First Aid Kit does. */
	public static boolean easeRadiation(LivingEntity entity, int ticks) {
		MobEffectInstance sickness = entity.getEffect(ModEffects.RADIATION_SICKNESS);
		if (sickness == null) {
			return false;
		}
		int dose = sickness.getDuration() - ticks;
		entity.removeEffect(ModEffects.RADIATION_SICKNESS);
		if (dose > 0) {
			entity.addEffect(new MobEffectInstance(ModEffects.RADIATION_SICKNESS, dose, Radiation.level(dose)));
		}
		return true;
	}

	/** A consume effect ({@code mymod:ease_radiation}) that draws this many ticks off a Radiation Sickness dose. */
	public record EaseRadiation(int ticks) implements ConsumeEffect {
		public static final MapCodec<EaseRadiation> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				Codec.INT.fieldOf("ticks").forGetter(EaseRadiation::ticks)).apply(instance, EaseRadiation::new));
		public static final StreamCodec<RegistryFriendlyByteBuf, EaseRadiation> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, EaseRadiation::ticks, EaseRadiation::new);

		@Override
		public ConsumeEffect.Type<EaseRadiation> getType() {
			return Herbalism.EASE_RADIATION;
		}

		@Override
		public boolean apply(Level level, ItemStack stack, LivingEntity user) {
			return easeRadiation(user, ticks);
		}
	}
}
