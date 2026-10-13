package com.jheinz.mymod.uranium;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

import com.jheinz.mymod.ModDamageTypes;

/**
 * Radiation Sickness: harmless at level I and II (II brings weakness, see {@link Radiation}); from level III it does
 * half a heart every {@link #DAMAGE_INTERVAL} ticks, twice as often at IV, straight through armor.
 */
public class RadiationSicknessEffect extends MobEffect {
	private static final int COLOR = 0x7CC42A;
	private static final int DAMAGE_INTERVAL = 80;

	public RadiationSicknessEffect() {
		super(MobEffectCategory.HARMFUL, COLOR);
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int tickCount, int amplification) {
		return amplification >= 2 && tickCount % (DAMAGE_INTERVAL >> (amplification - 2)) == 0;
	}

	@Override
	public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amplification) {
		mob.hurtServer(level, mob.damageSources().source(ModDamageTypes.RADIATION), 1.0f);
		return true;
	}
}
