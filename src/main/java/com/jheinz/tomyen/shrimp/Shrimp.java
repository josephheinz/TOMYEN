package com.jheinz.tomyen.shrimp;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Shrimp with a Gun: a mantis shrimp the size of a man, standing up on its curled tail and four spindly legs, a
 * salvaged pistol gripped in its raptorial claws. It swims fast and breathes anywhere; at night it wades up onto the
 * beach looking for people, and by day it drifts back into the sea.
 *
 * <p>It fights at range (see {@link ShrimpAttackGoal}): it backs off to a comfortable distance, draws a bead, empties
 * its pistol in a quick string of shots, then has to stop and thumb four rounds back in through the gate and rack the
 * slide, a few seconds in which it can't shoot. Anyone who gets close in that time, or at any time, meets the club on
 * its claw: the fastest punch in the sea.
 *
 * <p>The client animates its aim, each shot's kick and the reload from {@link #attackState()} and a shot counter that
 * goes up with every round fired.
 */
public class Shrimp extends Monster {
	public static final int IDLE = 0;
	/** Gun up, drawing a bead. */
	public static final int AIMING = 1;
	/** Firing its string of shots. */
	public static final int FIRING = 2;
	/** Thumbing rounds in and racking the slide; it can't shoot. */
	public static final int RELOADING = 3;

	/** How long a reload takes: four rounds and the slide. The client's reload animation runs over the same time. */
	public static final int RELOAD_TICKS = 70;
	public static final int ROUNDS = 4;
	/** Natural spawns need fewer than this many shrimp nearby already. */
	private static final int CROWD = 3;
	private static final double CROWD_RADIUS = 32.0;
	/** How far a beach spawn can be from the sea. */
	private static final int SHORE_REACH = 6;

	private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(Shrimp.class, EntityDataSerializers.BYTE);
	/** Goes up by one with every shot, so clients can kick the gun back. */
	private static final EntityDataAccessor<Integer> DATA_SHOTS = SynchedEntityData.defineId(Shrimp.class, EntityDataSerializers.INT);

	// Client side: how far the gun is up (0 to 1), the kick of the last shot (1, decaying), and how far through a reload.
	private float aim;
	private float aimO;
	private float recoil;
	private float recoilO;
	private int lastShots;
	private boolean shotsSeen;
	private int reloadTime;
	private int lastState;

