package com.jheinz.mymod.tank;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.bank.Collectors;

/**
 * A shell from a {@link Tank}'s cannon. It flies in a shallow arc trailing smoke and explodes on whatever
 * it hits, hurting and knocking back everything nearby but leaving the blocks alone. It spares whoever
 * fired it and their tank, and a collector's shell spares the other collectors.
 */
public class TankShell extends ThrowableItemProjectile {
	private static final float BLAST_POWER = 1.2f;

	public TankShell(EntityType<? extends TankShell> type, Level level) {
		super(type, level);
	}

	/** @param gunner the tank itself when it's firing on its own, or the player driving it */
	public TankShell(Level level, LivingEntity gunner, Vec3 muzzle) {
		super(Tanks.TANK_SHELL_ENTITY, muzzle.x, muzzle.y, muzzle.z, level, new ItemStack(Tanks.TANK_SHELL));
		setOwner(gunner);
	}

	@Override
	protected Item getDefaultItem() {
		return Tanks.TANK_SHELL;
	}

	/** The gunner, their tank, and (for a collector's shell) the other collectors. */
	private boolean spares(Entity entity) {
		Entity owner = getOwner();
		if (owner == null) {
			return false;
		}
		return entity == owner || entity == owner.getVehicle() || owner.hasPassenger(entity)
				|| Collectors.isCollector(owner) && Collectors.isCollector(entity);
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		return !spares(entity) && super.canHitEntity(entity);
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			level().addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
		}
		// A shell that somehow never lands doesn't fly forever.
		if (!level().isClientSide() && tickCount > 200) {
			discard();
		}
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (!level().isClientSide()) {
			ExplosionDamageCalculator blast = new ExplosionDamageCalculator() {
				@Override
				public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
					return !spares(entity) && super.shouldDamageEntity(explosion, entity);
				}
			};
			level().explode(this, null, blast, getX(), getY(), getZ(), BLAST_POWER, false, Level.ExplosionInteraction.NONE);
			discard();
		}
	}
}
