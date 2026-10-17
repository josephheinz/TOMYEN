package com.jheinz.tomyen.gnome;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.fae.Fae;
import com.jheinz.tomyen.fae.Feywild;

/**
 * A garden gnome come alive: one of a {@link GnomeCaptain}'s squad. It follows its captain in formation, pulls up
 * ripe crops when the squad raids a farm (see {@link CropRaids}), carries what it took home to the squad's fae ring
 * (or wherever it mustered), and stashes it there. Hit any of the squad and they all scatter, regroup round the
 * captain and come back to poke at whoever did it, for a point or two of damage at a time. If the captain dies the
 * squad breaks and every gnome flees for good. A gnome with no captain wanders, fights back when it's hit, and joins
 * any captain it comes across with room in the ranks.
 *
 * <p>When day breaks (outside the Feywild, where it never does) a gnome sets into a glazed ceramic statue wherever it
 * stands, still holding anything it stole: it doesn't move, can't be pushed or knocked back, and takes no damage. A
 * player can break it, for a Garden Gnome block (and whatever it was carrying). At nightfall it comes alive again.
 *
 * <p>While it has stolen goods (in its arms or stashed) it doesn't despawn. Killed, it drops them, along with its loot
 * table's Fae Dust and now and then a Garden Gnome.
 */
public class Gnome extends Monster {
	public static final int VARIANTS = 3;
	public static final byte MODE_IDLE = 0;
	public static final byte MODE_MARCHING = 1;
	public static final byte MODE_PANIC = 2;
	/** How many stacks a gnome keeps stashed at home. */
	private static final int MAX_STASH = 9;
	/** Ticks a statue takes to set (or crack open), client side. */
	public static final float SET_TICKS = 30.0f;
	/** Ticks a captain can be missing (unloaded, say) before its troops give up on it. */
	private static final int CAPTAIN_PATIENCE = 400;
	private static final double JOIN_RANGE = 16.0;

