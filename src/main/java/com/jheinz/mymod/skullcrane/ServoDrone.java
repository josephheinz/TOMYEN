package com.jheinz.mymod.skullcrane;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A tame little servo skull built from skullcrane parts, deployed from a {@link ServoDroneItem}. It hovers at its
 * owner's shoulder and works for them, in this order:
 * <ul>
 * <li>Fights: whatever hurts its owner or its owner hits, and any monster closing in on them (not creepers),
 * flying at it and clawing for {@value #DAMAGE} damage, as long as it stays within {@value #LEASH} blocks of the
 * owner.
 * <li>Fetches: the nearest dropped item within {@value #FETCH_RANGE} blocks of the owner (not ones they just threw
 * away), one stack at a time, which it carries back in its claws and hands over (dropping it at their feet if
 * their inventory is full).
 * <li>Otherwise it keeps near them, and catches up by teleporting if it falls {@value #TELEPORT} blocks behind.
 * </ul>
 * The owner can't hurt it. Using it with an empty hand (or the drone item) picks it back up as an item, its health
 * kept as the item's durability. It neither drowns nor takes fall damage, and never despawns.
 */
public class ServoDrone extends PathfinderMob {
	public static final float MAX_HEALTH = 20.0f;
	private static final float DAMAGE = 3.0f;
	private static final double LEASH = 14.0;
	private static final double FETCH_RANGE = 10.0;
	private static final double TELEPORT = 24.0;
	private static final int ATTACK_COOLDOWN = 20;

	public static final int FOLLOWING = 0;
	public static final int FETCHING = 1;
	public static final int FIGHTING = 2;

	private static final EntityDataAccessor<Byte> DATA_MODE = SynchedEntityData.defineId(ServoDrone.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<ItemStack> DATA_CARRIED = SynchedEntityData.defineId(ServoDrone.class, EntityDataSerializers.ITEM_STACK);
	/** What it's reaching for (target or item) by entity id, or -1. */
	private static final EntityDataAccessor<Integer> DATA_REACH = SynchedEntityData.defineId(ServoDrone.class, EntityDataSerializers.INT);

	private @Nullable UUID owner;
	// Client side, as the skullcrane's: arms flared, claws shut, jaw chattering.
	private float flare;
	private float flareO;
	private float clamp;
	private float clampO;
	private float chatter;
	private float chatterO;

	public ServoDrone(EntityType<? extends ServoDrone> type, Level level) {
		super(type, level);
		moveControl = new FlyingMoveControl<>(this, 30, true);
		setNoGravity(true);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, MAX_HEALTH)
				.add(Attributes.ATTACK_DAMAGE, DAMAGE)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.FLYING_SPEED, 0.2)
				.add(Attributes.FOLLOW_RANGE, 24.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_MODE, (byte) FOLLOWING);
		builder.define(DATA_CARRIED, ItemStack.EMPTY);
		builder.define(DATA_REACH, -1);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(1, new WorkGoal());
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
		navigation.setCanOpenDoors(false);
		navigation.setCanFloat(true);
		return navigation;
	}

	// ------------------------------------------------------------------------------------------------ state

	void setOwner(Player player) {
		owner = player.getUUID();
	}

	public @Nullable Player owner() {
		return owner == null ? null : level().getPlayerByUUID(owner);
	}

	boolean isOwnedBy(Player player) {
		return player.getUUID().equals(owner);
	}

	public int mode() {
		return entityData.get(DATA_MODE);
	}

	public ItemStack carried() {
		return entityData.get(DATA_CARRIED);
	}

	private void setCarried(ItemStack stack) {
		entityData.set(DATA_CARRIED, stack);
	}

	/** Client side: what it's reaching for, if anything. */
	public @Nullable Entity reaching() {
		int id = entityData.get(DATA_REACH);
		return id < 0 ? null : level().getEntity(id);
	}

	// ------------------------------------------------------------------------------------------------ flying

	@Override
	public void travel(Vec3 input) {
		if (isAlive()) {
			travelFlying(input, getSpeed());
		} else {
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
	public boolean canBreatheUnderwater() {
		return true;
	}

	/** Straight at a point while nothing's in the way, round obstacles otherwise. */
	private void flyTo(Vec3 point, double speed) {
		boolean clear = level().clip(new ClipContext(position(), point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType()
				== HitResult.Type.MISS;
		if (clear) {
			getNavigation().stop();
			Vec3 to = point.subtract(position());
			double distance = to.length();
			Vec3 wanted = to.scale(Math.min(speed, distance * 0.3) / Math.max(distance, 1.0e-4));
			setDeltaMovement(getDeltaMovement().lerp(wanted, 0.25));
			if (distance > 0.3) {
				float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0f;
				setYRot(yaw);
				yBodyRot = yaw;
			}
		} else if (getNavigation().isDone() || tickCount % 10 == 0) {
			getNavigation().moveTo(point.x, point.y, point.z, 1.4);
		}
	}

	// ------------------------------------------------------------------------------------------------ ticking

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
		flare = Mth.approach(flare, mode == FIGHTING || mode == FETCHING && carried().isEmpty() ? 1.0f : 0.0f, 0.1f);
		clamp = Mth.approach(clamp, carried().isEmpty() ? 0.0f : 1.0f, 0.2f);
		chatter = Mth.approach(chatter, mode == FIGHTING && isAlive() ? 1.0f : 0.0f, 0.15f);
		if (isAlive() && random.nextInt(25) == 0) {
			level().addParticle(ParticleTypes.ELECTRIC_SPARK, getX() + random.nextGaussian() * 0.08, getY() + 0.03,
					getZ() + random.nextGaussian() * 0.08, 0.0, -0.05, 0.0);
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

	/** Its whole job, run as one goal: fight, fetch, or keep up with its owner. */
	private class WorkGoal extends Goal {
		private int attackCooldown;
		private int searchTicks;
		private @Nullable ItemEntity fetching;

		WorkGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return true;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			ServerLevel level = (ServerLevel) level();
			attackCooldown = Math.max(0, attackCooldown - 1);
			Player player = owner();
			if (player == null || player.isSpectator()) {
				// Nobody to work for: hovers where it is.
				setTarget(null);
				setDeltaMovement(getDeltaMovement().scale(0.8).add(0.0, Mth.sin(tickCount * 0.1f) * 0.004, 0.0));
				setState(FOLLOWING, null);
				return;
			}
			if (distanceTo(player) > TELEPORT) {
				Vec3 at = player.position().add(0.0, player.getBbHeight() + 0.8, 0.0);
				teleportTo(at.x, at.y, at.z);
				getNavigation().stop();
				fetching = null;
			}
			if (--searchTicks <= 0) {
				searchTicks = 10;
				LivingEntity target = getTarget();
				if (target == null || !target.isAlive() || target.distanceTo(player) > LEASH) {
					setTarget(findTarget(level, player));
				}
				if (fetching == null || !fetching.isAlive()) {
					fetching = carried().isEmpty() ? findItem(level, player) : null;
				}
			}
			LivingEntity target = getTarget();
			if (target != null && target.isAlive() && target.distanceTo(player) <= LEASH) {
				fight(level, target);
			} else if (!carried().isEmpty()) {
				deliver(level, player);
			} else if (fetching != null && fetching.isAlive()) {
				fetch(level, fetching);
			} else {
				fetching = null;
				follow(player);
			}
		}

		private void setState(int mode, @Nullable Entity reaching) {
			if (mode() != mode) {
				entityData.set(DATA_MODE, (byte) mode);
			}
			int id = reaching == null ? -1 : reaching.getId();
			if (entityData.get(DATA_REACH) != id) {
				entityData.set(DATA_REACH, id);
			}
		}

		/** Whatever hurt its owner or its owner hurt, else the nearest monster coming at them. */
		private @Nullable LivingEntity findTarget(ServerLevel level, Player player) {
			for (LivingEntity candidate : new LivingEntity[] {player.getLastHurtByMob(), player.getLastHurtMob()}) {
				if (candidate != null && candidate.isAlive() && fightable(candidate) && candidate.distanceTo(player) <= LEASH) {
					return candidate;
				}
			}
			LivingEntity nearest = null;
			double best = Double.MAX_VALUE;
			for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(LEASH * 0.7), mob -> mob instanceof Enemy
					&& mob.isAlive() && fightable(mob) && (mob.getTarget() == player || mob.distanceTo(player) < 6.0))) {
				double distance = mob.distanceToSqr(player);
				if (distance < best) {
					best = distance;
					nearest = mob;
				}
			}
			return nearest;
		}

		private boolean fightable(LivingEntity entity) {
			return !(entity instanceof Player) && !(entity instanceof ServoDrone) && !(entity instanceof Creeper) && entity != ServoDrone.this;
		}

		private void fight(ServerLevel level, LivingEntity target) {
			setState(FIGHTING, target);
			getLookControl().setLookAt(target, 30.0f, 30.0f);
			flyTo(target.position().add(0.0, target.getBbHeight() * 0.7, 0.0), 0.55);
			if (attackCooldown == 0 && getBoundingBox().inflate(0.7).intersects(target.getBoundingBox())) {
				swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				if (doHurtTarget(level, target)) {
					playSound(Skullcranes.SCRATCH, 0.8f, 1.3f + random.nextFloat() * 0.2f);
				}
				attackCooldown = ATTACK_COOLDOWN;
				// Darts back out.
				Vec3 away = position().subtract(target.position()).horizontal();
				away = away.lengthSqr() < 1.0e-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
				setDeltaMovement(away.scale(0.4).add(0.0, 0.3, 0.0));
			}
		}

		/** The nearest item lying near the owner (not one they've just thrown away), if any. */
		private @Nullable ItemEntity findItem(ServerLevel level, Player player) {
			ItemEntity nearest = null;
			double best = Double.MAX_VALUE;
			for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(FETCH_RANGE), item -> item.isAlive()
					&& !item.hasPickUpDelay() && !(item.getOwner() == player && item.getAge() < 200) && item.distanceTo(player) > 1.5)) {
				double distance = item.distanceToSqr(ServoDrone.this);
				if (distance < best) {
					best = distance;
					nearest = item;
				}
			}
			return nearest;
		}

		private void fetch(ServerLevel level, ItemEntity item) {
			setState(FETCHING, item);
			getLookControl().setLookAt(item);
			flyTo(item.position().add(0.0, 0.5, 0.0), 0.5);
			if (distanceToSqr(item.position().add(0.0, 0.5, 0.0)) < 1.0) {
				setCarried(item.getItem().copy());
				item.discard();
				fetching = null;
				level.playSound(null, getX(), getY(), getZ(), Skullcranes.CLAMP, SoundSource.NEUTRAL, 0.6f, 1.6f);
			}
		}

		private void deliver(ServerLevel level, Player player) {
			setState(FETCHING, player);
			getLookControl().setLookAt(player);
			Vec3 hand = player.position().add(0.0, player.getBbHeight() * 0.6, 0.0);
			flyTo(hand, 0.5);
			if (distanceToSqr(hand) < 2.5) {
				ItemStack stack = carried().copy();
				setCarried(ItemStack.EMPTY);
				player.getInventory().add(stack);
				if (!stack.isEmpty()) {
					// A full inventory: left at their feet.
					ItemEntity dropped = new ItemEntity(level, player.getX(), player.getY() + 0.3, player.getZ(), stack);
					dropped.setNoPickUpDelay();
					level.addFreshEntity(dropped);
				}
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3f, 1.4f);
			}
		}

		/** Hovers over its owner's right shoulder. */
		private void follow(Player player) {
			setState(FOLLOWING, null);
			float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
			Vec3 spot = player.position().add(-Mth.cos(yaw) * 0.9, player.getBbHeight() + 0.5 + Mth.sin(tickCount * 0.08f) * 0.15,
					-Mth.sin(yaw) * 0.9);
			if (distanceToSqr(spot) > 0.15) {
				flyTo(spot, 0.45);
			} else {
				setDeltaMovement(getDeltaMovement().scale(0.7));
			}
			getLookControl().setLookAt(player.getX(), player.getEyeY(), player.getZ());
		}
	}

	// ------------------------------------------------------------------------------------------------ owner

	/** Its owner picks it back up, health and all. */
	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (!isOwnedBy(player) || !(held.isEmpty() || held.is(Skullcranes.SERVO_DRONE))) {
			return super.mobInteract(player, hand);
		}
		if (level() instanceof ServerLevel level) {
			ItemStack item = ServoDroneItem.withHealth(getHealth());
			if (!carried().isEmpty()) {
				player.getInventory().placeItemBackInInventory(carried().copy(), Prediction.SERVER_ONLY);
				setCarried(ItemStack.EMPTY);
			}
			if (held.isEmpty()) {
				player.setItemInHand(hand, item);
			} else {
				player.getInventory().placeItemBackInInventory(item, Prediction.SERVER_ONLY);
			}
			level.playSound(null, getX(), getY(), getZ(), Skullcranes.RELEASE, SoundSource.NEUTRAL, 0.8f, 1.4f);
			discard();
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		Entity attacker = source.getEntity();
		if (attacker instanceof Player player && isOwnedBy(player) || attacker instanceof ServoDrone) {
			return false;
		}
		return super.hurtServer(level, source, damage);
	}

	@Override
	public void die(DamageSource source) {
		if (level() instanceof ServerLevel level && !carried().isEmpty()) {
			spawnAtLocation(level, carried().copy());
			setCarried(ItemStack.EMPTY);
		}
		super.die(source);
	}

	@Override
	protected void tickDeath() {
		if (deathTime == 0) {
			setNoGravity(false);
		}
		super.tickDeath();
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.storeNullable("Owner", UUIDUtil.CODEC, owner);
		if (!carried().isEmpty()) {
			output.store("Carried", ItemStack.CODEC, carried());
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
		Optional<ItemStack> carried = input.read("Carried", ItemStack.CODEC);
		setCarried(carried.orElse(ItemStack.EMPTY));
	}

	@Override
	public boolean removeWhenFarAway(double distanceSq) {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return Skullcranes.AMBIENT;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 240;
	}

	@Override
	protected float getSoundVolume() {
		return 0.4f;
	}

	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * 1.3f;
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
