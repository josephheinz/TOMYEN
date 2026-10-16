package com.jheinz.tomyen.schnozzle;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * A thrown Giant Booger. It sticks to whatever creature it hits, gumming it up: Slowness II for
 * {@value #STICK_TICKS} ticks and a point of damage. Either way it splats.
 */
public class ThrownBooger extends ThrowableItemProjectile {
	private static final int STICK_TICKS = 100;

	public ThrownBooger(EntityType<? extends ThrownBooger> type, Level level) {
		super(type, level);
	}

	public ThrownBooger(Level level, LivingEntity thrower, ItemStack stack) {
		super(Schnozzles.THROWN_BOOGER, thrower, level, stack);
	}

	public ThrownBooger(Level level, double x, double y, double z, ItemStack stack) {
		super(Schnozzles.THROWN_BOOGER, x, y, z, level, stack);
	}

	@Override
	protected Item getDefaultItem() {
		return Schnozzles.GIANT_BOOGER;
	}

	@Override
	public void handleEntityEvent(@EntityEvent.Value byte id) {
		if (id == EntityEvent.DEATH) {
			for (int i = 0; i < 12; i++) {
				level().addParticle(ParticleTypes.ITEM_SLIME, getX(), getY(), getZ(), random.nextGaussian() * 0.1, 0.1, random.nextGaussian() * 0.1);
			}
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		super.onHitEntity(result);
		if (level() instanceof ServerLevel level && result.getEntity() instanceof LivingEntity living) {
			living.hurtServer(level, damageSources().thrown(this, getOwner()), 1.0f);
			living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STICK_TICKS, 1), getOwner());
		}
	}

	@Override
	protected void onHit(HitResult result) {
		super.onHit(result);
		if (!level().isClientSide()) {
			level().broadcastEntityEvent(this, EntityEvent.DEATH);
			level().playSound(null, getX(), getY(), getZ(), Schnozzles.BOOGER_SPLAT, SoundSource.NEUTRAL, 1.0f, 0.9f + random.nextFloat() * 0.3f);
			discard();
		}
	}
}