	private static final EntityDataAccessor<Boolean> DATA_STATUE = SynchedEntityData.defineId(Gnome.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(Gnome.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Byte> DATA_MODE = SynchedEntityData.defineId(Gnome.class, EntityDataSerializers.BYTE);

	private @Nullable UUID captainId;
	private @Nullable GnomeCaptain captain;
	private int captainMissing;
	/** The squad broke when its captain fell: it only runs now. */
	private boolean broken;
	protected @Nullable BlockPos home;
	private final List<ItemStack> stash = new ArrayList<>();
	/** A leaderless gnome's own scatter, after it's hit. */
	private int panicTicks;
	private @Nullable Vec3 panicFrom;

	// Client side: how far set into a statue (0 to 1), and the stride and clock it froze on.
	private float statueAmount;
	private float statueAmountO;
	private boolean frozenCaptured;
	private float frozenWalkPos;
	private float frozenWalkSpeed;
	private float frozenAge;

	public Gnome(EntityType<? extends Gnome> type, Level level) {
		super(type, level);
		xpReward = 3;
		// A statue's gaze stays where it set.
		lookControl = new LookControl(this) {
			@Override
			public void tick() {
				if (!isStatue()) {
					super.tick();
				}
			}
		};
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 8.0)
				.add(Attributes.ATTACK_DAMAGE, 1.5)
				.add(Attributes.ATTACK_KNOCKBACK, 0.0)
				.add(Attributes.MOVEMENT_SPEED, 0.25)
				.add(Attributes.FOLLOW_RANGE, 24.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_STATUE, false);
		builder.define(DATA_VARIANT, 0);
		builder.define(DATA_MODE, MODE_IDLE);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new FleeGoal());
		goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25, false));
		goalSelector.addGoal(3, new HarvestGoal());
		goalSelector.addGoal(4, new FollowFormationGoal());
		goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7) {
			@Override
			public boolean canUse() {
				return captainId == null && !broken && super.canUse();
			}
		});
		goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new SquadTargetGoal(this));
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
			@Nullable SpawnGroupData groupData) {
		entityData.set(DATA_VARIANT, random.nextInt(VARIANTS));
		if (home == null) {
			home = blockPosition();
		}
		return super.finalizeSpawn(level, difficulty, reason, groupData);
	}

	// ------------------------------------------------------------------------------------------------ the squad

	public int variant() {
		return entityData.get(DATA_VARIANT);
	}

	public byte mode() {
		return entityData.get(DATA_MODE);
	}

	protected void setMode(byte mode) {
		if (mode() != mode) {
			entityData.set(DATA_MODE, mode);
		}
	}

	public boolean isBroken() {
		return broken;
	}

	public @Nullable BlockPos home() {
		return home;
	}

	/** Its captain, if it has one and it's loaded. */
	public @Nullable GnomeCaptain captain() {
		if (captainId == null) {
			return null;
		}
		if (captain == null || captain.isRemoved() || !captainId.equals(captain.getUUID())) {
			captain = level().getEntity(captainId) instanceof GnomeCaptain found ? found : null;
		}
		return captain != null && captain.isAlive() ? captain : null;
	}

	/** Whether this is its captain (alive or not: a dying captain still knows its troops). */
	boolean follows(GnomeCaptain leader) {
		return leader.getUUID().equals(captainId);
	}

	void join(GnomeCaptain leader) {
		captainId = leader.getUUID();
		captain = leader;
		captainMissing = 0;
		broken = false;
		home = leader.home();
	}

	/** The captain fell: no more raids, only running. */
	void rout() {
		captainId = null;
		captain = null;
		broken = true;
		panicTicks = 0;
		setTarget(null);
	}

	public boolean hasLoot() {
		return !getMainHandItem().isEmpty() || !stash.isEmpty();
	}

	public boolean isCarrying() {
		return !getMainHandItem().isEmpty();
	}

	/** Puts what it's carrying into its stash at home (the oldest goes, if it's full). */
	void stashCarried(ServerLevel level) {
		ItemStack carried = getMainHandItem();
		if (carried.isEmpty()) {
			return;
		}
		setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		for (ItemStack stack : stash) {
			if (ItemStack.isSameItemSameComponents(stack, carried) && stack.getCount() < stack.getMaxStackSize()) {
				int moved = Math.min(carried.getCount(), stack.getMaxStackSize() - stack.getCount());
				stack.grow(moved);
				carried.shrink(moved);
			}
		}
		if (!carried.isEmpty()) {
			if (stash.size() >= MAX_STASH) {
				stash.removeFirst();
			}
			stash.add(carried);
		}
		level.sendParticles(Fae.SPARKLE, getX(), getY(0.5), getZ(), 8, 0.2, 0.2, 0.2, 0.02);
	}

	/** Takes a crop it's standing by. */
	boolean steal(ServerLevel level, BlockPos crop) {
		ItemStack produce = CropRaids.harvest(level, crop, this);
		if (produce.isEmpty()) {
			return false;
		}
		setItemSlot(EquipmentSlot.MAINHAND, produce);
		playSound(Gnomes.GIGGLE, 0.8f, 1.0f + random.nextFloat() * 0.2f);
		return true;
	}

	/** Hit by something: the squad scatters (the captain decides), or a lone gnome scatters on its own. */
	protected void alarm(LivingEntity attacker) {
		GnomeCaptain leader = captain();
		if (leader != null) {
			leader.alarm(attacker);
		} else if (!broken && panicTicks <= 0) {
			panicTicks = 30;
			panicFrom = attacker.position();
			playSound(Gnomes.SCATTER, 0.8f, 1.0f + random.nextFloat() * 0.2f);
		}
	}

	/** Running for it: scattering on the captain's order, panicking alone, or routed. */
	boolean isFleeing() {
		if (broken || panicTicks > 0) {
			return true;
		}
		GnomeCaptain leader = captain();
		return leader != null && leader.order() == GnomeCaptain.Order.SCATTER;
	}

	@Nullable Vec3 threat() {
		GnomeCaptain leader = captain();
		if (leader != null && leader.foe() != null) {
			return leader.foe().position();
		}
		if (panicFrom != null && panicTicks > 0) {
			return panicFrom;
		}
		Player player = level().getNearestPlayer(this, 16.0);
		return player != null && !player.isSpectator() ? player.position() : null;
	}

	/** Troops keep track of their captain (and give up on one long missing), and lone gnomes look for one to join. */
	protected void tickSquad(ServerLevel level) {
		if (panicTicks > 0) {
			panicTicks--;
		}
		if (tickCount % 20 != 0) {
			return;
		}
		if (captainId != null) {
			if (captain() == null) {
				captainMissing += 20;
				if (captainMissing > CAPTAIN_PATIENCE) {
					captainId = null;
					captain = null;
				}
			} else {
				captainMissing = 0;
			}
		} else if (!broken) {
			for (GnomeCaptain leader : level.getEntitiesOfClass(GnomeCaptain.class, getBoundingBox().inflate(JOIN_RANGE),
					leader -> leader.isAlive() && !leader.isStatue() && leader.hasRoom())) {
				join(leader);
				leader.refreshMembers(level);
				break;
			}
		}
	}

	protected void updateMode() {
		GnomeCaptain leader = captain();
		if (isFleeing()) {
			setMode(MODE_PANIC);
		} else if (leader != null && leader.isMarching() && getDeltaMovement().horizontalDistanceSqr() > 1.0e-4) {
			setMode(MODE_MARCHING);
		} else {
			setMode(MODE_IDLE);
		}
	}

	// ------------------------------------------------------------------------------------------------ the statue

	public boolean isStatue() {
		return entityData.get(DATA_STATUE);
	}

	/** Day outside the Feywild: when gnomes set into statues. */
	public static boolean isDaytime(Level level) {
		return !Feywild.is(level) && level.isBrightOutside();
	}

	private void freeze(ServerLevel level) {
		entityData.set(DATA_STATUE, true);
		getNavigation().stop();
		setTarget(null);
		setAggressive(false);
		setDeltaMovement(0.0, Math.min(0.0, getDeltaMovement().y), 0.0);
		setMode(MODE_IDLE);
		updateControlFlags();
		level.playSound(null, getX(), getY(), getZ(), Gnomes.FREEZE, getSoundSource(), 0.8f, 0.9f + random.nextFloat() * 0.2f);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Gnomes.GARDEN_GNOME.defaultBlockState()), getX(), getY(0.5), getZ(),
				10, 0.2, 0.3, 0.2, 0.05);
	}

	private void thaw(ServerLevel level) {
		entityData.set(DATA_STATUE, false);
		updateControlFlags();
		level.playSound(null, getX(), getY(), getZ(), Gnomes.THAW, getSoundSource(), 0.8f, 0.9f + random.nextFloat() * 0.2f);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Gnomes.GARDEN_GNOME.defaultBlockState()), getX(), getY(0.5), getZ(),
				14, 0.2, 0.3, 0.2, 0.08);
		level.sendParticles(Fae.SPARKLE, getX(), getY(0.6), getZ(), 6, 0.2, 0.3, 0.2, 0.02);
	}

	/** Broken by a player: the statue shatters into a Garden Gnome (unless they're in creative), and what it held. */
	private void shatter(ServerLevel level, boolean drops) {
		level.playSound(null, getX(), getY(), getZ(), Gnomes.SHATTER, getSoundSource(), 1.0f, 0.9f + random.nextFloat() * 0.2f);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Gnomes.GARDEN_GNOME.defaultBlockState()), getX(), getY(0.5), getZ(),
				30, 0.2, 0.35, 0.2, 0.1);
		if (drops) {
			Block.popResource(level, blockPosition(), new ItemStack(Gnomes.GARDEN_GNOME_ITEM));
			dropStolen(level);
		}
		onLost(level);
		gameEvent(GameEvent.ENTITY_DIE);
		discard();
	}

	/** It's gone (killed or shattered): a captain's squad breaks. */
	protected void onLost(ServerLevel level) {
	}

	private void tickStatue(ServerLevel level) {
		boolean day = isDaytime(level);
		// Not all at once: they set (and wake) one by one as the light changes.
		if (!isStatue() && day && random.nextInt(40) == 0) {
			freeze(level);
		} else if (isStatue() && !day && random.nextInt(60) == 0) {
			thaw(level);
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		tickStatue(level);
		if (!isStatue()) {
			tickSquad(level);
			updateMode();
		}
	}

	/** A statue's goals all stand still; it wakes with them back. */
	@Override
	protected void updateControlFlags() {
		super.updateControlFlags();
		if (isStatue()) {
			goalSelector.setControlFlag(Goal.Flag.MOVE, false);
			goalSelector.setControlFlag(Goal.Flag.JUMP, false);
			goalSelector.setControlFlag(Goal.Flag.LOOK, false);
		}
		targetSelector.setControlFlag(Goal.Flag.TARGET, !isStatue());
	}

	@Override
	protected void tickHeadTurn(float yBodyRotT) {
		if (!isStatue()) {
			super.tickHeadTurn(yBodyRotT);
		}
	}

	@Override
	protected boolean isImmobile() {
		return isStatue() || super.isImmobile();
	}

	@Override
	public void knockback(double strength, double x, double z, DamageSource source, float yRot, boolean shield) {
		if (!isStatue()) {
			super.knockback(strength, x, z, source, yRot, shield);
		}
	}

	@Override
	public void push(double x, double y, double z) {
		if (!isStatue()) {
			super.push(x, y, z);
		}
	}

	@Override
	public boolean isPushable() {
		return !isStatue() && super.isPushable();
	}

	@Override
	public void tick() {
		super.tick();
		if (isStatue()) {
			Vec3 motion = getDeltaMovement();
			setDeltaMovement(0.0, Math.min(0.0, motion.y), 0.0);
		}
		if (level().isClientSide()) {
			statueAmountO = statueAmount;
			statueAmount = Mth.approach(statueAmount, isStatue() ? 1.0f : 0.0f, 1.0f / SET_TICKS);
			if (isStatue() && !frozenCaptured) {
				frozenCaptured = true;
				frozenWalkPos = walkAnimation.position();
				frozenWalkSpeed = walkAnimation.speed();
				frozenAge = tickCount;
				if (frozenWalkSpeed < 0.05f && statueAmount >= 1.0f) {
					// Loaded already set: a stride of its own, so a row of statues isn't all in step.
					frozenWalkPos = getId() * 1.7f;
					frozenWalkSpeed = 0.35f;
				}
			} else if (!isStatue()) {
				frozenCaptured = false;
			}
		}
	}

	public float statueAmount(float partialTicks) {
		return Mth.lerp(partialTicks, statueAmountO, statueAmount);
	}

	public float frozenWalkPos() {
		return frozenWalkPos;
	}

	public float frozenWalkSpeed() {
		return frozenWalkSpeed;
	}

	public float frozenAge() {
		return frozenAge;
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (DATA_STATUE.equals(accessor) && level().isClientSide() && isStatue() && tickCount < 2) {
			// Already a statue when it came into view: no setting animation.
			statueAmount = statueAmountO = 1.0f;
		}
	}

	// ------------------------------------------------------------------------------------------------ damage and loot

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (isStatue()) {
			if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
				return super.hurtServer(level, source, damage);
			}
			if (source.getEntity() instanceof Player player && player.getAbilities().mayBuild && isAlive()) {
				shatter(level, !source.isCreativePlayer());
				return true;
			}
			return false;
		}
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && isAlive() && source.getEntity() instanceof LivingEntity attacker && attacker != this && !(attacker instanceof Gnome)) {
			alarm(attacker);
		}
		return hurt;
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit) {
			playSound(Gnomes.POKE, 0.7f, 0.9f + random.nextFloat() * 0.3f);
		}
		return hit;
	}

	@Override
	public void die(DamageSource source) {
		if (level() instanceof ServerLevel level) {
			onLost(level);
		}
		super.die(source);
	}

	/** Its stolen goods: in its arms and stashed. */
	protected void dropStolen(ServerLevel level) {
		ItemStack carried = getMainHandItem();
		if (!carried.isEmpty()) {
			spawnAtLocation(level, carried.copy());
			setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
		for (ItemStack stack : stash) {
			spawnAtLocation(level, stack);
		}
		stash.clear();
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		dropStolen(level);
	}

	@Override
	public boolean removeWhenFarAway(double distanceSq) {
		return !hasLoot() && super.removeWhenFarAway(distanceSq);
	}

	/** Fae, not creatures of the dark: they don't care how lit a spot is (and so can spawn in the Feywild's twilight). */
	@Override
	public float getWalkTargetValue(BlockPos pos, LevelReader level) {
		return 0.0f;
	}

	@Override
	public boolean canPickUpLoot() {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("statue", isStatue());
		output.putInt("variant", variant());
		output.storeNullable("captain", UUIDUtil.CODEC, captainId);
		output.putBoolean("broken", broken);
		output.storeNullable("home", BlockPos.CODEC, home);
		output.store("stash", ItemStack.CODEC.listOf(), stash);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		entityData.set(DATA_STATUE, input.getBooleanOr("statue", false));
		entityData.set(DATA_VARIANT, Mth.clamp(input.getIntOr("variant", 0), 0, VARIANTS - 1));
		captainId = input.read("captain", UUIDUtil.CODEC).orElse(null);
		broken = input.getBooleanOr("broken", false);
		home = input.read("home", BlockPos.CODEC).orElse(null);
		stash.clear();
		input.read("stash", ItemStack.CODEC.listOf()).ifPresent(stash::addAll);
	}

	// ------------------------------------------------------------------------------------------------ sounds

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return isStatue() ? null : Gnomes.AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return Gnomes.HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return Gnomes.DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		playSound(Gnomes.STEP, 0.3f, 1.0f + random.nextFloat() * 0.3f);
	}

	@Override
	public SoundSource getSoundSource() {
		return SoundSource.HOSTILE;
	}

	// ------------------------------------------------------------------------------------------------ goals

	/** Scattering, panicking or routed: runs away from the threat (a routed gnome, toward home if nothing's near). */
	class FleeGoal extends Goal {
		private int repath;

		FleeGoal() {
			setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return isFleeing();
		}

		@Override
		public void start() {
			repath = 0;
			setTarget(null);
		}

		@Override
		public void tick() {
			if (--repath > 0 && !getNavigation().isDone()) {
				return;
			}
			repath = 20;
			Vec3 threat = threat();
			Vec3 to = null;
			if (threat != null && threat.distanceToSqr(position()) < 20.0 * 20.0) {
				to = DefaultRandomPos.getPosAway(Gnome.this, 10, 5, threat);
			} else if (broken && home != null) {
				to = LandRandomPos.getPosTowards(Gnome.this, 12, 5, Vec3.atBottomCenterOf(home));
			}
			if (to == null) {
				to = LandRandomPos.getPos(Gnome.this, 8, 4);
			}
			if (to != null) {
				getNavigation().moveTo(to.x, to.y, to.z, 1.45);
			}
		}

		@Override
		public void stop() {
			getNavigation().stop();
		}
	}

	/** On a raid with empty arms: off to the nearest ripe crop its captain hasn't given someone else, to pull it up. */
	class HarvestGoal extends Goal {
		private @Nullable BlockPos crop;
		private int ticks;
		private boolean done;

		HarvestGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			GnomeCaptain leader = captain();
			if (leader == null || leader.order() != GnomeCaptain.Order.RAID || isCarrying() || !(level() instanceof ServerLevel level)
					|| !CropRaids.mayRaid(level)) {
				return false;
			}
			crop = leader.claimCrop(Gnome.this);
			return crop != null;
		}

		@Override
		public boolean canContinueToUse() {
			GnomeCaptain leader = captain();
			return !done && crop != null && ticks < 240 && leader != null && leader.order() == GnomeCaptain.Order.RAID
					&& CropRaids.isRipe(level().getBlockState(crop));
		}

		@Override
		public void start() {
			ticks = 0;
			done = false;
		}

		@Override
		public void tick() {
			ticks++;
			Vec3 centre = Vec3.atBottomCenterOf(crop);
			getLookControl().setLookAt(centre.x, centre.y, centre.z);
			if (distanceToSqr(centre) < 1.6 * 1.6) {
				getNavigation().stop();
				if (level() instanceof ServerLevel level) {
					swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
					steal(level, crop);
				}
				done = true;
			} else if (ticks % 10 == 1) {
				getNavigation().moveTo(centre.x, centre.y, centre.z, 1.15);
			}
		}

		@Override
		public void stop() {
			GnomeCaptain leader = captain();
			if (leader != null && crop != null) {
				leader.releaseCrop(crop);
			}
			crop = null;
			getNavigation().stop();
		}
	}

	/**
	 * Keeps its place in the squad's formation: a column or a V behind the captain on the march, a huddle round it
	 * otherwise. Close in, it steps straight to its spot; further off, it paths there, hurrying more the further it is.
	 */
	class FollowFormationGoal extends Goal {
		private int repath;

		FollowFormationGoal() {
			setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return captain() != null;
		}

		@Override
		public void tick() {
			GnomeCaptain leader = captain();
			if (leader == null) {
				return;
			}
			Vec3 spot = leader.formationSpot(Gnome.this);
			double dx = spot.x - getX();
			double dz = spot.z - getZ();
			double distance = Math.sqrt(dx * dx + dz * dz);
			if (distance > 2.5 || Math.abs(spot.y - getY()) > 1.5) {
				if (--repath <= 0 || getNavigation().isDone()) {
					repath = 8;
					getNavigation().moveTo(spot.x, spot.y, spot.z, distance > 8.0 ? 1.5 : 1.25);
				}
			} else if (distance > 0.35) {
				getNavigation().stop();
				getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, Mth.clamp(0.6 + distance * 0.35, 0.6, 1.2));
			} else {
				getNavigation().stop();
				if (!leader.isMarching()) {
					// In place: face the way the captain does.
					float yaw = leader.getYRot() * Mth.DEG_TO_RAD;
					getLookControl().setLookAt(getX() - Mth.sin(yaw) * 4.0, getEyeY(), getZ() + Mth.cos(yaw) * 4.0);
				}
			}
		}

		@Override
		public void stop() {
			getNavigation().stop();
		}
	}

	/**
	 * Who it pokes at: whoever its captain has ordered the squad to fight; or, with no captain, whoever last hit it
	 * (once it's done scattering).
	 */
	static class SquadTargetGoal extends Goal {
		private final Gnome gnome;
		private @Nullable LivingEntity foe;

		SquadTargetGoal(Gnome gnome) {
			this.gnome = gnome;
			setFlags(EnumSet.of(Flag.TARGET));
		}

		private @Nullable LivingEntity wanted() {
			if (gnome.isStatue() || gnome.broken || gnome.panicTicks > 0) {
				return null;
			}
			GnomeCaptain leader = gnome instanceof GnomeCaptain self ? self : gnome.captain();
			if (leader != null) {
				return leader.order() == GnomeCaptain.Order.FIGHT ? leader.foe() : null;
			}
			LivingEntity attacker = gnome.getLastHurtByMob();
			return attacker != null && gnome.tickCount - gnome.getLastHurtByMobTimestamp() < 300 ? attacker : null;
		}

		@Override
		public boolean canUse() {
			foe = wanted();
			return foe != null && foe.isAlive() && gnome.canAttack(foe);
		}

		@Override
		public boolean canContinueToUse() {
			return foe != null && foe == wanted() && foe.isAlive() && gnome.canAttack(foe);
		}

		@Override
		public void start() {
			gnome.setTarget(foe);
		}

		@Override
		public void stop() {
			if (gnome.getTarget() == foe) {
				gnome.setTarget(null);
			}
			foe = null;
		}
	}
}
