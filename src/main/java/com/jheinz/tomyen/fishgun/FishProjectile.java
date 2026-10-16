package com.jheinz.tomyen.fishgun;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.fishing.Fishing;
import com.jheinz.tomyen.fishing.fish.FishKind;

/**
 * A fish in flight out of a Fish Gun. It carries the fish (drawn as its own 3D model, tumbling end over end; a swordfish
 * flies point first) and its weight, which sets how it falls ({@link FishAmmo#gravity}) and how hard it hits.
 *
 * <p>A hit deals {@link FishAmmo#damage} and shoves the target along the flight by {@link FishAmmo#knockback} (less its
 * knockback resistance), then the species does its trick ({@link FishAmmo#applyEffects}). A cannonball (or a swordfish)
 * bowls on through the next few mobs, a fifth slower each time; whatever finally stops it, a cannonball goes off in a
 * shockwave that throws and hurts everything round it but never touches a block. Then it splats: a wet slap, a spray of
 * fish, and maybe the fish itself bounces off bruised ({@link FishAmmo#remains}). A fish that lands in water is
 * released: it swims off alive (a trophy is kept, and floats).
 */
public class FishProjectile extends ThrowableItemProjectile {
	private static final EntityDataAccessor<Float> DATA_WEIGHT = SynchedEntityData.defineId(FishProjectile.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_AVERAGE = SynchedEntityData.defineId(FishProjectile.class, EntityDataSerializers.FLOAT);
	private static final int LIFETIME = 200;
	/** A shockwave hurts this share of the shot's own damage at its centre, less further out. */
	private static final float SHOCKWAVE_DAMAGE = 0.45f;

	private int pierceLeft;
	private boolean scorched;
	private final Set<Integer> struck = new HashSet<>();

	public FishProjectile(EntityType<? extends FishProjectile> type, Level level) {
		super(type, level);
	}

	public FishProjectile(Level level, LivingEntity shooter, Vec3 muzzle, ItemStack fish, float weight, float average) {
		super(FishGuns.FISH_ROUND, muzzle.x, muzzle.y, muzzle.z, level, fish);
		setOwner(shooter);
		getEntityData().set(DATA_WEIGHT, weight);
		getEntityData().set(DATA_AVERAGE, average);
		pierceLeft = FishAmmo.pierce(fish.getItem(), weight);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_WEIGHT, 1.0f);
		builder.define(DATA_AVERAGE, 1.0f);
	}

	public float weight() {
		return getEntityData().get(DATA_WEIGHT);
	}

	/** Its species' typical weight, so the renderer can size it. */
	public float average() {
		return getEntityData().get(DATA_AVERAGE);
	}

	/** Sends it off along {@code velocity}, facing that way from the first frame. */
	public void launch(Vec3 velocity) {
		setDeltaMovement(velocity);
		updateRotation();
		xRotO = getXRot();
		yRotO = getYRot();
	}

	@Override
	protected Item getDefaultItem() {
		return Items.COD;
	}

	@Override
	protected double getDefaultGravity() {
		return FishAmmo.gravity(weight());
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 128.0 * 128.0;
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		return !struck.contains(entity.getId()) && super.canHitEntity(entity);
	}

	@Override
	public void tick() {
		super.tick();
		if (isRemoved()) {
			return;
		}
		if (level().isClientSide()) {
			trail();
			return;
		}
		if (isInWater()) {
			release((ServerLevel) level());
		} else if (tickCount > LIFETIME) {
			discard();
		}
	}

