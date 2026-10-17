package com.jheinz.tomyen.boar;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootTable;

import com.jheinz.tomyen.MyMod;

/**
 * The Wild Boar: a bristly, hump-shouldered wild pig of jungles, taigas and dark forests, found in small groups (a sow or
 * two, piglets and now and then a big tusker). Left alone it grazes, roots in the dirt (see {@link BoarRootGoal}) and
 * can be bred with carrots, beetroots and mushrooms, as a pig is.
 * <p>
 * Hit it, or go near its piglets ({@link ProtectPigletsGoal}), and it charges ({@link BoarChargeGoal}): it paws the ground
 * as a warning, then runs straight at you, ramming for heavy damage and knockback. A charge that misses and runs into a
 * wall or a tree leaves it dazed for a few seconds, taking extra damage: that's the moment to strike. It calms down if
 * left alone for a while or outrun. Piglets don't fight: hurt one and they bolt while the adults nearby come for you.
 * <p>
 * A tusker is the same animal, bigger (by {@link Attributes#SCALE}), tougher and harder hitting, with big tusks; it drops a
 * pair of tusks on top of its loot.
 */
public class Boar extends Animal {
	/** What it's doing, synced for its animation. */
	public static final byte IDLE = 0;
	public static final byte ROOTING = 1;
	public static final byte PAWING = 2;
	public static final byte CHARGING = 3;
	public static final byte STUNNED = 4;

	/** Ticks it stays angry after it was last provoked, if it never lands a blow. */
	static final int CALM_TICKS = 600;
	/** It gives up on a target this far off. */
	static final double GIVE_UP_DISTANCE = 28.0;
	/** Dazed, it takes this much more damage. */
	private static final float STUNNED_DAMAGE = 1.5f;
	/** Adults this close come to a hurt piglet's squeal. */
	private static final double ALARM_RANGE = 16.0;
	private static final float TUSKER_CHANCE = 0.3f;
	private static final float TUSKER_EGG_CHANCE = 0.15f;
	private static final ResourceKey<LootTable> ROOTING_LOOT = ResourceKey.create(Registries.LOOT_TABLE, MyMod.id("gameplay/boar_rooting"));

