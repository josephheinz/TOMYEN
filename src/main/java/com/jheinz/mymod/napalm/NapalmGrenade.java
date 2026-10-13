package com.jheinz.mymod.napalm;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A thrown napalm grenade. It trails smoke and bursts on whatever it hits into a splash of burning gel
 * {@link #RADIUS} blocks round, burning everything in it ({@link NapalmFire#splash}). There's no blast: the casing
 * just bursts.
 */
public class NapalmGrenade extends ThrowableItemProjectile {
	public static final double RADIUS = 3.0;
	public static final float DAMAGE = 4.0f;
	public static final int BURN_TICKS = 160;

	public NapalmGrenade(EntityType<? extends NapalmGrenade> type, Level level) {
		super(type, level);
	}

	public NapalmGrenade(Level level, LivingEntity thrower, ItemStack stack) {
		super(Napalm.GRENADE, thrower, level, stack);
	}

	@Override
	protected Item getDefaultItem() {
		return Napalm.NAPALM_GRENADE;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide() && !isRemoved()) {
			level().addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.1, getZ(), 0.0, 0.01, 0.0);
		}
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (level() instanceof ServerLevel level) {
			// Burst just short of what it hit, so the splash isn't buried in a wall.
			Vec3 at = hit.getLocation().subtract(getDeltaMovement().normalize().scale(0.2));
			NapalmFire.splash(level, at, RADIUS, DAMAGE, BURN_TICKS, getOwner());
			discard();
		}
	}
}
