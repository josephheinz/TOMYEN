package com.jheinz.tomyen.fusion.gear;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * The Phantasmal Chestplate's (and the Revenant Mail's) ghost: {@value #CHANCE_PERCENT}% of the hits that would land
 * on the wearer pass straight through, with no damage, no knockback and no hurt flash, just a ghostly shimmer and a
 * soul's sigh. A hit is anything dealt by something: a mob, a player, an arrow, a blast. Fire, falls, drowning, poison
 * and the like aren't hits, and {@code /kill} and the void always get through. A hit that comes while the wearer is
 * still flashing from the last one (and would mostly be shrugged off anyway) isn't rolled for.
 */
public final class Phasing {
	public static final int CHANCE_PERCENT = 20;
	private static final int ECTO = 0x7FF5D8;
	private static final int PALE = 0xD8FFF6;

	private Phasing() {
	}

	static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !phaseThrough(entity, source));
	}

	/** Whether this hit passes through the entity (and if it does, the shimmer). */
	static boolean phaseThrough(LivingEntity entity, DamageSource source) {
		if (!(entity.level() instanceof ServerLevel level) || entity.isDeadOrDying() || entity.getInvulnerableTime() > 10
				|| !isHit(source) || !FusionGear.phases(entity) || entity.getRandom().nextInt(100) >= CHANCE_PERCENT) {
			return false;
		}
		shimmer(level, entity);
		return true;
	}

	static boolean isHit(DamageSource source) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		return source.getEntity() != null || source.getDirectEntity() != null || source.is(DamageTypeTags.IS_EXPLOSION)
				|| source.is(DamageTypeTags.IS_PROJECTILE);
	}

	/** A pale ring and a rising wisp of ectoplasm through the body, and a soul's sigh. */
	static void shimmer(ServerLevel level, LivingEntity entity) {
		double x = entity.getX(), y = entity.getY(0.5), z = entity.getZ();
		double radius = entity.getBbWidth() * 0.7 + 0.15;
		DustParticleOptions ecto = new DustParticleOptions(ECTO, 0.9f);
		for (int i = 0; i < 14; i++) {
			float angle = i * Mth.TWO_PI / 14;
			level.sendParticles(ecto, x + Mth.cos(angle) * radius, y + Mth.sin(angle * 3) * 0.15, z + Mth.sin(angle) * radius,
					1, 0.0, 0.0, 0.0, 0.0);
		}
		level.sendParticles(new DustParticleOptions(PALE, 0.7f), x, y, z, 10, 0.25, entity.getBbHeight() * 0.3, 0.25, 0.0);
		level.sendParticles(ParticleTypes.SOUL, x, y, z, 3, 0.2, 0.3, 0.2, 0.02);
		level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0x60000000 | ECTO), x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, x, y, z, SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.0f, 1.3f + level.getRandom().nextFloat() * 0.3f);
		level.playSound(null, x, y, z, SoundEvents.ALLAY_ITEM_TAKEN, SoundSource.PLAYERS, 0.5f, 0.6f);
	}
}
