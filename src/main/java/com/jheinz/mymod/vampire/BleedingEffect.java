package com.jheinz.mymod.vampire;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import com.jheinz.mymod.ModDamageTypes;
import com.jheinz.mymod.neuralink.NeuralinkItem;
import com.jheinz.mymod.neuralink.NeuralinkModule;

/**
 * Loses half a heart every two seconds (faster at higher levels), half that with a Neuralink Bio-Monitor
 * clotting it. A bleeding player at night also has a
 * chance each time of drawing a fresh vampire out of the dark nearby; see {@link BloodScent}.
 */
public class BleedingEffect extends MobEffect {
	private static final int COLOR = 0x8A0303;
	private static final int DAMAGE_INTERVAL = 40;

	public BleedingEffect() {
		super(MobEffectCategory.HARMFUL, COLOR, new DustParticleOptions(COLOR, 1.0f));
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int tickCount, int amplification) {
		int interval = DAMAGE_INTERVAL >> amplification;
		return interval <= 0 || tickCount % interval == 0;
	}

	@Override
	public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amplification) {
		float damage = NeuralinkItem.isActive(mob, NeuralinkModule.BIO_MONITOR) ? 0.5f : 1.0f;
		mob.hurtServer(level, mob.damageSources().source(ModDamageTypes.BLEEDING), damage);
		if (mob instanceof Player player) {
			BloodScent.maybeCallVampire(level, player);
		}
		return true;
	}
}
