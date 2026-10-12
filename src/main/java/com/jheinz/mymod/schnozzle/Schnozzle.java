package com.jheinz.mymod.schnozzle;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Schnozzle: a giant human nose, torn off some enormous face, that floats {@value #MIN_HOVER}-{@value #MAX_HOVER}
 * blocks over the ground on a dozen writhing tentacles. It has no gravity and flies round obstacles. It fires volleys
 * of homing snot rockets from alternate nostrils (after a loud sniff), lashes anything that gets close with its
 * tentacles, and now and then winds up a huge sneeze that blows everything in front of it away (see
 * {@link SchnozzleAttackGoal}). Holy light burns it (it's in the {@code mymod:holy_vulnerable} tag).
 *
 * <p>A rare elite rather than a boss: no boss bar, and it despawns like any monster unless it's named or after
 * someone. It gives itself away with a heavy wheeze that carries {@value #WHEEZE_RANGE} blocks. Dying, it deflates
 * and drops out of the air.
 */
public class Schnozzle extends Monster {
	public static final int MIN_HOVER = 4;
	public static final int MAX_HOVER = 8;
	/** How far its presence wheeze can be heard. */
	public static final float WHEEZE_RANGE = 80.0f;
	/** Natural spawns need no other Schnozzle this close. */
	static final double CROWD_RADIUS = 128.0;
	/** How far down it looks for the ground. */
	private static final int GROUND_SEARCH = 24;
	public static final int DEATH_TICKS = 40;

	public static final int IDLE = 0;
	/** Sniffing in before a volley: nostrils flaring. */
	public static final int SNIFFING = 1;
	/** Firing a volley. */
	public static final int FIRING = 2;
	/** Rearing back before a sneeze. */
	public static final int SNEEZE_WINDUP = 3;

	private static final EntityDataAccessor<Byte> DATA_ATTACK = SynchedEntityData.defineId(Schnozzle.class, EntityDataSerializers.BYTE);
	/** The target's entity id, or -1: the client reaches its tentacles toward it. */
	private static final EntityDataAccessor<Integer> DATA_TARGET = SynchedEntityData.defineId(Schnozzle.class, EntityDataSerializers.INT);

	/** Its own cruising height above the ground, so a few of them don't all float at the same level. */
	private final double hoverHeight;
	private int wheezeTicks;
	// Client side: how wide the nostrils are flared (0 to 1), and the jolt of a sneeze going off, decaying from 1.
	private float flare;
	private float flareO;
	private float sneezeJolt;
	private float sneezeJoltO;
	private int lastAttack;

	public Schnozzle(EntityType<? extends Schnozzle> type, Level level) {
		super(type, level);
		xpReward = 50;
		hoverHeight = MIN_HOVER + 1.0 + random.nextDouble() * (MAX_HOVER - MIN_HOVER - 2.0);
		wheezeTicks = 40 + random.nextInt(100);
		moveControl = new FaceTargetMoveControl(this);
		setNoGravity(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 200.0)
				.add(Attributes.ARMOR, 6.0)
				.add(Attributes.ATTACK_DAMAGE, 7.0)
				.add(Attributes.ATTACK_KNOCKBACK, 1.5)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
				.add(Attributes.MOVEMENT_SPEED, 0.25)
				.add(Attributes.FLYING_SPEED, 0.12)
				.add(Attributes.FOLLOW_RANGE, 40.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ATTACK, (byte) IDLE);
		builder.define(DATA_TARGET, -1);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(1, new SchnozzleAttackGoal(this));
		goalSelector.addGoal(5, new SchnozzleDriftGoal(this));
		goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0f));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
		targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, true));
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
		navigation.setCanOpenDoors(false);
		navigation.setCanFloat(true);
		return navigation;
	}

	/** Its body and head always point where it's facing: there's only the one piece of it to turn. */
	@Override
	protected BodyRotationControl createBodyControl() {
		return new BodyRotationControl(this) {
			@Override
			public void clientTick() {
				yBodyRot = getYRot();
				yHeadRot = getYRot();
			}
		};
	}

	/**
	 * Surface only, at night, in the dark, and only if no other Schnozzle is within {@link #CROWD_RADIUS} blocks.
	 * Spawners and eggs skip all of that.
	 */
	public static boolean checkSpawnRules(EntityType<Schnozzle> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos,
			RandomSource random) {
		if (EntitySpawnReason.isSpawner(reason)) {
			return checkMobSpawnRules(type, level, reason, pos, random);
		}
		return checkSurfaceMonstersSpawnRules(type, level, reason, pos, random)
				&& level.getLevel().isDarkOutside()
				&& level.getEntitiesOfClass(Schnozzle.class, new AABB(pos).inflate(CROWD_RADIUS)).isEmpty();
	}

	// Only stays put while it's after someone (or named, which Mob handles).
	@Override
	public boolean removeWhenFarAway(double distanceSq) {
		return getTarget() == null && super.removeWhenFarAway(distanceSq);
	}

	public int attackState() {
		return entityData.get(DATA_ATTACK);
	}

	void setAttackState(int state) {
		entityData.set(DATA_ATTACK, (byte) state);
	}

	/** Client side: what it's reaching for, if anything. */
	public @Nullable Entity syncedTarget() {
		int id = entityData.get(DATA_TARGET);
		return id < 0 ? null : level().getEntity(id);
	}

	public double hoverHeight() {
		return hoverHeight;
	}

	/** The top of the first solid block under the point, searching {@link #GROUND_SEARCH} blocks down from {@code fromY}. */
	public double groundBelow(double x, double fromY, double z) {
		BlockPos.MutableBlockPos pos = BlockPos.containing(x, fromY, z).mutable();
		for (int i = 0; i <= GROUND_SEARCH; i++) {
			VoxelShape shape = level().getBlockState(pos).getCollisionShape(level(), pos);
			if (!shape.isEmpty()) {
				return pos.getY() + shape.max(Direction.Axis.Y);
			}
			pos.move(Direction.DOWN);
		}
		return fromY - GROUND_SEARCH;
	}

	/** The height it likes to float at over a point: its hover height above the ground there. */
	public double cruiseY(double x, double z) {
		return groundBelow(x, getY() + 2.0, z) + hoverHeight;
	}

	/** Where a nostril is (0 left, 1 right), just under the nose, which faces along its body. */
	public Vec3 nostril(int side) {
		float yaw = yBodyRot * Mth.DEG_TO_RAD;
		Vec3 forward = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
		Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
		return position().add(forward.scale(0.5)).add(right.scale(side == 0 ? -0.4 : 0.4)).add(0.0, -0.2, 0.0);
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit) {
			playSound(Schnozzles.LASH, 1.2f, 0.8f + random.nextFloat() * 0.3f);
			if (target instanceof LivingEntity living) {
				// Briefly wrapped up in tentacles.
				living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 2), this);
			}
		}
		return hit;
	}

	@Override
	public void travel(Vec3 input) {
		if (isAlive()) {
			travelFlying(input, getSpeed());
		} else {
			// Dead, it drops out of the air.
			super.travel(input);
		}
	}

	@Override
	protected void checkFallDamage(double fallY, boolean onGround, BlockState onState, BlockPos pos) {
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource source) {
		return false;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (level() instanceof ServerLevel level && isAlive()) {
			LivingEntity target = getTarget();
			int id = target != null && target.isAlive() ? target.getId() : -1;
			if (entityData.get(DATA_TARGET) != id) {
				entityData.set(DATA_TARGET, id);
			}
			if (getNavigation().isDone()) {
				bob();
			}
			if (--wheezeTicks <= 0) {
				level.playSound(null, getX(), getY(), getZ(), Schnozzles.WHEEZE, SoundSource.HOSTILE, 1.0f, 0.85f + random.nextFloat() * 0.2f);
				wheezeTicks = 160 + random.nextInt(140);
			}
		}
	}

	/** At rest: eases up or down to its cruising height, bobbing gently. */
	private void bob() {
		if (attackState() == SNEEZE_WINDUP) {
			return;
		}
		double wanted = cruiseY(getX(), getZ()) + 0.4 * Mth.sin(tickCount * 0.05f);
		Vec3 motion = getDeltaMovement();
		setDeltaMovement(motion.x, motion.y + Mth.clamp((wanted - getY()) * 0.01, -0.02, 0.02), motion.z);
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			flareO = flare;
			sneezeJoltO = sneezeJolt;
			int state = attackState();
			float wanted = state == SNIFFING || state == FIRING ? 1.0f : state == SNEEZE_WINDUP ? 0.8f : 0.0f;
			flare = Mth.approach(flare, wanted, wanted > flare ? 0.08f : 0.05f);
			if (lastAttack == SNEEZE_WINDUP && state != SNEEZE_WINDUP && isAlive()) {
				sneezeJolt = 1.0f;
			}
			sneezeJolt = Math.max(0.0f, sneezeJolt - 0.08f);
			lastAttack = state;
			// Snot drips off the nostrils, faster when they're flared.
			if (isAlive() && random.nextFloat() < 0.04f + 0.2f * flare) {
				Vec3 drip = nostril(random.nextInt(2));
				level().addParticle(ParticleTypes.ITEM_SLIME, drip.x, drip.y, drip.z, 0.0, -0.05, 0.0);
			}
		}
	}

	public float flare(float partialTicks) {
		return Mth.lerp(partialTicks, flareO, flare);
	}

	public float sneezeJolt(float partialTicks) {
		return Mth.lerp(partialTicks, sneezeJoltO, sneezeJolt);
	}

	/** Deflates over {@link #DEATH_TICKS} ticks (see the renderer) while it falls, oozing. */
	@Override
	protected void tickDeath() {
		deathTime++;
		if (deathTime == 1) {
			setNoGravity(false);
			setAttackState(IDLE);
		}
		if (level() instanceof ServerLevel level) {
			if (deathTime % 3 == 0) {
				level.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getY(0.3), getZ(), 6, 0.8, 0.6, 0.8, 0.05);
				level.sendParticles(ParticleTypes.POOF, getX(), getY(0.6), getZ(), 2, 0.5, 0.5, 0.5, 0.02);
			}
			if (deathTime >= DEATH_TICKS && !isRemoved()) {
				level.broadcastEntityEvent(this, (byte) 60);
				remove(RemovalReason.KILLED);
			}
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return Schnozzles.AMBIENT;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 120;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return Schnozzles.HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return Schnozzles.DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 2.0f;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
	}

	/** Flies like any flying mob, but while it's got a target it keeps its nose turned toward it, strafing sideways. */
	private static class FaceTargetMoveControl extends FlyingMoveControl<Schnozzle> {
		FaceTargetMoveControl(Schnozzle schnozzle) {
			super(schnozzle, 10, true);
		}

		@Override
		public void tick() {
			super.tick();
			LivingEntity target = mob.getTarget();
			if (target != null && target.isAlive()) {
				double dx = target.getX() - mob.getX();
				double dz = target.getZ() - mob.getZ();
				float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
				mob.setYRot(rotlerp(mob.getYRot(), yaw, 12.0f));
				mob.yBodyRot = mob.getYRot();
			}
		}
	}
}
