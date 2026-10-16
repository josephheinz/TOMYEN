package com.jheinz.tomyen.napalm;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Burning napalm stuck to the skin. While it lasts the bearer stays alight whatever the weather (rain can't touch it:
 * see EntityNapalmRainMixin), and once a second the gel burns them for {@link #DAMAGE} as {@link Napalm#NAPALM_BURN},
 * past armor and the hurt cooldown, on top of ordinary burning: 2 to 3 health a second in all, where plain fire does 1
 * (ordinary burning lands when it falls outside the gel's hurt cooldown). Water puts it out: getting in ends the
 * effect and the fire with it. Fire-proof mobs and fire resistance shrug it off, as they do fire.
 */
public class NapalmEffect extends MobEffect {
	public static final int COLOR = 0xFF6A10;
	public static final float DAMAGE = 2.0f;
	private static final int INTERVAL = 20;
	/** Fire ticks it keeps the bearer topped up to. */
	private static final int KEEP_ALIGHT = 40;

	public NapalmEffect() {
		super(MobEffectCategory.HARMFUL, COLOR, ParticleTypes.SMALL_FLAME);
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int tickCount, int amplification) {
		return true;
	}

	@Override
	public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amplification) {
		if (mob.isInWater()) {
			mob.extinguishFire();
			mob.playSound(SoundEvents.FIRE_EXTINGUISH, 0.6f, 1.4f);
			return false;
		}
		if (mob.fireImmune()) {
			return true;
		}
		mob.igniteForTicks(KEEP_ALIGHT);
		if (mob.tickCount % INTERVAL == 0) {
			mob.hurtServer(level, mob.damageSources().source(Napalm.NAPALM_BURN), DAMAGE);
		}
		return true;
	}
}