	private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(Boar.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Boolean> DATA_TUSKER = SynchedEntityData.defineId(Boar.class, EntityDataSerializers.BOOLEAN);

	private int calmTicks;
	// Client side: how far into each pose it is, 0 to 1, eased so the animation blends.
	private final float[] pose = new float[5];
	private final float[] poseO = new float[5];

	public Boar(EntityType<? extends Boar> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Animal.createAnimalAttributes()
				.add(Attributes.MAX_HEALTH, 18.0)
				.add(Attributes.MOVEMENT_SPEED, 0.25)
				.add(Attributes.ATTACK_DAMAGE, 4.0)
				.add(Attributes.ATTACK_KNOCKBACK, 1.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.3)
				.add(Attributes.FOLLOW_RANGE, 20.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_STATE, IDLE);
		builder.define(DATA_TUSKER, false);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new BoarChargeGoal(this));
		goalSelector.addGoal(2, new PanicGoal(this, 1.4) {
			@Override
			protected boolean shouldPanic() {
				return isBaby() && super.shouldPanic();
			}
		});
		goalSelector.addGoal(3, new BreedGoal(this, 1.0));
		goalSelector.addGoal(4, new TemptGoal(this, 1.2, this::isFood, false));
		goalSelector.addGoal(5, new FollowParentGoal(this, 1.1));
		goalSelector.addGoal(6, new BoarRootGoal(this));
		goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 6.0f));
		goalSelector.addGoal(9, new RandomLookAroundGoal(this));
		// Adults only: piglets have no fight in them, and squeal for the adults instead (hurtServer).
		targetSelector.addGoal(1, new HurtByTargetGoal(this) {
			@Override
			public boolean canUse() {
				return !isBaby() && super.canUse();
			}
		}.setAlertOthers());
		targetSelector.addGoal(2, new ProtectPigletsGoal(this));
	}

	// ------------------------------------------------------------------------------------------------- state

	public byte state() {
		return entityData.get(DATA_STATE);
	}

	void setState(byte state) {
		entityData.set(DATA_STATE, state);
	}

	public boolean isStunned() {
		return state() == STUNNED;
	}

	public boolean isTusker() {
		return entityData.get(DATA_TUSKER);
	}

	/** Makes it a tusker: bigger, tougher and harder hitting. */
	void makeTusker() {
		entityData.set(DATA_TUSKER, true);
		addModifier(Attributes.SCALE, 0.2);
		addModifier(Attributes.MAX_HEALTH, 12.0);
		addModifier(Attributes.ATTACK_DAMAGE, 2.0);
		addModifier(Attributes.KNOCKBACK_RESISTANCE, 0.3);
		setHealth(getMaxHealth());
	}

	private void addModifier(Holder<Attribute> attribute, double amount) {
		var instance = getAttribute(attribute);
		if (instance != null && !instance.hasModifier(MyMod.id("tusker"))) {
			AttributeModifier.Operation op = attribute == Attributes.SCALE ? AttributeModifier.Operation.ADD_MULTIPLIED_BASE
					: AttributeModifier.Operation.ADD_VALUE;
			instance.addPermanentModifier(new AttributeModifier(MyMod.id("tusker"), amount, op));
		}
	}

	/** Keeps it angry for a while longer. */
	void provoke() {
		calmTicks = CALM_TICKS;
	}

	@Override
	public void setTarget(@Nullable LivingEntity target) {
		if (target != null && isBaby()) {
			return;
		}
		if (target != null && target != getTarget()) {
			provoke();
		}
		super.setTarget(target);
	}

	// ------------------------------------------------------------------------------------------------- behaviour

	@Override
	public boolean isFood(ItemStack stack) {
		return stack.is(Boars.BOAR_FOOD);
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return Boars.BOAR.create(level, EntitySpawnReason.BREEDING);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, isStunned() ? damage * STUNNED_DAMAGE : damage);
		if (hurt && source.getEntity() instanceof LivingEntity attacker && attacker != this) {
			if (!isBaby()) {
				provoke();
			} else if (!(attacker instanceof Player player && (player.isCreative() || player.isSpectator()))) {
				// A piglet's squeal brings the adults.
				for (Boar adult : level.getEntitiesOfClass(Boar.class, getBoundingBox().inflate(ALARM_RANGE), boar -> !boar.isBaby())) {
					adult.setTarget(attacker);
				}
			}
		}
		return hurt;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (level() instanceof ServerLevel) {
			LivingEntity target = getTarget();
			if (target != null) {
				// It calms down when left alone long enough, or outrun.
				if (--calmTicks <= 0 || !target.isAlive() || distanceToSqr(target) > GIVE_UP_DISTANCE * GIVE_UP_DISTANCE) {
					setTarget(null);
					setAggressive(false);
				}
			}
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			byte state = state();
			for (int i = 1; i < pose.length; i++) {
				poseO[i] = pose[i];
				pose[i] = Mth.approach(pose[i], state == i ? 1.0f : 0.0f, i == CHARGING ? 0.25f : 0.12f);
			}
		}
	}

	/** How far it's into a pose (ROOTING, PAWING, CHARGING or STUNNED), 0 to 1, client side. */
	public float poseAmount(byte state, float partialTicks) {
		return Mth.lerp(partialTicks, poseO[state], pose[state]);
	}

	/** Roots up the ground at its snout: sometimes scuffs grass to coarse dirt, sometimes turns something up. */
	void finishRooting(ServerLevel level, BlockPos ground) {
		BlockState block = level.getBlockState(ground);
		if (!isBaby() && level.getGameRules().get(GameRules.MOB_GRIEFING)
				&& (block.is(Blocks.GRASS_BLOCK) || block.is(Blocks.DIRT))
				&& random.nextFloat() < 0.3f) {
			level.setBlockAndUpdate(ground, Blocks.COARSE_DIRT.defaultBlockState());
		}
		if (random.nextFloat() < (isBaby() ? 0.05f : 0.15f)) {
			dropFromGiftLootTable(level, ROOTING_LOOT, (l, stack) -> spawnAtLocation(l, stack, 0.2f));
			playSound(Boars.ROOT, 0.8f, 1.2f);
		}
	}

	static boolean canRootIn(BlockState block) {
		return block.is(BlockTags.DIRT) || block.is(BlockTags.ANIMALS_SPAWNABLE_ON);
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		if (isTusker()) {
			spawnAtLocation(level, new ItemStack(Boars.BOAR_TUSK, 2));
		}
	}

	// ------------------------------------------------------------------------------------------------- spawning

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
			@Nullable SpawnGroupData groupData) {
		if (groupData == null) {
			// A group: the first an adult (sometimes a tusker), the rest about half piglets.
			groupData = new AgeableMobGroupData(0.5f);
			boolean natural = reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION;
			if (random.nextFloat() < (natural ? TUSKER_CHANCE : TUSKER_EGG_CHANCE)) {
				makeTusker();
			}
		}
		return super.finalizeSpawn(level, difficulty, reason, groupData);
	}

	/** On grass or bare earth (podzol, coarse dirt, moss...), in daylight, as animals spawn. */
	public static boolean checkBoarSpawnRules(EntityType<Boar> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos,
			RandomSource random) {
		BlockState below = level.getBlockState(pos.below());
		boolean bright = EntitySpawnReason.ignoresLightRequirements(reason) || level.getRawBrightness(pos, 0) > 8;
		return (below.is(BlockTags.ANIMALS_SPAWNABLE_ON) || below.is(BlockTags.DIRT)) && bright;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("Tusker", isTusker());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		entityData.set(DATA_TUSKER, input.getBooleanOr("Tusker", false));
	}

	// ------------------------------------------------------------------------------------------------- sounds

	@Override
	protected SoundEvent getAmbientSound() {
		return getTarget() != null ? Boars.ANGRY : Boars.AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return Boars.HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return Boars.DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState block) {
		playSound(Boars.STEP, 0.15f, 1.0f);
	}

	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * (isTusker() ? 0.85f : 1.0f);
	}

	@Override
	public boolean canBeLeashed() {
		return !isAggressive();
	}

	@Override
	public boolean canAttack(LivingEntity target) {
		return !isBaby() && super.canAttack(target);
	}

	/** Whether it's a boar (other than this one): charges never hurt their own. */
	static boolean isKin(Entity entity) {
		return entity instanceof Boar;
	}
}
