package com.jheinz.tomyen.fishing.catfish;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.ModDamageTypes;
import com.jheinz.tomyen.fishing.FishSize;
import com.jheinz.tomyen.fishing.FishingRecords;
import com.jheinz.tomyen.fishing.FishingSpecies;
import com.jheinz.tomyen.fishing.RodHook;
import com.jheinz.tomyen.fishing.TrophyFish;
import com.jheinz.tomyen.gunsmith.HarpoonGunItem;

/**
 * The Giant Catfish, the River Monster: an ancient flathead five blocks long that lies in the deep holes of rivers. It's
 * a boss (a health bar for everyone near while it's fighting), and it only fights in the water: on land it flops about,
 * trying for the river, and slowly dries out.
 *
 * <p>Its attacks ({@link CatfishAttackGoal}): it lines up and <b>charges</b> through the water, ramming whatever's in the
 * way and overturning boats; it <b>drags</b>, seizing someone in or beside the water and hauling them under and away
 * down the river for a few seconds, shaking them, as their air runs out and their head spins (they get loose when it's
 * hurt badly enough, when it tires, or when a harpoon hits it); it <b>slaps</b> with its tail at anything beside or
 * behind it, knocking people and boats flying; and up close it <b>bites</b>.
 *
 * <p>A Harpoon Gun is the tool for it: harpoons hit it {@value #HARPOON_FACTOR} times as hard, and the line hauls it
 * toward the shooter and stuns it for a moment, letting go of anyone it's dragging.
 *
 * <p>It's met two ways: rarely, in deep river water, or on a rod, fishing a river at night (Stink Bait draws it): won on
 * the line it doesn't land but comes up roaring at the angler ({@link #surface}), already a little tired. Each one has a
 * weight, rolled from its species file as a caught fish's is, which sets its size; killed by a player it gives up a
 * Giant Catfish trophy of that weight for a Giant Fish Mount, and it counts for the angler's records.
 */
public class GiantCatfish extends Monster {
	public static final int IDLE = 0;
	/** Just hooked: rising out of the water and roaring. */
	public static final int SURFACING = 1;
	/** Lining up a charge: roaring, turning to its target. */
	public static final int WINDUP = 2;
	public static final int CHARGING = 3;
	/** Dragging someone under. */
	public static final int DRAGGING = 4;
	public static final int SLAPPING = 5;
	public static final int BITING = 6;
	/** Harpooned: thrashing on the line. */
	public static final int STUNNED = 7;

	public static final int SURFACE_TICKS = 40;
	public static final int SLAP_TICKS = 20;
	public static final int BITE_TICKS = 10;
	public static final int STUN_TICKS = 30;
	/** How long a drag lasts at most. */
	public static final int DRAG_TICKS = 80;
	/** A harpoon hits this many times as hard. */
	public static final float HARPOON_FACTOR = 1.75f;
	/** A drag breaks once it's taken this share of its health, or {@link #DRAG_BREAK_MIN} if that's more. */
	private static final float DRAG_BREAK_SHARE = 0.12f;
	private static final float DRAG_BREAK_MIN = 20.0f;
	/** A fish that came up on a line starts this tired. */
	private static final float HOOKED_HEALTH = 0.8f;
	private static final double BOSS_RANGE = 48.0;
	/** Its species' typical weight, if the species files don't say. */
	public static final float TYPICAL_WEIGHT = 320.0f;
	/** Natural spawns: none within this of another. */
	private static final double LONER_RADIUS = 96.0;
	/** Out of water this long, it starts to dry out. */
	private static final int DRY_GRACE = 100;

