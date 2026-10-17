package com.jheinz.tomyen.hag;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
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
 * A Bog Hag's hex: a gob of hexed bog muck lobbed in an arc, trailing witchy motes. Whatever it hits takes
 * {@value #DAMAGE} magic damage and the {@link HagsCurse Hag's Curse}. It bursts on anything, and never hits her.
 */
public class HexGlob extends ThrowableItemProjectile {
	private static final float DAMAGE = 2.0f;
	private static final int TRAIL_COLOR = 0x8FB83A;

	public HexGlob(EntityType<? extends HexGlob> type, Level level) {
		super(type, level);
	}

	public HexGlob(Level level, LivingEntity thrower) {
		super(BogHags.HEX_GLOB, thrower, level, new ItemStack(BogHags.BOG_MUCK));
	}

	@Override
	protected Item getDefaultItem() {
		return BogHags.BOG_MUCK;
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		return entity != getOwner() && !(entity instanceof BogHag) && super.canHitEntity(entity);
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide() && !isRemoved()) {
			level().addParticle(ParticleTypes.WITCH, getX(), getY() + 0.1, getZ(), 0.0, 0.0, 0.0);
			if (random.nextInt(2) == 0) {
				level().addParticle(new DustParticleOptions(TRAIL_COLOR, 0.8f), getX(), getY() + 0.1, getZ(), 0.0, 0.0, 0.0);
			}
		}
	}

	@Override
	public void handleEntityEvent(@EntityEvent.Value byte id) {
		if (id == EntityEvent.DEATH) {
			ItemParticleOption muck = new ItemParticleOption(ParticleTypes.ITEM, getItem().getItem());
			for (int i = 0; i < 10; i++) {
				level().addParticle(muck, getX(), getY(), getZ(), random.nextGaussian() * 0.08, 0.12, random.nextGaussian() * 0.08);
			}
			for (int i = 0; i < 8; i++) {
				level().addParticle(ParticleTypes.WITCH, getX(), getY() + 0.2, getZ(), random.nextGaussian() * 0.1, 0.1,
						random.nextGaussian() * 0.1);
			}
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		super.onHitEntity(result);
		if (level() instanceof ServerLevel level && result.getEntity() instanceof LivingEntity living) {
			living.hurtServer(level, damageSources().indirectMagic(this, getOwner()), DAMAGE);
			if (living.isAlive() && HagsCurse.curse(level, living, getOwner()) && getOwner() instanceof BogHag hag) {
				hag.cackle();
			}
		}
	}

	@Override
	protected void onHit(HitResult result) {
		super.onHit(result);
		if (!level().isClientSide()) {
			level().broadcastEntityEvent(this, EntityEvent.DEATH);
			level().playSound(null, getX(), getY(), getZ(), BogHags.HEX_SPLAT, SoundSource.HOSTILE, 0.9f, 0.8f + random.nextFloat() * 0.3f);
			discard();
		}
	}
}
