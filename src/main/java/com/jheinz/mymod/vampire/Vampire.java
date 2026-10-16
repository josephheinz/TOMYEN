package com.jheinz.mymod.vampire;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FleeSunGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RestrictSunGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.jheinz.mymod.ModEffects;
import com.jheinz.mymod.herbalism.HerbalWards;
import com.jheinz.mymod.holy.CrucifixItem;
import com.jheinz.mymod.holy.LightOrb;

/**
 * A vampire: undead, so it burns in daylight (the entity type tags do that) and holy light hurts it. It
 * creeps up behind its prey and bites, which starts it bleeding, then backs off to circle round again.
 * In the dark it's all but invisible, just a pair of glowing red eyes; light shows it up (see
 * {@link #targetVisibility}). At night it floats a couple of blocks off the ground, swooping down to bite.
 */
public class Vampire extends Monster {
	/** Light level at or below which it can't be seen at all. */
	public static final int DARK_LIGHT = 3;
	/** Light it needs to show fully. */
	private static final float FULL_LIGHT = 9.0f;
	/** Natural spawns, and ones called by blood, need at most this much light. */
	public static final int MAX_SPAWN_LIGHT = 7;
	private static final int BLEED_TICKS = 200;
	/** How long it shows after something lights it up: a hit, a beam of light passing close. */
	private static final int REVEAL_TICKS = 40;
	/** How far a light orb lights it up from. */
	private static final double ORB_GLOW_RADIUS = 7.0;
	/** How high it floats at night, in blocks above the ground. */
	private static final double HOVER_HEIGHT = 2.0;
	/** How far down it looks for ground to float over; past a drop that big it sinks. */
	private static final int HOVER_GROUND_SEARCH = 6;