	public Shrimp(EntityType<? extends Shrimp> type, Level level) {
		super(type, level);
		xpReward = 12;
		moveControl = new ShrimpMoveControl(this);
		setPathfindingMalus(PathType.WATER, 0.0f);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 34.0)
				.add(Attributes.ARMOR, 5.0)
				.add(Attributes.ATTACK_DAMAGE, 6.0)
				.add(Attributes.ATTACK_KNOCKBACK, 1.2)
				.add(Attributes.MOVEMENT_SPEED, 0.25)
				.add(Attributes.FOLLOW_RANGE, 32.0)
				.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_STATE, (byte) IDLE);
		builder.define(DATA_SHOTS, 0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(1, new ShrimpAttackGoal(this));
		goalSelector.addGoal(3, new GoToWaterGoal(this));
		goalSelector.addGoal(4, new GoToBeachGoal(this));
		goalSelector.addGoal(5, new RandomSwimmingGoal(this, 1.0, 40) {
			@Override
			public boolean canUse() {
				return mob.isInWater() && super.canUse();
			}
		});
		goalSelector.addGoal(6, new RandomStrollGoal(this, 0.8) {
			@Override
			public boolean canUse() {
				return !mob.isInWater() && super.canUse();
			}
		});
		goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		return new AmphibiousPathNavigation(this, level);
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	/** It swims against the current rather than being washed about by it. */
	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	/**
	 * In the sea near the surface, or on sand or gravel within {@value #SHORE_REACH} blocks of it, out of torchlight;
	 * always at night, one try in five by day; and only where there aren't already {@value #CROWD} of them about.
	 * Spawners and eggs skip all of that.
	 */
	public static boolean checkSpawnRules(EntityType<Shrimp> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos,
			RandomSource random) {
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			return false;
		}
		if (EntitySpawnReason.isSpawner(reason)) {
			return true;
		}
		if (level.getBrightness(LightLayer.BLOCK, pos) > 0 || !level.getLevel().isDarkOutside() && random.nextInt(5) != 0) {
			return false;
		}
		boolean wet = level.getFluidState(pos).is(FluidTags.WATER);
		boolean placed = wet ? pos.getY() >= level.getSeaLevel() - 8 && level.getFluidState(pos.above()).is(FluidTags.WATER)
				: onShore(level, pos);
		return placed && level.getEntitiesOfClass(Shrimp.class, new AABB(pos).inflate(CROWD_RADIUS)).size() < CROWD;
	}

	/** Standing room on sand or gravel open to the sky, with the sea close by. */
	private static boolean onShore(ServerLevelAccessor level, BlockPos pos) {
		BlockPos below = pos.below();
		BlockState ground = level.getBlockState(below);
		if (!(ground.is(BlockTags.SAND) || ground.is(Blocks.GRAVEL)) || !level.canSeeSky(pos)
				|| !NaturalSpawner.isValidEmptySpawnBlock(level, pos, level.getBlockState(pos), level.getFluidState(pos), Shrimps.SHRIMP)
				|| !NaturalSpawner.isValidEmptySpawnBlock(level, pos.above(), level.getBlockState(pos.above()),
						level.getFluidState(pos.above()), Shrimps.SHRIMP)) {
			return false;
		}
		BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			for (int step = 1; step <= SHORE_REACH; step++) {
				probe.setWithOffset(below, direction.getStepX() * step, 0, direction.getStepZ() * step);
				if (level.getFluidState(probe).is(FluidTags.WATER) || level.getFluidState(probe.move(Direction.UP)).is(FluidTags.WATER)) {
					return true;
				}
			}
		}
		return false;
	}

	/** The light test (which would keep any monster off a sunny beach) is in {@link #checkSpawnRules} instead. */
	@Override
	public boolean checkSpawnRules(LevelAccessor level, EntitySpawnReason reason) {
		return true;
	}

	@Override
	public boolean checkSpawnObstruction(LevelReader level) {
		return level.isUnobstructed(this);
	}

	// Only stays put while it's after someone (or named, which Mob handles).
	@Override
	public boolean removeWhenFarAway(double distanceSq) {
		return getTarget() == null && super.removeWhenFarAway(distanceSq);
	}

	public int attackState() {
		return entityData.get(DATA_STATE);
	}

	void setAttackState(int state) {
		entityData.set(DATA_STATE, (byte) state);
	}

	/** Server side: one round fired, for the clients' recoil. */
	void countShot() {
		entityData.set(DATA_SHOTS, entityData.get(DATA_SHOTS) + 1);
	}

	/** Where its pistol's muzzle is: out in front of its chest, a little to its right, where the model holds the gun. */
	public Vec3 muzzle() {
		float yaw = yBodyRot * Mth.DEG_TO_RAD;
		Vec3 forward = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
		Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
		return position().add(forward.scale(1.0)).add(right.scale(0.18)).add(0.0, 1.22, 0.0);
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		playSound(Shrimps.SNAP, 1.4f, 0.9f + random.nextFloat() * 0.2f);
		if (hit) {
			Vec3 at = target.getBoundingBox().getCenter();
			level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.3);
			// The strike is fast enough to boil the water in front of it.
			if (isInWater()) {
				level.sendParticles(ParticleTypes.BUBBLE, at.x, at.y, at.z, 16, 0.3, 0.3, 0.3, 0.2);
			}
		}
		return hit;
	}

	/** Swims fast and freely whenever it's going somewhere in the water; otherwise it settles and walks the bottom. */
	@Override
	protected void travelInWater(Vec3 input, double baseGravity, boolean isFalling, double oldY) {
		if (isInWater() && !getNavigation().isDone()) {
			moveRelative(0.02f, input);
			move(MoverType.SELF, getDeltaMovement());
			setDeltaMovement(getDeltaMovement().scale(0.9));
		} else {
			super.travelInWater(input, baseGravity, isFalling, oldY);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			aimO = aim;
			recoilO = recoil;
			int state = attackState();
			boolean up = state == AIMING || state == FIRING;
			aim = Mth.approach(aim, up ? 1.0f : 0.0f, up ? 0.2f : 0.08f);
			int shots = entityData.get(DATA_SHOTS);
			if (!shotsSeen) {
				// Only shots fired after it comes into view kick the gun.
				lastShots = shots;
				shotsSeen = true;
			}
			if (shots != lastShots) {
				recoil = 1.0f;
				lastShots = shots;
			} else {
				recoil = Math.max(0.0f, recoil - 0.25f);
			}
			reloadTime = state == RELOADING ? (lastState == RELOADING ? reloadTime + 1 : 0) : 0;
			lastState = state;
		}
	}

	public float aim(float partialTicks) {
		return Mth.lerp(partialTicks, aimO, aim);
	}

	public float recoil(float partialTicks) {
		return Mth.lerp(partialTicks, recoilO, recoil);
	}

	/** How far through a reload, 0 to 1 (0 when not reloading). */
	public float reloadProgress(float partialTicks) {
		return attackState() == RELOADING ? Mth.clamp((reloadTime + partialTicks) / RELOAD_TICKS, 0.0f, 1.0f) : 0.0f;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return Shrimps.AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return Shrimps.HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return Shrimps.DEATH;
	}

	@Override
	protected SoundEvent getSwimSound() {
		return Shrimps.SWIM;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		playSound(Shrimps.STEP, 0.5f, 1.0f);
	}

	/** Swims to where its path leads, in three dimensions, at its land speed; on land it walks as anything does. */
	private static class ShrimpMoveControl extends MoveControl<Shrimp> {
		ShrimpMoveControl(Shrimp shrimp) {
			super(shrimp);
		}

		@Override
		public void tick() {
			if (!mob.isInWater()) {
				super.tick();
				return;
			}
			if (operation != Operation.MOVE_TO || mob.getNavigation().isDone()) {
				mob.setSpeed(0.0f);
				// Treading water at the surface once it's got someone in sight, rather than sinking out of the fight.
				LivingEntity target = mob.getTarget();
				if (target != null && target.getY() > mob.getY()) {
					mob.setDeltaMovement(mob.getDeltaMovement().add(0.0, 0.01, 0.0));
				}
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
			mob.setYRot(rotlerp(mob.getYRot(), yaw, 90.0f));
			mob.yBodyRot = mob.getYRot();
			float speed = Mth.lerp(0.125f, mob.getSpeed(), (float) (speedModifier * mob.getAttributeValue(Attributes.MOVEMENT_SPEED)));
			mob.setSpeed(speed);
			mob.setDeltaMovement(mob.getDeltaMovement().add(speed * dx / distance * 0.05, speed * dy / distance * 0.1,
					speed * dz / distance * 0.05));
		}
	}

	/** At night, idling in the shallows: it wades up the beach. */
	private static class GoToBeachGoal extends MoveToBlockGoal {
		private final Shrimp shrimp;

		GoToBeachGoal(Shrimp shrimp) {
			super(shrimp, 1.0, 10, 3);
			this.shrimp = shrimp;
		}

		@Override
		public boolean canUse() {
			return shrimp.getTarget() == null && shrimp.level().isDarkOutside() && shrimp.isInWater()
					&& shrimp.getY() >= shrimp.level().getSeaLevel() - 4 && super.canUse();
		}

		@Override
		protected boolean isValidTarget(LevelReader level, BlockPos pos) {
			BlockPos above = pos.above();
			return level.isEmptyBlock(above) && level.isEmptyBlock(above.above()) && level.getBlockState(pos).is(BlockTags.SAND);
		}
	}

	/** By day, ashore with nothing to shoot: back into the sea. */
	private static class GoToWaterGoal extends Goal {
		private final Shrimp shrimp;
		private Vec3 wanted = Vec3.ZERO;

		GoToWaterGoal(Shrimp shrimp) {
			this.shrimp = shrimp;
			setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			if (shrimp.getTarget() != null || shrimp.isInWater() || shrimp.level().isDarkOutside() || shrimp.getRandom().nextInt(20) != 0) {
				return false;
			}
			BlockPos at = shrimp.blockPosition();
			RandomSource random = shrimp.getRandom();
			for (int i = 0; i < 12; i++) {
				BlockPos pos = at.offset(random.nextInt(24) - 12, 2 - random.nextInt(8), random.nextInt(24) - 12);
				if (shrimp.level().getFluidState(pos).is(FluidTags.WATER)) {
					wanted = Vec3.atBottomCenterOf(pos);
					return true;
				}
			}
			return false;
		}

		@Override
		public boolean canContinueToUse() {
			return !shrimp.getNavigation().isDone() && shrimp.getTarget() == null;
		}

		@Override
		public void start() {
			shrimp.getNavigation().moveTo(wanted.x, wanted.y, wanted.z, 1.0);
		}
	}

	/** The target, if it's still worth shooting at. */
	@Nullable LivingEntity liveTarget() {
		LivingEntity target = getTarget();
		if (target == null || !target.isAlive() || !canAttack(target)) {
			return null;
		}
		return target instanceof Player player && (player.isCreative() || player.isSpectator()) ? null : target;
	}
}
