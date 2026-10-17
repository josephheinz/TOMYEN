package com.jheinz.tomyen.moose;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The Moose: taller than a horse, long-legged, with a humped shoulder, a long drooping muzzle and a bell of hair under
 * its chin. A bull carries a great pair of palmate antlers; a cow and a calf have none.
 *
 * <p>It's peaceable: it wanders the woods and wades the shallows (it doesn't mind water at all), and stops at trees to
 * browse their leaves ({@link MooseBrowseGoal}). Hit one, or crowd a calf, and the grown moose round about turn on you
 * ({@link MooseAttackGoal}): a bull paws the ground, drops his head and charges, antlers first; a cow rears up and
 * stomps you with her forehooves. Both hit hard.
 *
 * <p>Feeding a wild one (apples, berries, leaves: {@link Mooses#FOOD}) calms it, now and then; a calm moose takes a
 * saddle (the vanilla saddle, in the vanilla saddle slot) and carries a rider. It's slower than a horse and can't jump,
 * but it steps straight up a block, walks on powder snow without sinking or freezing, and wades and swims steadily with
 * a rider on. Calm moose breed on the same food; a calf grows up faster for it.
 *
 * <p>A bull's rack follows the moon, which stands in for the seasons here: as the old moon wanes into the new ("late
 * winter") he sheds it, sometimes dropping an antler where he stands; it grows back from nubs over the next three days
 * ("spring and summer"), full again by the full moon ("the autumn rut"). A bull killed with his full rack drops antlers
 * besides his leather and venison ({@link Mooses#ANTLER_LOOT}).
 *
 * <p>The client animates browsing, the wind-up, the charge and the stomp from {@link #action()}; the rack's growth from
 * {@link #antlerGrowth()}.
 */
public class Moose extends Animal implements NeutralMob {
	public static final byte IDLE = 0;
	public static final byte BROWSING = 1;
	/** A bull pawing the ground, head down, about to charge. */
	public static final byte WINDING_UP = 2;
	public static final byte CHARGING = 3;
	/** A cow up on her hind legs and coming down on whoever's in front of her. */
	public static final byte STOMPING = 4;

	/** How long a stomp takes, and the tick its forehooves land (the model's slam is 60% of the way through). */
	public static final int STOMP_TICKS = 26;
	public static final int STOMP_LANDS = 16;

	/** How long a rack takes to grow back after it's shed: three days. */
	static final int ANTLER_GROWTH_TICKS = 3 * 24000;
	/** A bull with his full rack, in the shedding phases, sheds it about once in this many ticks. */
	private static final int SHED_CHANCE = 2400;
	/** One feed in this many calms a wild moose. */
	private static final int CALM_CHANCE = 3;
	/** Ridden, it goes at this fraction of its walking speed attribute (a horse goes at all of its own). */
	private static final float RIDDEN_SPEED = 0.9f;

	private static final byte FLAG_BULL = 1;
	private static final byte FLAG_CALM = 2;

	private static final EntityDataAccessor<Byte> DATA_FLAGS = SynchedEntityData.defineId(Moose.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Byte> DATA_ACTION = SynchedEntityData.defineId(Moose.class, EntityDataSerializers.BYTE);
	/** The rack's growth, in percent (0 just shed, 100 full); only kept up to date when it changes by a whole percent. */
	private static final EntityDataAccessor<Byte> DATA_ANTLERS = SynchedEntityData.defineId(Moose.class, EntityDataSerializers.BYTE);

	private static final UniformInt PERSISTENT_ANGER_TIME = TimeUtil.rangeOfSeconds(20, 39);
	private long persistentAngerEndTime;
	private @Nullable EntityReference<LivingEntity> persistentAngerTarget;

	/** Server side: ticks since the rack was shed (or {@link #ANTLER_GROWTH_TICKS} when it's full). */
	private int antlerTicks = ANTLER_GROWTH_TICKS;

	// Client side: the browse, wind-up and charge blends (0 to 1, eased in and out), and ticks into a stomp.
	private float browse;
	private float browseO;
	private float windup;
	private float windupO;
	private float charge;
	private float chargeO;
	private int stompTicks;
	private float pawClock;

	public Moose(EntityType<? extends Moose> type, Level level) {
		super(type, level);
		// Moose love wetlands: water is only a little harder going than dry land.
		setPathfindingMalus(PathType.WATER, 1.0f);
		setPathfindingMalus(PathType.WATER_BORDER, 0.0f);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Animal.createAnimalAttributes()
				.add(Attributes.MAX_HEALTH, 44.0)
				.add(Attributes.ARMOR, 2.0)
				.add(Attributes.MOVEMENT_SPEED, 0.2)
				.add(Attributes.ATTACK_DAMAGE, 7.0)
				.add(Attributes.ATTACK_KNOCKBACK, 1.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
				.add(Attributes.FOLLOW_RANGE, 24.0)
				.add(Attributes.STEP_HEIGHT, 1.1);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_FLAGS, (byte) 0);
		builder.define(DATA_ACTION, IDLE);
		builder.define(DATA_ANTLERS, (byte) 100);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new MooseAttackGoal(this));
		// Calves bolt when hurt; grown moose only from fire and the like (they turn and fight anything else).
		goalSelector.addGoal(1, new PanicGoal(this, 1.6, moose -> moose.isBaby() ? DamageTypeTags.PANIC_CAUSES
				: DamageTypeTags.PANIC_ENVIRONMENTAL_CAUSES));
		goalSelector.addGoal(2, new BreedGoal(this, 1.0));
		goalSelector.addGoal(3, new TemptGoal(this, 1.1, stack -> stack.is(Mooses.FOOD), false));
		goalSelector.addGoal(4, new FollowParentGoal(this, 1.15));
		goalSelector.addGoal(5, new MooseBrowseGoal(this));
		goalSelector.addGoal(6, new RandomStrollGoal(this, 0.8));
		goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new MooseHurtByTargetGoal(this));
		targetSelector.addGoal(2, new ProtectCalfGoal(this));
		targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isAngryAt));
		targetSelector.addGoal(4, new ResetUniversalAngerTargetGoal<>(this, false));
	}

	// --- State ------------------------------------------------------------------------------------------------

	public boolean isBull() {
		return (entityData.get(DATA_FLAGS) & FLAG_BULL) != 0;
	}

	public void setBull(boolean bull) {
		setFlag(FLAG_BULL, bull);
	}

	/** Fed until it trusts people: it takes a saddle and a rider, and leaves people near its calves alone. */
	public boolean isCalm() {
		return (entityData.get(DATA_FLAGS) & FLAG_CALM) != 0;
	}

	public void setCalm(boolean calm) {
		setFlag(FLAG_CALM, calm);
	}

	private void setFlag(byte flag, boolean on) {
		byte flags = entityData.get(DATA_FLAGS);
		entityData.set(DATA_FLAGS, (byte) (on ? flags | flag : flags & ~flag));
	}

	public byte action() {
		return entityData.get(DATA_ACTION);
	}

	void setAction(byte action) {
		entityData.set(DATA_ACTION, action);
	}

	/** A bull's rack, 0 (just shed) to 1 (full); 0 for a cow or a calf. */
	public float antlerGrowth() {
		return isBull() && !isBaby() ? entityData.get(DATA_ANTLERS) / 100.0f : 0.0f;
	}

	public boolean hasFullRack() {
		return isBull() && !isBaby() && antlerTicks >= ANTLER_GROWTH_TICKS;
	}

	private void setAntlerTicks(int ticks) {
		antlerTicks = Mth.clamp(ticks, 0, ANTLER_GROWTH_TICKS);
		byte percent = (byte) (antlerTicks * 100L / ANTLER_GROWTH_TICKS);
		if (entityData.get(DATA_ANTLERS) != percent) {
			entityData.set(DATA_ANTLERS, percent);
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("Bull", isBull());
		output.putBoolean("Calm", isCalm());
		output.putInt("AntlerTicks", antlerTicks);
		addPersistentAngerSaveData(output);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		setBull(input.getBooleanOr("Bull", false));
		setCalm(input.getBooleanOr("Calm", false));
		setAntlerTicks(input.getIntOr("AntlerTicks", ANTLER_GROWTH_TICKS));
		readPersistentAngerSaveData(level(), input);
	}

	/** Half are bulls; a bull met in the shedding phases may have shed already, or be growing his rack back. */
	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
			@Nullable SpawnGroupData groupData) {
		RandomSource random = level.getRandom();
		setBull(random.nextBoolean());
		setAntlerTicks(sheddingSeason(level.getLevel(), blockPosition()) && random.nextBoolean()
				? random.nextInt(ANTLER_GROWTH_TICKS / 3) : ANTLER_GROWTH_TICKS);
		if (groupData == null) {
			groupData = new AgeableMob.AgeableMobGroupData(0.3f);
		}
		return super.finalizeSpawn(level, difficulty, reason, groupData);
	}

	/**
	 * Grass under it as for any animal, in daylight; out on the snowy plains, only where there are trees about (it
	 * browses: no trees, no moose).
	 */
	public static boolean checkMooseSpawnRules(EntityType<Moose> type, LevelAccessor level, EntitySpawnReason reason, BlockPos pos,
			RandomSource random) {
		if (!checkAnimalSpawnRules(type, level, reason, pos, random)) {
			return false;
		}
		return EntitySpawnReason.isSpawner(reason) || !level.getBiome(pos).is(Biomes.SNOWY_PLAINS) || treesNear(level, pos);
	}

	private static boolean treesNear(LevelAccessor level, BlockPos pos) {
		BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
		for (int dx = -8; dx <= 8; dx += 2) {
			for (int dz = -8; dz <= 8; dz += 2) {
				for (int dy = 0; dy <= 6; dy += 2) {
					BlockState state = level.getBlockState(probe.setWithOffset(pos, dx, dy, dz));
					if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** The waning crescent and the new moon: "late winter", when bulls shed their racks. */
	static boolean sheddingSeason(ServerLevel level, BlockPos pos) {
		MoonPhase phase = level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, Vec3.atCenterOf(pos));
		return phase == MoonPhase.WANING_CRESCENT || phase == MoonPhase.NEW_MOON;
	}

	// --- Ticking ----------------------------------------------------------------------------------------------

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			byte action = action();
			browseO = browse;
			windupO = windup;
			chargeO = charge;
			browse = Mth.approach(browse, action == BROWSING ? 1.0f : 0.0f, 0.08f);
			windup = Mth.approach(windup, action == WINDING_UP ? 1.0f : 0.0f, action == WINDING_UP ? 0.2f : 0.1f);
			charge = Mth.approach(charge, action == CHARGING ? 1.0f : 0.0f, action == CHARGING ? 0.25f : 0.08f);
			stompTicks = action == STOMPING ? stompTicks + 1 : 0;
			if (action == WINDING_UP) {
				pawClock++;
			}
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		updatePersistentAnger(level, true);
		if (isBull() && !isBaby()) {
			if (antlerTicks < ANTLER_GROWTH_TICKS) {
				setAntlerTicks(antlerTicks + 1);
			} else if (sheddingSeason(level, blockPosition()) && random.nextInt(SHED_CHANCE) == 0) {
				shedAntlers(level);
			}
		}
	}

	/** Sheds the rack: one antler sometimes left lying (the other's lost in the snow), and it starts growing back. */
	void shedAntlers(ServerLevel level) {
		setAntlerTicks(0);
		playSound(Mooses.SHED, 1.0f, 0.9f + random.nextFloat() * 0.2f);
		Vec3 head = headPosition();
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Mooses.MOOSE_ANTLER), head.x, head.y + 0.4, head.z, 12, 0.4, 0.2, 0.4,
				0.08);
		if (random.nextInt(2) == 0) {
			spawnAtLocation(level, new ItemStack(Mooses.MOOSE_ANTLER), 2.0f);
		}
	}

	/** Where its head is, roughly: ahead of its body at about its eye height. */
	Vec3 headPosition() {
		float yaw = yBodyRot * Mth.DEG_TO_RAD;
		return position().add(-Mth.sin(yaw) * 1.6 * getAgeScale(), getEyeHeight(), Mth.cos(yaw) * 1.6 * getAgeScale());
	}

	/** Where its forehooves come down, roughly: just ahead of its chest, on the ground. */
	Vec3 hoofPosition() {
		float yaw = yBodyRot * Mth.DEG_TO_RAD;
		return position().add(-Mth.sin(yaw) * 1.3, 0.05, Mth.cos(yaw) * 1.3);
	}

	// Client-side animation state.

	public float browseAmount(float partialTicks) {
		return Mth.lerp(partialTicks, browseO, browse);
	}

	public float windupAmount(float partialTicks) {
		return Mth.lerp(partialTicks, windupO, windup);
	}

	public float chargeAmount(float partialTicks) {
		return Mth.lerp(partialTicks, chargeO, charge);
	}

	/** How far through a stomp, 0 to 1 (0 when not stomping). */
	public float stompProgress(float partialTicks) {
		return action() == STOMPING ? Mth.clamp((stompTicks + partialTicks) / STOMP_TICKS, 0.0f, 1.0f) : 0.0f;
	}

	public float pawClock(float partialTicks) {
		return pawClock + (action() == WINDING_UP ? partialTicks : 0.0f);
	}

	/** Wades rather than floats: it only starts to swim once the water's over its belly. */
	@Override
	public double getFluidJumpThreshold() {
		return isBaby() ? 0.5 : 1.2;
	}

	@Override
	protected float getWaterSlowDown() {
		return 0.9f;
	}

	@Override
	public int getMaxHeadYRot() {
		return 50;
	}

	// --- Feeding, saddling and riding -------------------------------------------------------------------------

	@Override
	public boolean isFood(ItemStack stack) {
		return stack.is(Mooses.FOOD);
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!isBaby() && !isCalm() && isFood(stack)) {
			// A wild moose takes food from a hand now and then; enough of it and it calms down.
			if (!level().isClientSide()) {
				usePlayerItem(player, hand, stack);
				playSound(Mooses.EAT, 1.0f, 1.0f);
				if (player.getAbilities().instabuild || random.nextInt(CALM_CHANCE) == 0) {
					setCalm(true);
					stopBeingAngry();
					level().broadcastEntityEvent(this, (byte) 7);
				} else {
					level().broadcastEntityEvent(this, (byte) 6);
				}
			}
			return InteractionResult.SUCCESS;
		}
		if (!isBaby() && !isCalm() && stack.is(Items.SADDLE)) {
			// Not from a stranger.
			if (!level().isClientSide()) {
				playSound(Mooses.SNORT, 1.0f, 1.1f);
				level().broadcastEntityEvent(this, (byte) 6);
			}
			return InteractionResult.SUCCESS;
		}
		if (isSaddled() && !isVehicle() && !player.isSecondaryUseActive() && !isFood(stack)) {
			if (!level().isClientSide()) {
				player.startRiding(this);
			}
			return InteractionResult.SUCCESS;
		}
		InteractionResult result = super.mobInteract(player, hand);
		if (!result.consumesAction() && isEquippableInSlot(stack, EquipmentSlot.SADDLE)) {
			return stack.interactLivingEntity(player, this, hand);
		}
		return result;
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == 7 || id == 6) {
			ParticleOptions particle = id == 7 ? ParticleTypes.HEART : ParticleTypes.SMOKE;
			for (int i = 0; i < 7; i++) {
				level().addParticle(particle, getRandomX(1.0), getRandomY() + 0.5, getRandomZ(1.0), random.nextGaussian() * 0.02,
						random.nextGaussian() * 0.02, random.nextGaussian() * 0.02);
			}
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	public boolean canUseSlot(EquipmentSlot slot) {
		return slot != EquipmentSlot.SADDLE ? super.canUseSlot(slot) : isAlive() && !isBaby() && isCalm();
	}

	@Override
	protected boolean canDispenserEquipIntoSlot(EquipmentSlot slot) {
		return slot == EquipmentSlot.SADDLE && isCalm() || super.canDispenserEquipIntoSlot(slot);
	}

	@Override
	protected Holder<SoundEvent> getEquipSound(EquipmentSlot slot, ItemStack stack, Equippable equippable) {
		return slot == EquipmentSlot.SADDLE ? SoundEvents.HORSE_SADDLE : super.getEquipSound(slot, stack, equippable);
	}

	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		return isSaddled() && getFirstPassenger() instanceof Player rider ? rider : super.getControllingPassenger();
	}

	/** Turns as its rider looks, as a horse does. */
	@Override
	protected void tickRidden(Player controller, Vec3 riddenInput) {
		super.tickRidden(controller, riddenInput);
		setRot(controller.getYRot(), controller.getXRot() * 0.5f);
		yRotO = yBodyRot = yHeadRot = getYRot();
	}

	/** Forward at its pace, a little sideways, and only slowly backward. */
	@Override
	protected Vec3 getRiddenInput(Player controller, Vec3 selfInput) {
		float forward = controller.zza;
		return new Vec3(controller.xxa * 0.5f, 0.0, forward <= 0.0f ? forward * 0.25f : forward);
	}

	@Override
	protected float getRiddenSpeed(Player controller) {
		return (float) getAttributeValue(Attributes.MOVEMENT_SPEED) * RIDDEN_SPEED;
	}

	@Override
	protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
		super.positionRider(passenger, moveFunction);
		if (passenger instanceof LivingEntity living) {
			living.yBodyRot = yBodyRot;
		}
	}

	@Override
	protected void addPassenger(Entity passenger) {
		super.addPassenger(passenger);
		passenger.absSnapRotationTo(getViewYRot(0.0f), getViewXRot(0.0f));
	}

	// --- Breeding ---------------------------------------------------------------------------------------------

	/** Only calm moose breed (a wild one eats what it's given and calms down first). */
	@Override
	public boolean canFallInLove() {
		return isCalm() && super.canFallInLove();
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		Moose calf = Mooses.MOOSE.create(level, EntitySpawnReason.BREEDING);
		if (calf != null) {
			calf.setBull(random.nextBoolean());
			calf.setAntlerTicks(0);
			calf.setCalm(true);
		}
		return calf;
	}

	/** A calf grows up into a moose with its rack still to grow. */
	@Override
	protected void ageBoundaryReached() {
		super.ageBoundaryReached();
		if (!isBaby() && !level().isClientSide()) {
			setAntlerTicks(0);
		}
	}

	// --- Fighting ---------------------------------------------------------------------------------------------

	/**
	 * Hits a target with a charge or a stomp: the damage, and a shove in the direction it was going (with a lift, for
	 * a charge). Returns whether it landed.
	 */
	boolean strike(ServerLevel level, LivingEntity target, float damage, Vec3 direction, double shove, double lift) {
		DamageSource source = damageSources().mobAttack(this);
		boolean hit = target.hurtServer(level, source, damage);
		if (hit) {
			target.knockback(shove, -direction.x, -direction.z, source, damage);
			target.setDeltaMovement(target.getDeltaMovement().add(0.0, lift, 0.0));
			target.needsSync = true;
			EnchantmentHelper.doPostAttackEffects(level, target, source);
			setLastHurtMob(target);
		}
		return hit;
	}

	/** Whether it'll go for this one: alive, attackable, and not in creative or spectating. */
	boolean fair(@Nullable LivingEntity target) {
		return target != null && target.isAlive() && canAttack(target)
				&& !(target instanceof Player player && (player.isCreative() || player.isSpectator()));
	}

	@Override
	public void startPersistentAngerTimer() {
		setTimeToRemainAngry(PERSISTENT_ANGER_TIME.sample(random));
	}

	@Override
	public void setPersistentAngerEndTime(long endTime) {
		persistentAngerEndTime = endTime;
	}

	@Override
	public long getPersistentAngerEndTime() {
		return persistentAngerEndTime;
	}

	@Override
	public void setPersistentAngerTarget(@Nullable EntityReference<LivingEntity> target) {
		persistentAngerTarget = target;
	}

	@Override
	public @Nullable EntityReference<LivingEntity> getPersistentAngerTarget() {
		return persistentAngerTarget;
	}

	// --- Sounds -----------------------------------------------------------------------------------------------

	@Override
	protected SoundEvent getAmbientSound() {
		// Now and then a bull with his rack bellows instead of groaning.
		return hasFullRack() && random.nextInt(5) == 0 ? Mooses.BELLOW : Mooses.AMBIENT;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 240;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return Mooses.HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return Mooses.DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		playSound(Mooses.STEP, 0.6f, 1.0f);
	}

	@Override
	public float getVoicePitch() {
		return isBaby() ? 1.5f + (random.nextFloat() - random.nextFloat()) * 0.1f : super.getVoicePitch();
	}

	/** A bull with his rack also rolls the antler table. */
	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		if (hasFullRack()) {
			dropFromLootTable(level, source, killedByPlayer, Mooses.ANTLER_LOOT);
		}
	}

	// --- Targeting --------------------------------------------------------------------------------------------

	/** Hit one, and the grown moose round about come too (a hurt calf only calls them). */
	private static class MooseHurtByTargetGoal extends HurtByTargetGoal {
		MooseHurtByTargetGoal(Moose moose) {
			super(moose);
			setAlertOthers();
		}

		@Override
		public void start() {
			super.start();
			if (mob.isBaby()) {
				alertOthers();
				stop();
			}
		}

		@Override
		protected void alertOther(Mob other, LivingEntity hurtBy) {
			if (other instanceof Moose && !other.isBaby()) {
				super.alertOther(other, hurtBy);
			}
		}
	}

	/** A wild cow or bull goes for anyone who comes too close to a calf. */
	private static class ProtectCalfGoal extends TargetGoal {
		private static final double CALF_RANGE = 12.0;
		private static final double TOO_CLOSE = 4.5;
		private final Moose moose;

		ProtectCalfGoal(Moose moose) {
			super(moose, true);
			this.moose = moose;
		}

		@Override
		public boolean canUse() {
			if (moose.isBaby() || moose.isCalm() || moose.isVehicle() || moose.getRandom().nextInt(reducedTickDelay(10)) != 0) {
				return false;
			}
			for (Moose calf : moose.level().getEntitiesOfClass(Moose.class, moose.getBoundingBox().inflate(CALF_RANGE, 4.0, CALF_RANGE),
					Moose::isBaby)) {
				Player intruder = moose.level().getNearestPlayer(calf, TOO_CLOSE);
				if (intruder != null && moose.fair(intruder) && !(intruder.getVehicle() instanceof Moose)) {
					targetMob = intruder;
					return true;
				}
			}
			return false;
		}

		@Override
		public void start() {
			mob.setTarget(targetMob);
			moose.playSound(Mooses.SNORT, 1.2f, 0.9f);
			super.start();
		}
	}
}