	private static final EntityDataAccessor<Boolean> DATA_STALKING = SynchedEntityData.defineId(Vampire.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_REVEALED = SynchedEntityData.defineId(Vampire.class, EntityDataSerializers.BOOLEAN);

	private int revealTicks;
	/** Diving down to bite while floating. */
	private boolean swooping;
	/** Ticks until it can hiss at a crucifix again. */
	private int wardHissCooldown;
	// Client side: how much of it shows, 0 (just eyes) to 1.
	private float visibility;
	private float visibilityO;

	public Vampire(EntityType<? extends Vampire> type, Level level) {
		super(type, level);
		xpReward = 10;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 32.0)
				.add(Attributes.MOVEMENT_SPEED, 0.28)
				.add(Attributes.ATTACK_DAMAGE, 4.0)
				.add(Attributes.ARMOR, 2.0)
				.add(Attributes.FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_STALKING, false);
		builder.define(DATA_REVEALED, false);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new VampireHuntGoal(this));
		goalSelector.addGoal(2, new RestrictSunGoal(this));
		goalSelector.addGoal(3, new FleeSunGoal(this, 1.2));
		goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.9));
		goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
		targetSelector.addGoal(2, new BloodScent.Goal<>(this, Player.class));
		targetSelector.addGoal(3, new BloodScent.Goal<>(this, AbstractVillager.class));
		targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Player.class, true));
		targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, true));
	}

	public static boolean checkSpawnRules(EntityType<Vampire> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos,
			RandomSource random) {
		return checkMonsterSpawnRules(type, level, reason, pos, random)
				&& (EntitySpawnReason.isSpawner(reason) || level.getMaxLocalRawBrightness(pos) <= MAX_SPAWN_LIGHT);
	}

	/** Creeping up on someone: crouched and silent. */
	public boolean isStalking() {
		return entityData.get(DATA_STALKING);
	}

	void setStalking(boolean stalking) {
		entityData.set(DATA_STALKING, stalking);
	}

	/** Shows it in full for a moment, whatever the light. */
	public void reveal() {
		revealTicks = REVEAL_TICKS;
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		if (target instanceof LivingEntity living && CrucifixItem.isHeldBy(living)) {
			return false;
		}
		boolean hit = super.doHurtTarget(level, target);
		if (hit) {
			if (target instanceof LivingEntity living) {
				living.addEffect(new MobEffectInstance(ModEffects.BLEEDING, BLEED_TICKS), this);
			}
			heal(2.0f);
			playSound(SoundEvents.PHANTOM_BITE, 1.0f, 0.8f + random.nextFloat() * 0.2f);
		}
		return hit;
	}

	/** A crucifix holder this close (within its ward), or null. */
	public @Nullable Player wardingPlayer(double margin) {
		double radius = CrucifixItem.WARD_RADIUS + margin;
		for (Player player : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(radius + 1.0))) {
			if (!player.isSpectator() && player.isAlive() && CrucifixItem.isHeldBy(player) && distanceTo(player) < radius) {
				return player;
			}
		}
		return null;
	}

	/** Shoves it back out to the edge of any crucifix's ward it has got inside, hissing and smoking. */
	private void keepOutOfWards() {
		if (wardHissCooldown > 0) {
			wardHissCooldown--;
		}
		Player holder = wardingPlayer(0.0);
		if (holder == null) {
			return;
		}
		Vec3 away = new Vec3(getX() - holder.getX(), 0.0, getZ() - holder.getZ());
		if (away.lengthSqr() < 1.0E-4) {
			float angle = random.nextFloat() * Mth.TWO_PI;
			away = new Vec3(Mth.cos(angle), 0.0, Mth.sin(angle));
		}
		away = away.normalize();
		double depth = CrucifixItem.WARD_RADIUS - distanceTo(holder);
		// Step straight out (blocked by walls like any move), and keep drifting away so it doesn't hover on the edge.
		move(MoverType.SELF, away.scale(depth + 0.05));
		setDeltaMovement(away.x * 0.3, getDeltaMovement().y, away.z * 0.3);
		swooping = false;
		if (wardHissCooldown == 0 && level() instanceof ServerLevel level) {
			playSound(SoundEvents.FIRE_EXTINGUISH, 0.6f, 0.6f + random.nextFloat() * 0.2f);
			level.sendParticles(ParticleTypes.SMOKE, getX(), getY(0.6), getZ(), 10, 0.25, 0.4, 0.25, 0.02);
			wardHissCooldown = 20;
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt) {
			reveal();
		}
		return hurt;
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		return new HoverNavigation(this, level);
	}

	void setSwooping(boolean swooping) {
		this.swooping = swooping;
	}

	/** Floating above the ground, which it does at night. Gravity is off while it floats (synced to clients). */
	public boolean isHovering() {
		return isNoGravity();
	}

	private void updateHover() {
		boolean hover = isAlive() && level().isDarkOutside() && !isInLiquid() && !isPassenger() && !isLeashed();
		if (hover != isNoGravity()) {
			setNoGravity(hover);
		}
		if (!hover) {
			return;
		}
		resetFallDistance();
		double height = swooping ? 0.0 : HOVER_HEIGHT + 0.15 * Mth.sin(tickCount * 0.08f);
		double rise = Mth.clamp((groundBelow() + height - getY()) * 0.12, -0.2, 0.2);
		Vec3 motion = getDeltaMovement();
		setDeltaMovement(motion.x, rise, motion.z);
	}

	/** The top of the first solid block under it, searching a few blocks down. */
	private double groundBelow() {
		BlockPos.MutableBlockPos pos = blockPosition().mutable();
		for (int i = 0; i <= HOVER_GROUND_SEARCH; i++) {
			VoxelShape shape = level().getBlockState(pos).getCollisionShape(level(), pos);
			if (!shape.isEmpty()) {
				return pos.getY() + shape.max(Direction.Axis.Y);
			}
			pos.move(Direction.DOWN);
		}
		return getY() - HOVER_GROUND_SEARCH;
	}

	// In the air, mobs only get a sliver of their speed; give a floating vampire about its walking speed.
	@Override
	protected float getFlyingSpeed() {
		return isHovering() ? getSpeed() * 0.2f : super.getFlyingSpeed();
	}

	@Override
	public void aiStep() {
		if (!level().isClientSide()) {
			updateHover();
		}
		super.aiStep();
		if (!level().isClientSide()) {
			keepOutOfWards();
			HerbalWards.keepVampireOut(this);
			if (revealTicks > 0) {
				revealTicks--;
			}
			entityData.set(DATA_REVEALED, revealTicks > 0 || isOnFire());
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			visibilityO = visibility;
			float target = targetVisibility();
			// Fades in quickly when lit, slips back into the dark more slowly.
			visibility = Mth.approach(visibility, target, target > visibility ? 0.15f : 0.05f);
		}
	}

	/**
	 * How much should show in this light: nothing at {@link #DARK_LIGHT} or below, rising to fully visible at
	 * {@link #FULL_LIGHT}. Light orbs and someone charging a crucifix nearby count as light too.
	 */
	private float targetVisibility() {
		if (entityData.get(DATA_REVEALED)) {
			return 1.0f;
		}
		float light = level().getMaxLocalRawBrightness(blockPosition());
		for (LightOrb orb : level().getEntitiesOfClass(LightOrb.class, getBoundingBox().inflate(ORB_GLOW_RADIUS))) {
			light = Math.max(light, 15.0f - 1.6f * (float) orb.distanceTo(this));
		}
		for (Player player : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(6.0))) {
			if (player.getUseItem().getItem() instanceof CrucifixItem) {
				light = Math.max(light, 12.0f - 1.5f * (float) player.distanceTo(this));
			}
		}
		float amount = Mth.clamp((light - DARK_LIGHT) / (FULL_LIGHT - DARK_LIGHT), 0.0f, 1.0f);
		return amount * Mth.sqrt(amount);
	}

	public float visibility(float partialTicks) {
		return Mth.lerp(partialTicks, visibilityO, visibility);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		// Silent while it creeps up on someone.
		return isStalking() ? null : SoundEvents.EVOKER_AMBIENT;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 240;
	}

	@Override
	public float getVoicePitch() {
		return 0.55f + random.nextFloat() * 0.1f;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.PHANTOM_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.PHANTOM_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		if (!isStalking()) {
			super.playStepSound(pos, state);
		}
	}
}