	private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(GiantCatfish.class, EntityDataSerializers.BYTE);
	/** Which way the tail slap swings: true for its right side. */
	private static final EntityDataAccessor<Boolean> DATA_SLAP_RIGHT = SynchedEntityData.defineId(GiantCatfish.class,
			EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Float> DATA_WEIGHT = SynchedEntityData.defineId(GiantCatfish.class, EntityDataSerializers.FLOAT);

	private final ServerBossEvent bossEvent = new ServerBossEvent(java.util.UUID.randomUUID(),
			Component.translatable("boss.tomyen.giant_catfish"), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.NOTCHED_10);

	// Server side.
	private int stateTicks;
	/** Came up on a line, so it stays until the fight's done. */
	private boolean hooked;
	private int dryTicks;
	private int flopDelay;
	private @Nullable LivingEntity dragged;
	private Vec3 dragHeading = Vec3.ZERO;
	private float dragDamage;
	int dragCooldown;

	// Client side: animation, each with its value last tick for smoothing.
	private float swim;
	private float swimO;
	private float tail;
	private float tailO;
	private float mouth;
	private float mouthO;
	private float roar;
	private float roarO;
	private float thrash;
	private float thrashO;
	private float pitch;
	private float pitchO;
	private int clientState;
	private int clientStateTicks;

	public GiantCatfish(EntityType<? extends GiantCatfish> type, Level level) {
		super(type, level);
		xpReward = 60;
		moveControl = new CatfishMoveControl(this);
		setPathfindingMalus(PathType.WATER, 0.0f);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 160.0)
				.add(Attributes.ARMOR, 6.0)
				.add(Attributes.ATTACK_DAMAGE, 9.0)
				.add(Attributes.ATTACK_KNOCKBACK, 1.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
				.add(Attributes.MOVEMENT_SPEED, 0.6)
				.add(Attributes.FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_STATE, (byte) IDLE);
		builder.define(DATA_SLAP_RIGHT, false);
		builder.define(DATA_WEIGHT, TYPICAL_WEIGHT);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(1, new CatfishAttackGoal(this));
		goalSelector.addGoal(5, new RandomSwimmingGoal(this, 0.7, 60));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		// It goes for people in or on the water, or right by it; not for anyone walking past on the bank.
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
				(target, level) -> target.isInWater() || target.getVehicle() instanceof AbstractBoat || target.distanceToSqr(this) < 36.0));
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		return new WaterBoundPathNavigation(this, level);
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	@Override
	public int getMaxHeadXRot() {
		return 20;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 240;
	}

	// --- Size and weight -----------------------------------------------------------------------------------------

	/** Its weight in lb: what its trophy will weigh. */
	public float weight() {
		return entityData.get(DATA_WEIGHT);
	}

	void setWeight(float weight) {
		entityData.set(DATA_WEIGHT, weight);
	}

	/** How big it's drawn and boxed, against a typical fish: a fish twice the weight is about a fifth longer. */
	public float sizeScale() {
		return Mth.clamp((float) Math.pow(weight() / TYPICAL_WEIGHT, 0.25), 0.85f, 1.15f);
	}

	@Override
	protected EntityDimensions getDefaultDimensions(Pose pose) {
		return super.getDefaultDimensions(pose).scale(sizeScale());
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (DATA_WEIGHT.equals(accessor)) {
			refreshDimensions();
		}
	}

	private static @Nullable FishSize size() {
		FishingSpecies species = FishingSpecies.forItem(GiantCatfishes.GIANT_CATFISH);
		return species != null ? species.size() : null;
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
			@Nullable SpawnGroupData groupData) {
		FishSize size = size();
		RandomSource random = level.getRandom();
		setWeight(size != null ? size.roll(random, 0.0f) : FishSize.round(220.0f + random.nextFloat() * 260.0f));
		return super.finalizeSpawn(level, difficulty, reason, groupData);
	}

	// --- Spawning ------------------------------------------------------------------------------------------------

