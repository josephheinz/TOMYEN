package com.jheinz.mymod.tank;

import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.bank.Collectors;

/**
 * A round from a {@link Tank}'s gun, flying as its {@link ShellType} says. A direct hit does the round's damage
 * to whatever it strikes, then it bursts: a sabot only puffs (a depleted uranium one in a burning flash that sets
 * everything within {@link #INCENDIARY_RADIUS} blocks alight), high explosive blows a crater (unless mobGriefing
 * is off), and a collector's shell hurts without breaking blocks. Each leaves a tracer behind it. It spares
 * whoever fired it and their tank, and a collector's shell spares the other collectors.
 */
public class TankShell extends ThrowableItemProjectile {
	private static final EntityDataAccessor<Byte> TYPE = SynchedEntityData.defineId(TankShell.class, EntityDataSerializers.BYTE);
	private static final int LIFETIME = 120;
	static final double INCENDIARY_RADIUS = 3.0;
	private static final float INCENDIARY_SECONDS = 6.0f;

	public TankShell(EntityType<? extends TankShell> type, Level level) {
		super(type, level);
	}

	/** @param gunner the tank itself when it's firing on its own, or the player crewing it */
	public TankShell(Level level, LivingEntity gunner, Vec3 muzzle, ShellType type) {
		super(Tanks.TANK_SHELL_ENTITY, muzzle.x, muzzle.y, muzzle.z, level, new ItemStack(type.item()));
		setOwner(gunner);
		entityData.set(TYPE, (byte) type.ordinal());
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(TYPE, (byte) ShellType.COLLECTOR.ordinal());
	}

	public ShellType shellType() {
		return ShellType.byOrdinal(entityData.get(TYPE));
	}

	@Override
	protected Item getDefaultItem() {
		return Tanks.TANK_SHELL;
	}

	@Override
	protected double getDefaultGravity() {
		return shellType().gravity();
	}

	@Override
	protected float getAirDrag() {
		return shellType().drag();
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 256.0 * 256.0;
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
		Vec3 before = position();
		super.tick();
		if (level().isClientSide()) {
			tracer(before, position());
		} else if (tickCount > LIFETIME) {
			// A round that never lands doesn't fly forever.
			discard();
		}
	}

	/** A sabot draws a bright streak; the slower rounds trail smoke with a glowing base. */
	private void tracer(Vec3 from, Vec3 to) {
		Vec3 path = to.subtract(from);
		int steps = Math.max(1, (int) (path.length() / 0.8));
		ShellType type = shellType();
		for (int i = 0; i < steps; i++) {
			Vec3 p = from.add(path.scale(i / (double) steps));
			if (type.sabot()) {
				level().addParticle(ParticleTypes.END_ROD, p.x, p.y, p.z, 0.0, 0.0, 0.0);
			} else {
				level().addParticle(i % 2 == 0 ? ParticleTypes.SMOKE : ParticleTypes.SMALL_FLAME, p.x, p.y, p.z, 0.0, 0.0, 0.0);
			}
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		float damage = shellType().damage();
		if (damage > 0.0f && level() instanceof ServerLevel level) {
			hit.getEntity().hurtServer(level, damageSources().thrown(this, getOwner()), damage);
			if (shellType().incendiary()) {
				hit.getEntity().igniteForSeconds(INCENDIARY_SECONDS);
			}
		}
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		ShellType type = shellType();
		ExplosionDamageCalculator blast = new ExplosionDamageCalculator() {
			@Override
			public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
				return !spares(entity) && super.shouldDamageEntity(explosion, entity);
			}
		};
		Vec3 at = hit.getLocation();
		level.explode(this, null, blast, at.x, at.y, at.z, type.blast(), false,
				type.breaksBlocks() ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE);
		if (type.sabot()) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 16, 0.2, 0.2, 0.2, 0.4);
		}
		if (type.incendiary()) {
			incendiaryFlash(level, at);
		}
		discard();
	}

	/** Burning uranium spatters round the strike: a white flash, flames, and everything close by set alight. */
	private void incendiaryFlash(ServerLevel level, Vec3 at) {
		level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFF4D8), at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 40, 0.6, 0.4, 0.6, 0.08);
		level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 6, 0.3, 0.2, 0.3, 0.0);
		AABB area = new AABB(at, at).inflate(INCENDIARY_RADIUS);
		for (Entity entity : level.getEntities(this, area, entity -> entity instanceof LivingEntity && !spares(entity))) {
			if (entity.position().distanceTo(at) <= INCENDIARY_RADIUS + entity.getBbWidth()) {
				entity.igniteForSeconds(INCENDIARY_SECONDS);
			}
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putByte("ShellType", entityData.get(TYPE));
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		entityData.set(TYPE, input.getByteOr("ShellType", (byte) ShellType.COLLECTOR.ordinal()));
	}
}
