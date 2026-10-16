package com.jheinz.tomyen.skullcrane;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
 * A skullcrane: a bone-white human skull kept flying by an antigrav emitter underneath, with red optic lenses in its
 * eye sockets, a servo-hinged jaw and four segmented servo arms ending in grabber claws. It hovers
 * {@value #MIN_HOVER}-{@value #MAX_HOVER} blocks off the ground with a drone's jitter, and hunts in packs: it
 * keeps near its packmates (following its {@link SkullcraneLeader pack leader} if it has one, otherwise the eldest, see
 * {@link SkullcranePackGoal}), shares its target with them, and calls them all in when it's hurt. A pack that loses
 * its leader panics, scattering for a few seconds. Alone it can only scratch; a pack grabs its prey between them and carries it up
 * to drop it (see {@link SkullcraneHuntGoal} and {@link SkullcraneGrip}).
 *
 * <p>Undead after a fashion, so smite and holy light (it's in the {@code tomyen:holy_vulnerable} tag) hurt it more,
 * and it neither drowns nor takes fall damage. Clamped onto someone, a hard enough knock ({@value #LET_GO_DAMAGE}
 * damage all told) makes it let go. Dying, its lenses go dark and it drops out of the air in a shower of sparks.
 */
public class Skullcrane extends Monster {
	public static final double MIN_HOVER = 2.0;
	public static final double MAX_HOVER = 5.0;
	/** Damage, all told, that knocks a clamped-on skull loose. */
	static final float LET_GO_DAMAGE = 5.0f;
	/** How far off it counts other skullcranes as its pack. */
	static final double PACK_RADIUS = 20.0;
	/** Natural packs: one in this many comes with a leader (and always a pack of {@link Skullcranes#MAX_PACK}). */
	private static final int LEADER_CHANCE = 3;
	/** Natural spawns need fewer than {@link #CROWD_LIMIT} skullcranes this close (a couple of packs). */
	private static final double CROWD_RADIUS = 64.0;
	private static final int CROWD_LIMIT = 8;
	private static final int GROUND_SEARCH = 16;

	public static final int IDLE = 0;
	/** After something: arms flared out. */
	public static final int HUNTING = 1;
	/** Clamped onto its prey. */
	public static final int CLAMPED = 2;

	private static final EntityDataAccessor<Byte> DATA_MODE = SynchedEntityData.defineId(Skullcrane.class, EntityDataSerializers.BYTE);
	/** Its target's (or, clamped on, its victim's) entity id, or -1: the client reaches for it. */
	private static final EntityDataAccessor<Integer> DATA_TARGET = SynchedEntityData.defineId(Skullcrane.class, EntityDataSerializers.INT);
	/** Clamped on: its place in the ring round the victim, and how many are in the ring. */
	private static final EntityDataAccessor<Byte> DATA_SLOT = SynchedEntityData.defineId(Skullcrane.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Byte> DATA_SLOTS = SynchedEntityData.defineId(Skullcrane.class, EntityDataSerializers.BYTE);

	/** Its own cruising height, so a pack doesn't all fly at one level. */
	private final double hoverHeight;
	// Server side.
	private @Nullable SkullcraneGrip grip;
	private float damageWhileClamped;
	/** Ticks before it'll try to grab anything again. */
	int grabCooldown;
	private int alertTicks;
	/** Scattering after its leader died: ticks left, and where from. */
	private int panicTicks;
	private Vec3 panicFrom = Vec3.ZERO;
	// Client side: arms flared (0 to 1), claws clamped shut (0 to 1), jaw chattering (0 to 1).
	private float flare;
	private float flareO;
	private float clamp;
	private float clampO;
	private float chatter;
	private float chatterO;

	public Skullcrane(EntityType<? extends Skullcrane> type, Level level) {
		super(type, level);
		xpReward = 6;
		hoverHeight = MIN_HOVER + 0.5 + random.nextDouble() * (MAX_HOVER - MIN_HOVER - 1.0);
		moveControl = new FlyingMoveControl<>(this, 30, true);
		setNoGravity(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 12.0)
				.add(Attributes.ARMOR, 2.0)
				.add(Attributes.ATTACK_DAMAGE, 2.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.FLYING_SPEED, 0.16)
				.add(Attributes.FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_MODE, (byte) IDLE);
		builder.define(DATA_TARGET, -1);
		builder.define(DATA_SLOT, (byte) 0);
		builder.define(DATA_SLOTS, (byte) 1);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(1, new SkullcraneHuntGoal(this));
		goalSelector.addGoal(5, new SkullcranePackGoal(this));
		goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0f));
		targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
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

	/**
	 * Surface only, at night, in the dark, and not where there are already a couple of packs about. Spawners and eggs
	 * skip all of that.
	 */
	public static boolean checkSpawnRules(EntityType<Skullcrane> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos,
			RandomSource random) {
		if (EntitySpawnReason.isSpawner(reason)) {
			return checkMobSpawnRules(type, level, reason, pos, random);
		}
		return checkSurfaceMonstersSpawnRules(type, level, reason, pos, random)
				&& level.getLevel().isDarkOutside()
				&& level.getEntitiesOfClass(Skullcrane.class, new AABB(pos).inflate(CROWD_RADIUS)).size() < CROWD_LIMIT;
	}

	/**
	 * Shared by one pack as it spawns: whether it's to have a leader, whether that's been spawned yet, and how many
	 * skulls there are so far. Also marks the rest of a pack hatched from one spawn egg, so they don't each hatch a pack
	 * of their own.
	 */
	private static final class PackData implements SpawnGroupData {
		private final boolean led;
		private boolean leaderSpawned;
		private int members;

		PackData(boolean led) {
			this.led = led;
		}
	}

	/** Whether a spawn egg of this hatches a whole pack (a leader's hatches just the leader). */
	protected boolean hatchesPack() {
		return true;
	}

	/**
	 * A spawn egg hatches a whole pack, and a natural pack may come with a leader: one in {@value #LEADER_CHANCE} do,
	 * and every pack of {@link Skullcranes#MAX_PACK}.
	 */
	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
			@Nullable SpawnGroupData groupData) {
		groupData = super.finalizeSpawn(level, difficulty, reason, groupData);
		if (reason == EntitySpawnReason.SPAWN_ITEM_USE && hatchesPack() && !(groupData instanceof PackData)) {
			int mates = Skullcranes.MIN_PACK - 1 + random.nextInt(Skullcranes.MAX_PACK - Skullcranes.MIN_PACK + 1);
			PackData pack = new PackData(mates + 1 >= Skullcranes.MAX_PACK || random.nextInt(LEADER_CHANCE) == 0);
			for (int i = 0; i < mates; i++) {
				Skullcrane mate = Skullcranes.SKULLCRANE.create(level.getLevel(), EntitySpawnReason.MOB_SUMMONED);
				if (mate == null) {
					continue;
				}
				float angle = Mth.TWO_PI * i / mates;
				mate.snapTo(getX() + Mth.cos(angle) * 1.5, getY() + 0.5 + random.nextDouble(), getZ() + Mth.sin(angle) * 1.5,
						random.nextFloat() * 360.0f, 0.0f);
				mate.finalizeSpawn(level, difficulty, EntitySpawnReason.MOB_SUMMONED, pack);
				level.addFreshEntityWithPassengers(mate);
			}
			if (pack.led) {
				spawnLeader(level, difficulty, pack);
			}
		} else if (reason == EntitySpawnReason.NATURAL && hatchesPack()) {
			PackData pack = groupData instanceof PackData data ? data : new PackData(random.nextInt(LEADER_CHANCE) == 0);
			groupData = pack;
			pack.members++;
			if (!pack.leaderSpawned && (pack.led || pack.members >= Skullcranes.MAX_PACK)) {
				spawnLeader(level, difficulty, pack);
			}
		}
		return groupData;
	}

	/** A leader for the pack, just above this one if there's room. */
	private void spawnLeader(ServerLevelAccessor level, DifficultyInstance difficulty, PackData pack) {
		SkullcraneLeader leader = Skullcranes.SKULLCRANE_LEADER.create(level.getLevel(), EntitySpawnReason.MOB_SUMMONED);
		if (leader == null) {
			return;
		}
		leader.snapTo(getX(), getY() + 1.0, getZ(), random.nextFloat() * 360.0f, 0.0f);
		if (!level.noCollision(leader)) {
			return;
		}
		pack.leaderSpawned = true;
		leader.finalizeSpawn(level, difficulty, EntitySpawnReason.MOB_SUMMONED, pack);
		level.addFreshEntityWithPassengers(leader);
	}

	// Only stays put while it's after something (or named, which Mob handles).
	@Override
	public boolean removeWhenFarAway(double distanceSq) {
		return getTarget() == null && grip == null && super.removeWhenFarAway(distanceSq);
	}

	// ------------------------------------------------------------------------------------------------ state

	public int mode() {
		return entityData.get(DATA_MODE);
	}

	private void setMode(int mode) {
		entityData.set(DATA_MODE, (byte) mode);
	}

	public boolean isClamped() {
		return mode() == CLAMPED;
	}

	public int slot() {
		return entityData.get(DATA_SLOT);
	}

	public int slots() {
		return Math.max(1, entityData.get(DATA_SLOTS));
	}

	/** Client side: what it's reaching for or clamped onto, if anything. */
	public @Nullable Entity syncedTarget() {
		int id = entityData.get(DATA_TARGET);
		return id < 0 ? null : level().getEntity(id);
	}

	/** Server side: the grip it's clamped into, if any. */
	public @Nullable SkullcraneGrip grip() {
		return grip;
	}

	public double hoverHeight() {
		return hoverHeight;
	}

	/** Packmates in range: every other living skullcrane within {@link #PACK_RADIUS} blocks. */
	List<Skullcrane> packmates() {
		return level().getEntitiesOfClass(Skullcrane.class, getBoundingBox().inflate(PACK_RADIUS),
				other -> other != this && other.isAlive());
	}

	/** The nearest pack leader in range, if it isn't one itself. */
	@Nullable SkullcraneLeader packLeader() {
		if (this instanceof SkullcraneLeader) {
			return null;
		}
		SkullcraneLeader nearest = null;
		for (Skullcrane mate : packmates()) {
			if (mate instanceof SkullcraneLeader leader && (nearest == null || distanceToSqr(leader) < distanceToSqr(nearest))) {
				nearest = leader;
			}
		}
		return nearest;
	}

	/** How many ordinary skulls' worth of lift it gives a grip. */
	public int liftWeight() {
		return 1;
	}

	/** Damage, all told, that knocks it loose while it's clamped on. */
	protected float letGoDamage() {
		return LET_GO_DAMAGE;
	}

	/** Where it's held, clamped on, relative to its victim's feet (see {@link SkullcraneGrip#holdOffset}). */
	public Vec3 holdOffset(float bodyYaw, float width, float height, int slot, int count) {
		return SkullcraneGrip.holdOffset(bodyYaw, width, height, slot, count);
	}

	public boolean isPanicking() {
		return panicTicks > 0;
	}

	/** Its leader's down: it lets go of anything it's holding and scatters away from {@code from} for a while. */
	void panic(Vec3 from, int ticks) {
		if (grip != null) {
			grip.drop(this, ticks);
		}
		panicFrom = from;
		panicTicks = ticks;
		super.setTarget(null);
		getNavigation().stop();
		playSound(Skullcranes.CHATTER, 1.0f, 1.4f + random.nextFloat() * 0.3f);
	}

	// Scattering, it won't take up a target.
	@Override
	public void setTarget(@Nullable LivingEntity target) {
		if (target == null || panicTicks <= 0) {
			super.setTarget(target);
		}
	}

	void clampTo(SkullcraneGrip grip) {
		this.grip = grip;
		damageWhileClamped = 0.0f;
		getNavigation().stop();
		setDeltaMovement(Vec3.ZERO);
		setMode(CLAMPED);
	}

	/** Placed by its grip: at a point over the victim's shoulders, facing in. */
	void holdAt(Vec3 pos, float yaw, int slot, int count) {
		setPos(pos);
		setDeltaMovement(Vec3.ZERO);
		setYRot(yaw);
		setXRot(30.0f);
		yBodyRot = yaw;
		yHeadRot = yaw;
		entityData.set(DATA_SLOT, (byte) slot);
		entityData.set(DATA_SLOTS, (byte) count);
	}

	/** Lets go (the grip has already let go of it), springing up and away, and won't grab again for a while. */
	void detach(int cooldown) {
		if (grip == null) {
			return;
		}
		Vec3 away = position().subtract(grip.position()).horizontal();
		away = away.lengthSqr() < 1.0e-4 ? Vec3.ZERO : away.normalize();
		grip = null;
		grabCooldown = Math.max(grabCooldown, cooldown);
		setMode(getTarget() != null ? HUNTING : IDLE);
		setDeltaMovement(away.scale(0.35).add(0.0, 0.15, 0.0));
	}

	// ------------------------------------------------------------------------------------------------ flying

	@Override
	public void travel(Vec3 input) {
		if (!isAlive()) {
			// Dead, it drops out of the air.
			super.travel(input);
		} else if (!isClamped()) {
			travelFlying(input, getSpeed());
		}
		// Clamped on, its grip places it.
	}

	@Override
	public boolean isPushable() {
		return !isClamped() && super.isPushable();
	}

	@Override
	protected void pushEntities() {
		if (!isClamped()) {
			super.pushEntities();
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
	public boolean canBreatheUnderwater() {
		return true;
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

	/** The height it likes to fly at over a point: its hover height above the ground there. */
	public double cruiseY(double x, double z) {
		return groundBelow(x, getY() + 2.0, z) + hoverHeight;
	}

	// ------------------------------------------------------------------------------------------------ ticking

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel level) || !isAlive()) {
			return;
		}
		grabCooldown = Math.max(0, grabCooldown - 1);
		if (grip != null && (grip.isRemoved() || !isClamped())) {
			grip = null;
		}
		if (panicTicks > 0) {
			panicTicks--;
			scatter();
		}
		LivingEntity victim = grip != null ? grip.victim() : null;
		if (victim != null && getTarget() != victim) {
			// Clamped on, it's after whatever it's holding, whoever hits it.
			setTarget(victim);
		}
		LivingEntity target = getTarget();
		if (target != null && !target.isAlive()) {
			target = null;
		}
		int id = target != null ? target.getId() : -1;
		if (entityData.get(DATA_TARGET) != id) {
			entityData.set(DATA_TARGET, id);
		}
		if (grip == null) {
			int mode = target != null ? HUNTING : IDLE;
			if (mode() != mode) {
				setMode(mode);
			}
			jitter();
			if (target == null && getNavigation().isDone()) {
				bob();
			}
		}
		if (target != null && --alertTicks <= 0) {
			// The pack shares its prey; a leader's prey is everyone's.
			boolean leading = this instanceof SkullcraneLeader;
			for (Skullcrane mate : packmates()) {
				LivingEntity theirs = mate.getTarget();
				if ((theirs == null || leading && theirs != target && !mate.isClamped()) && mate.canAttack(target)) {
					mate.setTarget(target);
				}
			}
			alertTicks = 10;
		}
	}

	/** Flies off away from where its leader fell, picking a new spot every second or when it gets there. */
	private void scatter() {
		if (!getNavigation().isDone() && panicTicks % 20 != 0) {
			return;
		}
		Vec3 away = position().subtract(panicFrom).horizontal();
		away = away.lengthSqr() < 1.0e-4 ? new Vec3(random.nextGaussian(), 0.0, random.nextGaussian()) : away;
		away = away.normalize().yRot((random.nextFloat() - 0.5f) * 1.2f).scale(8.0 + random.nextDouble() * 6.0);
		double x = getX() + away.x;
		double z = getZ() + away.z;
		getNavigation().moveTo(x, cruiseY(x, z) + 2.0, z, 1.6);
	}

	/** Drone twitchiness: little random nudges, and a shove apart from any packmate it's bumping into. */
	private void jitter() {
		Vec3 motion = getDeltaMovement();
		if (random.nextInt(3) == 0) {
			motion = motion.add(random.nextGaussian() * 0.025, random.nextGaussian() * 0.02, random.nextGaussian() * 0.025);
		}
		for (Skullcrane other : level().getEntitiesOfClass(Skullcrane.class, getBoundingBox().inflate(0.5), other -> other != this)) {
			Vec3 away = position().subtract(other.position());
			if (away.lengthSqr() > 1.0e-4) {
				motion = motion.add(away.normalize().scale(0.03));
			}
		}
		setDeltaMovement(motion);
	}

	/** At rest: eases up or down to its cruising height, bobbing. */
	private void bob() {
		double wanted = cruiseY(getX(), getZ()) + 0.3 * Mth.sin(tickCount * 0.09f + getId());
		Vec3 motion = getDeltaMovement();
		setDeltaMovement(motion.x, motion.y + Mth.clamp((wanted - getY()) * 0.02, -0.03, 0.03), motion.z);
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide()) {
			return;
		}
		flareO = flare;
		clampO = clamp;
		chatterO = chatter;
		int mode = mode();
		Entity target = syncedTarget();
		flare = Mth.approach(flare, mode == HUNTING ? 1.0f : 0.0f, 0.1f);
		clamp = Mth.approach(clamp, mode == CLAMPED ? 1.0f : 0.0f, mode == CLAMPED ? 0.25f : 0.1f);
		boolean attacking = mode == CLAMPED || getSwingAnimation(1.0f) > 0.0f
				|| mode == HUNTING && target != null && distanceToSqr(target) < 16.0;
		chatter = Mth.approach(chatter, attacking && isAlive() ? 1.0f : 0.0f, 0.15f);
		if (mode == CLAMPED && target instanceof LivingEntity victim) {
			// Glued on where the grip holds it, rather than trailing behind its position updates.
			setPos(victim.position().add(holdOffset(victim.yBodyRot, victim.getBbWidth(), victim.getBbHeight(), slot(), slots())));
		}
		if (isAlive() && random.nextInt(mode == CLAMPED ? 6 : 30) == 0) {
			// The antigrav emitter underneath crackles.
			level().addParticle(ParticleTypes.ELECTRIC_SPARK, getX() + random.nextGaussian() * 0.1, getY() + 0.05,
					getZ() + random.nextGaussian() * 0.1, 0.0, -0.05, 0.0);
		}
	}

	public float flare(float partialTicks) {
		return Mth.lerp(partialTicks, flareO, flare);
	}

	public float clamp(float partialTicks) {
		return Mth.lerp(partialTicks, clampO, clamp);
	}

	public float chatter(float partialTicks) {
		return Mth.lerp(partialTicks, chatterO, chatter);
	}

	// ------------------------------------------------------------------------------------------------ fighting

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit) {
			playSound(Skullcranes.SCRATCH, 1.0f, 0.9f + random.nextFloat() * 0.3f);
			playSound(Skullcranes.CHATTER, 0.8f, 0.9f + random.nextFloat() * 0.3f);
		}
		return hit;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && grip != null && isAlive()) {
			damageWhileClamped += damage;
			if (damageWhileClamped >= letGoDamage()) {
				// Knocked loose.
				grip.drop(this, SkullcraneGrip.REGROUP_TICKS / 2);
			}
		}
		return hurt;
	}

	@Override
	public void die(DamageSource source) {
		if (grip != null) {
			grip.drop(this, 0);
		}
		super.die(source);
	}

	@Override
	protected void tickDeath() {
		if (deathTime == 0) {
			setNoGravity(false);
			setMode(IDLE);
		}
		if (level() instanceof ServerLevel level && deathTime % 4 == 0) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY(0.4), getZ(), 4, 0.2, 0.2, 0.2, 0.1);
			level.sendParticles(ParticleTypes.SMOKE, getX(), getY(0.5), getZ(), 2, 0.15, 0.15, 0.15, 0.01);
		}
		super.tickDeath();
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return Skullcranes.AMBIENT;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 100;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return Skullcranes.HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return Skullcranes.DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
	}
}