	/**
	 * Deep in a river: open water all round it and two blocks above and below, away from any other giant catfish, and more
	 * often at night (one try in three by day). Spawners and eggs skip all that.
	 */
	public static boolean checkSpawnRules(EntityType<GiantCatfish> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos,
			RandomSource random) {
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			return false;
		}
		if (EntitySpawnReason.isSpawner(reason)) {
			return true;
		}
		if (!level.getLevel().isDarkOutside() && random.nextInt(3) != 0 || pos.getY() > level.getSeaLevel() - 2) {
			return false;
		}
		for (BlockPos probe : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 2, 1))) {
			if (!level.getFluidState(probe).is(FluidTags.WATER)) {
				return false;
			}
		}
		return level.getEntitiesOfClass(GiantCatfish.class, new AABB(pos).inflate(LONER_RADIUS)).isEmpty();
	}

	@Override
	public boolean checkSpawnRules(LevelAccessor level, EntitySpawnReason reason) {
		return true;
	}

	@Override
	public boolean checkSpawnObstruction(LevelReader level) {
		return level.isUnobstructed(this);
	}

	@Override
	public boolean removeWhenFarAway(double distanceSq) {
		return !hooked && getTarget() == null && super.removeWhenFarAway(distanceSq);
	}

	/**
	 * Won on a rod ({@link com.jheinz.tomyen.fishing.FishingFights.Surfacing}): it comes up at the bobber, roaring, after
	 * the angler. Not in Peaceful, where it's landed as an ordinary (if enormous) catch.
	 */
	public static boolean surface(ServerLevel level, ServerPlayer player, RodHook hook, FishingSpecies species, float weight) {
		if (!species.stack().is(GiantCatfishes.GIANT_CATFISH) || level.getDifficulty() == Difficulty.PEACEFUL) {
			return false;
		}
		GiantCatfish fish = GiantCatfishes.GIANT_CATFISH_ENTITY.create(level, EntitySpawnReason.EVENT);
		if (fish == null) {
			return false;
		}
		fish.setWeight(weight > 0.0f ? weight : TYPICAL_WEIGHT);
		float yaw = (float) (Mth.atan2(player.getZ() - hook.getZ(), player.getX() - hook.getX()) * Mth.RAD_TO_DEG) - 90.0f;
		fish.snapTo(hook.getX(), hook.getY() - 1.6, hook.getZ(), yaw, 0.0f);
		fish.yBodyRot = yaw;
		fish.yHeadRot = yaw;
		// Out of the bank or the bed: up until it's clear.
		for (int i = 0; i < 4 && !level.noCollision(fish); i++) {
			fish.setPos(fish.getX(), fish.getY() + 0.5, fish.getZ());
		}
		fish.finalizeSpawn(level, level.getCurrentDifficultyAt(fish.blockPosition()), EntitySpawnReason.EVENT, null);
		fish.setWeight(weight > 0.0f ? weight : fish.weight());
		fish.hooked = true;
		fish.setPersistenceRequired();
		fish.setHealth(fish.getMaxHealth() * HOOKED_HEALTH);
		fish.setTarget(player);
		level.addFreshEntity(fish);
		fish.setState(SURFACING);
		fish.capsizeAround(4.0, 0.6);
		fish.playSound(GiantCatfishes.ROAR, 3.0f, 0.9f);
		level.playSound(null, hook.getX(), hook.getY(), hook.getZ(), GiantCatfishes.SPLASH, SoundSource.HOSTILE, 2.0f, 0.8f);
		level.sendParticles(ParticleTypes.SPLASH, hook.getX(), hook.getY() + 0.3, hook.getZ(), 60, 1.2, 0.2, 1.2, 0.3);
		level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, hook.getX(), hook.getY() - 0.5, hook.getZ(), 40, 1.0, 0.5, 1.0, 0.1);
		player.sendSystemMessage(Component.translatable("chat.tomyen.giant_catfish.surfaces").withStyle(ChatFormatting.DARK_GREEN));
		return true;
	}

	// --- State ---------------------------------------------------------------------------------------------------

	public int state() {
		return entityData.get(DATA_STATE);
	}

	void setState(int state) {
		entityData.set(DATA_STATE, (byte) state);
		stateTicks = 0;
	}

	int stateTicks() {
		return stateTicks;
	}

	public boolean slapRight() {
		return entityData.get(DATA_SLAP_RIGHT);
	}

	void setSlapRight(boolean right) {
		entityData.set(DATA_SLAP_RIGHT, right);
	}

	/** Facing, flat. */
	Vec3 forward() {
		float yaw = getYRot() * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
	}

	/** Where its mouth is: the front of its jaw. */
	public Vec3 mouth() {
		float s = sizeScale();
		return position().add(forward().scale(2.1 * s)).add(0.0, 0.45 * s, 0.0);
	}

	/** Where its tail fin sweeps. */
	public Vec3 tailTip() {
		float s = sizeScale();
		return position().subtract(forward().scale(2.5 * s)).add(0.0, 0.5 * s, 0.0);
	}

	/** Whether {@code target} is somewhere it can be seized: in the water, in a boat, or on the bank right beside it. */
	boolean grabbable(LivingEntity target) {
		if (target instanceof Player player && (player.isCreative() || player.isSpectator())) {
			return false;
		}
		if (target.isInWater() || target.getVehicle() instanceof AbstractBoat) {
			return true;
		}
		BlockPos feet = target.blockPosition();
		for (BlockPos probe : BlockPos.betweenClosed(feet.offset(-1, -1, -1), feet.offset(1, 0, 1))) {
			if (level().getFluidState(probe).is(FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}

	/** The target, if it's still worth fighting. */
	@Nullable LivingEntity liveTarget() {
		LivingEntity target = getTarget();
		if (target == null || !target.isAlive() || !canAttack(target)) {
			return null;
		}
		return target instanceof Player player && (player.isCreative() || player.isSpectator()) ? null : target;
	}

	public boolean isDragging() {
		return dragged != null;
	}

	// --- Dragging ------------------------------------------------------------------------------------------------

	/** Seizes {@code victim} and starts hauling them under: out of any boat, spinning, the air going out of them. */
	void startDrag(LivingEntity victim) {
		if (victim.getVehicle() instanceof AbstractBoat boat) {
			capsize(boat, position(), 0.5);
		}
		victim.stopRiding();
		dragged = victim;
		dragDamage = 0.0f;
		dragHeading = forward();
		setState(DRAGGING);
		getNavigation().stop();
		victim.addEffect(new MobEffectInstance(MobEffects.NAUSEA, DRAG_TICKS + 60, 0), this);
		playSound(GiantCatfishes.BITE, 2.0f, 0.8f);
		if (level() instanceof ServerLevel level) {
			doHurtTarget(level, victim);
		}
	}

	/** Each tick of a drag: on down the river, the victim in its jaws, shaken now and then. */
	private void tickDrag(ServerLevel level) {
		LivingEntity victim = dragged;
		float threshold = Math.max(DRAG_BREAK_MIN, DRAG_BREAK_SHARE * getMaxHealth());
		if (victim == null || !victim.isAlive() || victim.isRemoved() || stateTicks > DRAG_TICKS || dragDamage >= threshold
				|| victim.distanceToSqr(mouth()) > 36.0 || !isInWater()) {
			releaseDrag();
			return;
		}
		// Swim on the way it was going; turn along the bank where it's blocked, and keep low.
		Vec3 ahead = position().add(dragHeading.scale(3.0 * sizeScale()));
		if (!water(BlockPos.containing(ahead.x, getY() + 0.5, ahead.z))) {
			Vec3 left = new Vec3(dragHeading.z, 0.0, -dragHeading.x);
			Vec3 right = left.reverse();
			if (water(BlockPos.containing(getX() + left.x * 3.0, getY() + 0.5, getZ() + left.z * 3.0))) {
				dragHeading = left;
			} else if (water(BlockPos.containing(getX() + right.x * 3.0, getY() + 0.5, getZ() + right.z * 3.0))) {
				dragHeading = right;
			} else {
				dragHeading = dragHeading.reverse();
			}
		}
		double down = water(blockPosition().below(2)) ? -0.06 : 0.0;
		setDeltaMovement(dragHeading.x * 0.32, down, dragHeading.z * 0.32);
		float yaw = (float) (Mth.atan2(dragHeading.z, dragHeading.x) * Mth.RAD_TO_DEG) - 90.0f;
		setYRot(Mth.approachDegrees(getYRot(), yaw, 12.0f));
		yBodyRot = getYRot();
		yHeadRot = getYRot();
		// The victim, held in its jaws.
		Vec3 hold = mouth().subtract(victim.position().add(0.0, victim.getBbHeight() * 0.4, 0.0));
		victim.setDeltaMovement(hold.scale(0.5).add(getDeltaMovement()));
		victim.needsSync = true;
		victim.fallDistance = 0.0;
		if (victim.isEyeInFluid(FluidTags.WATER)) {
			// The air goes out of them five times as fast (stopping short of the drowning tick, which vanilla deals).
			victim.setAirSupply(Math.max(victim.getAirSupply() - 4, -19));
		}
		if (stateTicks % 15 == 7) {
			victim.hurtServer(level, damageSources().mobAttack(this), 2.5f);
			playSound(GiantCatfishes.FLOP, 1.4f, 0.7f);
		}
		Vec3 m = mouth();
		level.sendParticles(ParticleTypes.BUBBLE, m.x, m.y, m.z, 4, 0.4, 0.3, 0.4, 0.05);
	}

	/** Lets go of whoever it's dragging, with a shove up and away. */
	void releaseDrag() {
		LivingEntity victim = dragged;
		dragged = null;
		dragCooldown = 160;
		if (state() == DRAGGING) {
			setState(IDLE);
		}
		if (victim != null && victim.isAlive()) {
			Vec3 away = victim.position().subtract(position()).multiply(1.0, 0.0, 1.0);
			away = away.lengthSqr() < 1.0E-4 ? forward() : away.normalize();
			victim.setDeltaMovement(away.x * 0.6, 0.5, away.z * 0.6);
			victim.needsSync = true;
			playSound(GiantCatfishes.SPLASH, 1.5f, 1.1f);
		}
	}

	private boolean water(BlockPos pos) {
		return level().getFluidState(pos).is(FluidTags.WATER);
	}

	// --- Boats ---------------------------------------------------------------------------------------------------

	/** Overturns a boat: everyone in it in the water, the boat shoved away from {@code from}. */
	static void capsize(AbstractBoat boat, Vec3 from, double strength) {
		List<Entity> riders = new ArrayList<>(boat.getPassengers());
		boat.ejectPassengers();
		Vec3 away = boat.position().subtract(from).multiply(1.0, 0.0, 1.0);
		away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
		boat.push(away.x * strength, 0.35, away.z * strength);
		boat.needsSync = true;
		for (Entity rider : riders) {
			rider.push(away.x * strength * 0.6, 0.3, away.z * strength * 0.6);
			rider.needsSync = true;
		}
		boat.level().playSound(null, boat.getX(), boat.getY(), boat.getZ(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.HOSTILE,
				1.0f, 0.8f);
	}

	/** Overturns every boat within {@code radius}. */
	void capsizeAround(double radius, double strength) {
		for (AbstractBoat boat : level().getEntitiesOfClass(AbstractBoat.class, getBoundingBox().inflate(radius))) {
			capsize(boat, position(), strength);
		}
	}

	// --- Damage --------------------------------------------------------------------------------------------------

	/** Whether {@code source} is a Harpoon Gun's spear. */
	private static boolean harpoon(DamageSource source) {
		return source.is(ModDamageTypes.BULLET) && source.getEntity() instanceof LivingEntity shooter
				&& (shooter.getMainHandItem().getItem() instanceof HarpoonGunItem || shooter.getOffhandItem().getItem() instanceof HarpoonGunItem);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean harpooned = harpoon(source);
		if (harpooned) {
			damage *= HARPOON_FACTOR;
		}
		float before = getHealth();
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && dragged != null) {
			dragDamage += before - getHealth();
		}
		if (hurt && harpooned && isAlive() && source.getEntity() instanceof LivingEntity shooter) {
			// The line goes taut: it lets go of whoever it had, and is hauled thrashing toward the shooter.
			releaseDrag();
			setState(STUNNED);
			getNavigation().stop();
			Vec3 pull = shooter.position().subtract(position());
			if (pull.lengthSqr() > 1.0E-4) {
				pull = pull.normalize();
				setDeltaMovement(getDeltaMovement().add(pull.x * 0.9, Math.max(0.0, pull.y) * 0.5 + 0.1, pull.z * 0.9));
				needsSync = true;
			}
			playSound(GiantCatfishes.HARPOONED, 2.0f, 1.0f);
			level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.8, getZ(), 30, 1.0, 0.2, 1.0, 0.2);
		}
		return hurt;
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit) {
			playSound(GiantCatfishes.BITE, 1.6f, 0.8f + random.nextFloat() * 0.2f);
		}
		return hit;
	}

	/** Killed by someone, it gives up its trophy, weighed in for their records. */
	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		Player killer = source.getEntity() instanceof Player player ? player : getLastHurtByPlayer();
		if (killer == null) {
			return;
		}
		FishSize size = size();
		float weight = weight();
		long day = TrophyFish.day(level);
		ItemStack trophy = TrophyFish.make(new ItemStack(GiantCatfishes.GIANT_CATFISH),
				new TrophyFish(weight, size != null ? size.avg() : TYPICAL_WEIGHT, killer.getScoreboardName(), day));
		spawnAtLocation(level, trophy);
		Component described = TrophyFish.describe(trophy, weight);
		killer.sendSystemMessage(Component.translatable("chat.tomyen.giant_catfish.landed", described).withStyle(TrophyFish.COLOR));
		float previous = FishingRecords.submit(killer, GiantCatfishes.SPECIES_ID, weight, day);
		if (previous > 0.0f) {
			killer.sendSystemMessage(Component.translatable("chat.tomyen.fishing.personal_best", TrophyFish.speciesName(trophy),
					FishSize.format(weight), FishSize.format(previous)).withStyle(ChatFormatting.AQUA));
		}
	}

	// --- Ticking -------------------------------------------------------------------------------------------------

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			clientTick();
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		stateTicks++;
		if (dragCooldown > 0) {
			dragCooldown--;
		}
		int state = state();
		if (state == SURFACING) {
			// Breaching: up out of the water, then down on whoever hooked it.
			if (stateTicks < 14) {
				setDeltaMovement(getDeltaMovement().multiply(0.6, 0.0, 0.6).add(0.0, 0.16, 0.0));
			}
			if (stateTicks == 14) {
				level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 1.0, getZ(), 50, 1.5, 0.3, 1.5, 0.3);
				playSound(GiantCatfishes.SPLASH, 2.5f, 0.7f);
			}
			if (stateTicks >= SURFACE_TICKS) {
				setState(IDLE);
			}
		} else if (state == STUNNED && stateTicks >= STUN_TICKS) {
			setState(IDLE);
		} else if (state == DRAGGING) {
			tickDrag(level);
		} else if (dragged != null) {
			releaseDrag();
		}
		beached(level);
		if (tickCount % 10 == 0) {
			updateBossBar(level);
		}
		bossEvent.setProgress(getHealth() / getMaxHealth());
	}

	/** Out of the water: flops toward it, and slowly dries out. */
	private void beached(ServerLevel level) {
		if (isInWater()) {
			dryTicks = 0;
			return;
		}
		dryTicks++;
		if (dryTicks > DRY_GRACE && dryTicks % 40 == 0) {
			hurtServer(level, damageSources().dryOut(), 2.0f);
		}
		if (onGround() && --flopDelay <= 0) {
			flopDelay = 12 + random.nextInt(14);
			Vec3 toward = nearestWater();
			double dx;
			double dz;
			if (toward != null) {
				Vec3 d = toward.subtract(position()).multiply(1.0, 0.0, 1.0).normalize();
				dx = d.x * 0.32;
				dz = d.z * 0.32;
			} else {
				dx = (random.nextFloat() * 2.0f - 1.0f) * 0.25f;
				dz = (random.nextFloat() * 2.0f - 1.0f) * 0.25f;
			}
			setDeltaMovement(dx, 0.42, dz);
			setYRot(getYRot() + (random.nextFloat() - 0.5f) * 90.0f);
			yBodyRot = getYRot();
			setOnGround(false);
			needsSync = true;
			playSound(GiantCatfishes.FLOP, 1.6f, 0.6f + random.nextFloat() * 0.2f);
		}
	}

	private @Nullable Vec3 nearestWater() {
		BlockPos at = blockPosition();
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos probe : BlockPos.betweenClosed(at.offset(-8, -2, -8), at.offset(8, 1, 8))) {
			if (water(probe)) {
				double distance = probe.distSqr(at);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = probe.immutable();
				}
			}
		}
		return best == null ? null : Vec3.atCenterOf(best);
	}

	/** The health bar, for everyone near while it's fighting. */
	private void updateBossBar(ServerLevel level) {
		boolean fighting = getTarget() != null || state() != IDLE;
		Set<ServerPlayer> near = new HashSet<>();
		if (fighting && isAlive()) {
			near.addAll(level.getPlayers(player -> player.isAlive() && player.distanceToSqr(this) < BOSS_RANGE * BOSS_RANGE));
		}
		for (ServerPlayer player : List.copyOf(bossEvent.getPlayers())) {
			if (!near.contains(player)) {
				bossEvent.removePlayer(player);
			}
		}
		near.forEach(bossEvent::addPlayer);
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		bossEvent.removePlayer(player);
	}

	@Override
	public void remove(RemovalReason reason) {
		bossEvent.removeAllPlayers();
		super.remove(reason);
	}

	@Override
	protected void tickDeath() {
		super.tickDeath();
		bossEvent.setProgress(0.0f);
	}

	/** Swims freely in three dimensions; on land, falls and flops as anything does. */
	@Override
	protected void travelInWater(Vec3 input, double baseGravity, boolean isFalling, double oldY) {
		moveRelative(0.1f, input);
		move(MoverType.SELF, getDeltaMovement());
		setDeltaMovement(getDeltaMovement().scale(0.9));
		if (getNavigation().isDone() && getTarget() == null && state() == IDLE) {
			// Settles toward the bottom when it's got nothing to do.
			setDeltaMovement(getDeltaMovement().add(0.0, -0.005, 0.0));
		}
	}

	// --- Client animation ----------------------------------------------------------------------------------------

	private void clientTick() {
		swimO = swim;
		tailO = tail;
		mouthO = mouth;
		roarO = roar;
		thrashO = thrash;
		pitchO = pitch;
		int state = state();
		if (state != clientState) {
			clientState = state;
			clientStateTicks = 0;
		} else {
			clientStateTicks++;
		}
		double dx = getX() - xo;
		double dy = getY() - yo;
		double dz = getZ() - zo;
		double speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
		float wantSwim = isInWater() ? (float) Mth.clamp(speed / 0.3, 0.0, 1.0) : 0.0f;
		if (state == CHARGING || state == DRAGGING || state == SURFACING) {
			wantSwim = 1.0f;
		}
		swim += (wantSwim - swim) * 0.15f;
		tail += 0.12f + 0.45f * swim + (state == STUNNED ? 0.3f : 0.0f);
		float wantPitch = isInWater() && speed > 0.03 ? (float) Mth.clamp(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG, -30.0, 30.0)
				: 0.0f;
		if (state == SURFACING && clientStateTicks < 14) {
			wantPitch = 40.0f;
		}
		pitch += (wantPitch - pitch) * 0.15f;
		float wantMouth = switch (state) {
			case SURFACING -> 1.0f;
			case WINDUP -> 0.7f;
			case CHARGING -> 0.45f;
			case DRAGGING -> 0.3f;
			case BITING -> Mth.sin(Mth.PI * Mth.clamp(clientStateTicks / (float) BITE_TICKS, 0.0f, 1.0f));
			case STUNNED -> 0.5f;
			default -> isInWater() ? 0.06f + 0.06f * Mth.sin(tickCount * 0.08f) : 0.4f + 0.3f * Mth.sin(tickCount * 0.3f);
		};
		mouth += (wantMouth - mouth) * (state == BITING ? 0.7f : 0.25f);
		float wantRoar = state == SURFACING || state == WINDUP ? 1.0f : 0.0f;
		roar += (wantRoar - roar) * 0.2f;
		float wantThrash = state == DRAGGING || state == STUNNED ? 1.0f : 0.0f;
		thrash += (wantThrash - thrash) * 0.2f;
		if (isInWater() && swim > 0.4f && random.nextInt(3) == 0) {
			Vec3 tip = tailTip();
			level().addParticle(ParticleTypes.BUBBLE, tip.x + random.nextGaussian() * 0.4, tip.y, tip.z + random.nextGaussian() * 0.4,
					0.0, 0.05, 0.0);
		}
	}

	public float swim(float partialTicks) {
		return Mth.lerp(partialTicks, swimO, swim);
	}

	public float tail(float partialTicks) {
		return Mth.lerp(partialTicks, tailO, tail);
	}

	public float mouth(float partialTicks) {
		return Mth.lerp(partialTicks, mouthO, mouth);
	}

	public float roar(float partialTicks) {
		return Mth.lerp(partialTicks, roarO, roar);
	}

	public float thrash(float partialTicks) {
		return Mth.lerp(partialTicks, thrashO, thrash);
	}

	public float pitch(float partialTicks) {
		return Mth.lerp(partialTicks, pitchO, pitch);
	}

	/** How far through a tail slap, 0 to 1 (0 when not slapping). */
	public float slap(float partialTicks) {
		return clientState == SLAPPING ? Mth.clamp((clientStateTicks + partialTicks) / SLAP_TICKS, 0.0f, 1.0f) : 0.0f;
	}

	// --- Sounds and saving ---------------------------------------------------------------------------------------

	@Override
	protected SoundEvent getAmbientSound() {
		return GiantCatfishes.AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return GiantCatfishes.HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return GiantCatfishes.DEATH;
	}

	@Override
	protected SoundEvent getSwimSound() {
		return GiantCatfishes.SWIM;
	}

	@Override
	protected SoundEvent getSwimSplashSound() {
		return GiantCatfishes.SPLASH;
	}

	@Override
	protected float getSoundVolume() {
		return 2.0f;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putFloat("weight_lb", weight());
		output.putBoolean("hooked", hooked);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		setWeight(input.getFloatOr("weight_lb", TYPICAL_WEIGHT));
		hooked = input.getBooleanOr("hooked", false);
	}

	/** Swims to where its path leads, turning as it goes, as a guardian does. */
	private static class CatfishMoveControl extends MoveControl<GiantCatfish> {
		CatfishMoveControl(GiantCatfish fish) {
			super(fish);
		}

		@Override
		public void tick() {
			if (!mob.isInWater() || operation != Operation.MOVE_TO || mob.getNavigation().isDone()) {
				mob.setSpeed(0.0f);
				return;
			}
			operation = Operation.WAIT;
			double dx = wantedX - mob.getX();
			double dy = wantedY - mob.getY();
			double dz = wantedZ - mob.getZ();
			double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (distance < 1.0E-4) {
				mob.setSpeed(0.0f);
				return;
			}
			float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
			// A big fish turns slowly.
			mob.setYRot(rotlerp(mob.getYRot(), yaw, 12.0f));
			mob.yBodyRot = mob.getYRot();
			float speed = Mth.lerp(0.125f, mob.getSpeed(), (float) (speedModifier * mob.getAttributeValue(Attributes.MOVEMENT_SPEED)));
			mob.setSpeed(speed);
			mob.setDeltaMovement(mob.getDeltaMovement().add(0.0, speed * dy / distance * 0.1, 0.0));
		}
	}
}