	/** Drips, a wake of air behind the heavy ones, and the species' own sparkle. */
	private void trail() {
		Vec3 p = position();
		Vec3 back = getDeltaMovement().scale(-0.5);
		Item fish = getItem().getItem();
		if (random.nextInt(3) == 0) {
			level().addParticle(ParticleTypes.FALLING_WATER, p.x, p.y + 0.1, p.z, 0.0, 0.0, 0.0);
		}
		if (FishAmmo.heft(weight()) > 0.6f) {
			level().addParticle(ParticleTypes.CLOUD, p.x + back.x, p.y + 0.15 + back.y, p.z + back.z, 0.0, 0.01, 0.0);
		}
		if (FishAmmo.fiery(fish)) {
			level().addParticle(ParticleTypes.FLAME, p.x, p.y + 0.15, p.z, 0.0, 0.01, 0.0);
		} else if (fish == com.jheinz.tomyen.fishing.fish.Fishes.ANGLERFISH || fish == com.jheinz.tomyen.fishing.fish.Fishes.STARLIT_GAR) {
			level().addParticle(ParticleTypes.GLOW, p.x, p.y + 0.15, p.z, 0.0, 0.0, 0.0);
		} else if (fish == com.jheinz.tomyen.fishing.fish.Fishes.GLOWING_MUTANT_CARP) {
			level().addParticle(ParticleTypes.HAPPY_VILLAGER, p.x, p.y + 0.15, p.z, 0.0, 0.0, 0.0);
		} else if (fish == com.jheinz.tomyen.fishing.fish.Fishes.GHOULFISH) {
			level().addParticle(ParticleTypes.SOUL, p.x, p.y + 0.15, p.z, 0.0, 0.01, 0.0);
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		super.onHitEntity(result);
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		Entity target = result.getEntity();
		struck.add(target.getId());
		strike(level, target, FishAmmo.damage(weight()) * (getItem().is(com.jheinz.tomyen.fishing.fish.Fishes.SWORDFISH) ? 1.25f : 1.0f),
				FishAmmo.knockback(weight()), getDeltaMovement());
		if (target.isOnFire()) {
			scorched = true;
		}
		if (pierceLeft > 0 && target instanceof LivingEntity) {
			// Bowled clean through: on it goes, a little slower.
			pierceLeft--;
			setDeltaMovement(getDeltaMovement().scale(0.8));
			needsSync = true;
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0f, 0.7f);
			return;
		}
		land(level, result.getLocation());
	}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		super.onHitBlock(result);
		if (level() instanceof ServerLevel level) {
			land(level, result.getLocation().subtract(getDeltaMovement().normalize().scale(0.25)));
		}
	}

	/** Damage and a shove along {@code along}, then the species' trick. */
	private void strike(ServerLevel level, Entity target, float damage, float knockback, Vec3 along) {
		DamageSource source = new DamageSource(
				level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(FishGuns.FISH_GUN_DAMAGE), this, getOwner());
		boolean hurt = target.hurtServer(level, source, damage);
		Vec3 flat = new Vec3(along.x, 0.0, along.z);
		if (flat.lengthSqr() > 1.0e-6) {
			double resist = target instanceof LivingEntity living ? living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) : 0.0;
			double k = knockback * (1.0 - resist);
			flat = flat.normalize();
			target.push(flat.x * k, Math.min(0.6, 0.1 + 0.15 * k), flat.z * k);
			target.needsSync = true;
		}
		if (hurt || !(target instanceof LivingEntity)) {
			FishAmmo.applyEffects(level, getItem().getItem(), target);
		}
		level.playSound(null, target.getX(), target.getY(), target.getZ(), FishGuns.FISH_SPLAT, SoundSource.PLAYERS,
				0.6f + 0.6f * FishAmmo.heft(weight()), 1.3f - 0.6f * FishAmmo.heft(weight()));
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.COD_FLOP, SoundSource.PLAYERS, 1.0f, 1.0f);
	}

	/** It's stopped: a cannonball's shockwave, the splat, and whatever's left of the fish. */
	private void land(ServerLevel level, Vec3 at) {
		float radius = FishAmmo.shockwave(weight());
		if (radius > 0.0f) {
			shockwave(level, at, radius);
		}
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, getItem().getItem()), at.x, at.y, at.z,
				6 + Math.round(18 * FishAmmo.heft(weight())), 0.15, 0.15, 0.15, 0.12);
		level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.1, at.z, 12, 0.25, 0.1, 0.25, 0.1);
		level.playSound(null, at.x, at.y, at.z, FishGuns.FISH_SPLAT, SoundSource.PLAYERS, 0.8f + 0.6f * FishAmmo.heft(weight()),
				1.25f - 0.5f * FishAmmo.heft(weight()));
		ItemStack left = FishAmmo.remains(level, getItem(), scorched);
		if (!left.isEmpty()) {
			ItemEntity drop = new ItemEntity(level, at.x, at.y, at.z, left);
			drop.setDeltaMovement(getDeltaMovement().scale(-0.1).add(0.0, 0.25, 0.0));
			drop.setDefaultPickUpDelay();
			level.addFreshEntity(drop);
		}
		discard();
	}

	/** Everything within {@code radius} is thrown outward and hurt, less with distance. No block is touched. */
	private void shockwave(ServerLevel level, Vec3 at, float radius) {
		Entity owner = getOwner();
		float damage = FishAmmo.damage(weight()) * SHOCKWAVE_DAMAGE;
		float knockback = FishAmmo.knockback(weight());
		DamageSource source = new DamageSource(
				level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(FishGuns.FISH_GUN_DAMAGE), this, owner);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius))) {
			if (victim == owner || struck.contains(victim.getId())) {
				continue;
			}
			Vec3 away = victim.position().add(0.0, victim.getBbHeight() * 0.5, 0.0).subtract(at);
			double d = away.length();
			if (d > radius) {
				continue;
			}
			float f = (float) (1.0 - d / radius);
			victim.hurtServer(level, source, damage * f);
			Vec3 flat = new Vec3(away.x, 0.0, away.z);
			flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0.0, 0.0, 0.0) : flat.normalize();
			double k = knockback * 0.7 * f * (1.0 - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
			victim.push(flat.x * k, 0.25 + 0.35 * f, flat.z * k);
			victim.needsSync = true;
		}
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 2 + Math.round(radius), radius * 0.3, 0.2, radius * 0.3, 0.0);
		level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.2, at.z, 30, radius * 0.4, 0.1, radius * 0.4, 0.15);
		level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.3, at.z, 60, radius * 0.4, 0.3, radius * 0.4, 0.3);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2f, 1.35f);
	}

	/** Into the water it goes, and swims off: the living fish, or (a trophy, or a fish with no living kind) the item. */
	private void release(ServerLevel level) {
		ItemStack fish = getItem();
		EntityType<?> type = fish.has(Fishing.TROPHY) ? null : livingKind(fish.getItem());
		Entity swimmer = type == null ? null : type.create(level, EntitySpawnReason.TRIGGERED);
		if (swimmer != null) {
			swimmer.snapTo(getX(), getY(), getZ(), getYRot(), 0.0f);
			swimmer.setDeltaMovement(getDeltaMovement().scale(0.3));
			level.addFreshEntity(swimmer);
		} else {
			ItemEntity drop = new ItemEntity(level, getX(), getY(), getZ(), fish.has(Fishing.TROPHY) ? FishAmmo.bruised(fish.copy()) : fish.copy());
			level.addFreshEntity(drop);
		}
		level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.3, getZ(), 20, 0.3, 0.1, 0.3, 0.2);
		level.sendParticles(ParticleTypes.BUBBLE, getX(), getY(), getZ(), 12, 0.2, 0.2, 0.2, 0.05);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.8f, 1.2f);
		discard();
	}

	private static EntityType<?> livingKind(Item fish) {
		if (fish == Items.COD) {
			return EntityTypes.COD;
		} else if (fish == Items.SALMON) {
			return EntityTypes.SALMON;
		} else if (fish == Items.PUFFERFISH) {
			return EntityTypes.PUFFERFISH;
		} else if (fish == Items.TROPICAL_FISH) {
			return EntityTypes.TROPICAL_FISH;
		}
		for (FishKind kind : FishKind.ALL) {
			if (kind.catchItem() == fish && !kind.isLava()) {
				return kind.type();
			}
		}
		return null;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putFloat("weight", weight());
		output.putFloat("average", average());
		output.putInt("pierce", pierceLeft);
		output.putBoolean("scorched", scorched);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		getEntityData().set(DATA_WEIGHT, input.getFloatOr("weight", 1.0f));
		getEntityData().set(DATA_AVERAGE, input.getFloatOr("average", 1.0f));
		pierceLeft = input.getIntOr("pierce", 0);
		scorched = input.getBooleanOr("scorched", false);
	}
}
