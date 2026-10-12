package com.jheinz.mymod.bank;

import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

import com.jheinz.mymod.GunItem;
import com.jheinz.mymod.ModDamageTypes;
import com.jheinz.mymod.ModItems;
import com.jheinz.mymod.ModSounds;

/**
 * A villager debt collector in a banker's suit, packing a Glock. It rides in a {@link com.jheinz.mymod.tank.Tank}'s
 * hatch, shooting at the debtor while the tank shells them; if the tank is destroyed it carries on on
 * foot. Its shots are hitscan like the player's guns, so plate carriers stop them the same way.
 */
public class DebtCollector extends PathfinderMob implements RangedAttackMob, Collector {
	private static final float BULLET_DAMAGE = 3.0f;
	/** Cone radius in degrees its shots stray within. */
	private static final float SPREAD = 2.5f;
	private static final double RANGE = 40.0;

	private @Nullable UUID debtor;

	public DebtCollector(EntityType<? extends DebtCollector> type, Level level) {
		super(type, level);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 24.0)
				.add(Attributes.MOVEMENT_SPEED, 0.32)
				.add(Attributes.ARMOR, 4.0)
				.add(Attributes.FOLLOW_RANGE, 64.0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		// Keeps its distance on foot: closes to 20 blocks, then stands and shoots.
		goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0, 18, 30, 20.0f));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16.0f));
	}

	/** Hands it its sidearm. It's bank property: it never drops. */
	public void arm() {
		setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.GLOCK));
		setDropChance(EquipmentSlot.MAINHAND, 0.0f);
	}

	@Override
	public @Nullable UUID debtor() {
		return debtor;
	}

	@Override
	public void setDebtor(@Nullable UUID debtor) {
		this.debtor = debtor;
	}

	@Override
	public void tick() {
		super.tick();
		Collectors.tick(this);
	}

	@Override
	public void performRangedAttack(LivingEntity target, float power) {
		if (level() instanceof ServerLevel level) {
			shoot(level, this, target, BULLET_DAMAGE, SPREAD, RANGE, ModSounds.GLOCK_SHOT);
		}
	}

	/**
	 * A hitscan shot from the shooter's eyes at the target's chest, straying up to {@code spread} degrees:
	 * stops at walls, hits whatever living thing is in the way (other collectors aside), and leaves a tracer.
	 */
	public static void shoot(ServerLevel level, LivingEntity shooter, LivingEntity target, float damage, float spread, double range,
			SoundEvent sound) {
		Vec3 eye = shooter.getEyePosition();
		Vec3 aim = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0).subtract(eye);
		float yaw = (float) Math.toDegrees(Math.atan2(-aim.x, aim.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(aim.y, aim.horizontalDistance()));
		float radius = spread * (float) Math.sqrt(shooter.getRandom().nextFloat());
		float angle = shooter.getRandom().nextFloat() * (float) (Math.PI * 2.0);
		Vec3 look = Vec3.directionFromRotation(pitch + radius * (float) Math.sin(angle), yaw + radius * (float) Math.cos(angle));
		Vec3 end = eye.add(look.scale(range));

		HitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
		if (blockHit.getType() != HitResult.Type.MISS) {
			end = blockHit.getLocation();
		}
		AABB sweep = shooter.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(shooter, eye, end, sweep,
				entity -> !entity.isSpectator() && entity.isPickable() && !Collectors.isCollector(entity), eye.distanceToSqr(end));
		Vec3 muzzle = eye.add(look.scale(0.8)).add(0.0, -0.3, 0.0);
		if (entityHit != null) {
			end = entityHit.getLocation();
			DamageSource source = new DamageSource(
					level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ModDamageTypes.BULLET), shooter);
			entityHit.getEntity().hurtServer(level, source, damage);
			level.sendParticles(ParticleTypes.CRIT, end.x, end.y, end.z, 6, 0.1, 0.1, 0.1, 0.2);
		}
		level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 2, 0.03, 0.03, 0.03, 0.01);
		GunItem.drawTracer(level, muzzle, end, false);
		level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), sound, SoundSource.HOSTILE, 1.5f,
				0.95f + shooter.getRandom().nextFloat() * 0.1f);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return !Collectors.isFriendlyFire(source) && super.hurtServer(level, source, damage);
	}

	/** The tank drives itself: its crew only shoots. */
	@Override
	public boolean canControlVehicle() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		Collectors.save(output, debtor);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		debtor = Collectors.load(input);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.VILLAGER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.VILLAGER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.VILLAGER_DEATH;
	}
}
