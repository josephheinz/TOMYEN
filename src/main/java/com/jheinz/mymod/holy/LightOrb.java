package com.jheinz.mymod.holy;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.ModEntities;

/**
 * A slow, weightless orb of light punched out of a crucifix. It drifts straight on through anything living
 * and bursts on the first undead creature or block it meets, or after {@link #LIFETIME} ticks.
 */
public class LightOrb extends ThrowableProjectile {
	public static final float SPEED = 0.35f;
	private static final int LIFETIME = 100;
	private static final float DAMAGE = 4.0f;
	/** Half its height: its position is the bottom of its box, so this puts the middle on the aim line. */
	private static final double HALF_SIZE = 0.2;

	public LightOrb(EntityType<? extends LightOrb> type, Level level) {
		super(type, level);
	}

	/** Starts just in front of the owner's eyes, centred on their line of sight. */
	public LightOrb(Level level, LivingEntity owner) {
		super(ModEntities.LIGHT_ORB, level);
		Vec3 start = owner.getEyePosition().add(owner.getViewVector(1.0f).scale(0.6));
		setPos(start.x, start.y - HALF_SIZE, start.z);
		setOwner(owner);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	protected double getDefaultGravity() {
		return 0.0;
	}

	@Override
	protected float getAirDrag() {
		return 1.0f;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			if (random.nextInt(2) == 0) {
				level().addParticle(ParticleTypes.END_ROD, getRandomX(0.6), getRandomY(), getRandomZ(0.6), 0.0, 0.0, 0.0);
			}
		} else if (tickCount > LIFETIME) {
			burst((ServerLevel) level());
		}
	}

	/** Passes straight through anything holy light can't hurt. */
	@Override
	protected boolean canHitEntity(Entity target) {
		return super.canHitEntity(target) && HolyLight.hurts(target);
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		super.onHitEntity(result);
		if (level() instanceof ServerLevel level) {
			HolyLight.smite(level, result.getEntity(), this, getOwner(), DAMAGE);
		}
	}

	@Override
	protected void onHit(HitResult result) {
		super.onHit(result);
		if (level() instanceof ServerLevel level) {
			burst(level);
		}
	}

	private void burst(ServerLevel level) {
		level.sendParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 14, 0.1, 0.1, 0.1, 0.12);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.8f, 1.6f);
		discard();
	}
}
