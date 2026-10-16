package com.jheinz.tomyen.cathode;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.PotionTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.herbalism.Herbalism;

/**
 * A Cathode Walker: a wood-veneer CRT television with rabbit-ear antennas, strutting about on a pair of bare human
 * legs. Its screen is its face: grey static while it wanders, a grinning pixel face once it's hunting, colour bars
 * when it's hurt (see client/cathode/CathodeWalkerRenderer).
 *
 * <p>It struts quickly, high-kneed, sprinting once it has a target, and kicks hard ({@link Attributes#ATTACK_KNOCKBACK}
 * sends you flying). Every {@value #BURST_COOLDOWN}-odd ticks, with its target within {@value #BURST_RANGE} blocks and
 * in sight, it stops and its screen brightens to white for {@value #CHARGE_TICKS} ticks (the tell: look away), then
 * goes off in a Static Burst: a roar of static, and every player within range who's facing it (the way an enderman
 * checks, but a wide cone) gets a burst of TV snow over their screen ({@link StaticBurstPayload}) and a moment's
 * slowness. Harmless, but disorienting.
 *
 * <p>Water shorts it out: it avoids water, and in rain it sparks and takes {@value #RAIN_DAMAGE} damage a second, in
 * water {@value #WATER_DAMAGE} every half second, and a splash of water {@value #SPLASH_DAMAGE}. It won't spawn out in
 * the rain. Dying, its screen implodes with a pop and a burst of glass, then clicks off down to a shrinking white dot.
 */
public class CathodeWalker extends Monster {
	public static final double BURST_RANGE = 8.0;
	static final int BURST_COOLDOWN = 200;
	public static final int CHARGE_TICKS = 16;
	/** How long the static fills a dazzled player's screen. */
	public static final int OVERLAY_TICKS = 40;
	private static final int SLOWNESS_TICKS = 40;
	/** How wide a cone a player has to be looking along to be caught by a burst (1 minus the cosine of its half-angle). */
	private static final double GAZE_CONE = 0.35;
	static final float RAIN_DAMAGE = 1.0f;
	static final float WATER_DAMAGE = 2.0f;
	static final float SPLASH_DAMAGE = 4.0f;
	/** Sprinting after its target, as a multiple of its walking speed. */
	private static final double SPRINT = 1.4;
	/** A kick's swing: slower than an arm's, so the wind-up and snap read. */
	private static final SwingAnimation KICK = new SwingAnimation(SwingAnimationType.WHACK, 10);

	/** Death: the screen implodes on the first tick, and the set clicks off on this one. */
	public static final int CLICK_OFF_TICK = 4;
	/** Sent when a Static Burst goes off, so the screen flashes white. */
	private static final byte BURST_EVENT = 100;
	/** Sent when it shorts out, for a shower of sparks. */
	private static final byte SHORT_EVENT = 101;

	public static final int IDLE = 0;
	public static final int HUNTING = 1;
	/** Brightening for a Static Burst. */
	public static final int CHARGING = 2;

	private static final EntityDataAccessor<Byte> DATA_MODE = SynchedEntityData.defineId(CathodeWalker.class, EntityDataSerializers.BYTE);

	// Server side.
	private int burstCooldown;
	private int shortCooldown;
	// Client side: ticks of white flash left after a burst, and how far its screen has brightened charging one (0 to 1).
	private int flashTicks;
	private float charge;
	private float chargeO;

	public CathodeWalker(EntityType<? extends CathodeWalker> type, Level level) {
		super(type, level);
		xpReward = 6;
		burstCooldown = BURST_COOLDOWN / 2 + random.nextInt(BURST_COOLDOWN / 2);
		setPathfindingMalus(PathType.WATER, -1.0f);
		setPathfindingMalus(PathType.WATER_BORDER, 16.0f);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 24.0)
				.add(Attributes.ARMOR, 2.0)
				.add(Attributes.ATTACK_DAMAGE, 4.0)
				.add(Attributes.ATTACK_KNOCKBACK, 1.6)
				.add(Attributes.MOVEMENT_SPEED, 0.26)
				.add(Attributes.FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_MODE, (byte) IDLE);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new StaticBurstGoal(this));
		goalSelector.addGoal(2, new MeleeAttackGoal(this, SPRINT, false));
		goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1.0));
		goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
		targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, false));
		targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
	}

	/** A dark-enough spot, as for any monster, but never out in the rain (it would only short out). */
	public static boolean checkSpawnRules(EntityType<CathodeWalker> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos,
			RandomSource random) {
		return checkMonsterSpawnRules(type, level, reason, pos, random)
				&& (EntitySpawnReason.isSpawner(reason) || !level.getLevel().isRainingAt(pos.above()));
	}

	// ------------------------------------------------------------------------------------------------ state

	public int mode() {
		return entityData.get(DATA_MODE);
	}

	private void setMode(int mode) {
		if (mode() != mode) {
			entityData.set(DATA_MODE, (byte) mode);
		}
	}

	/** Client side: ticks left of the white flash after a burst. */
	public int flashTicks() {
		return flashTicks;
	}

	/** Client side: how far its screen has brightened charging a burst, 0 to 1. */
	public float charge(float partialTicks) {
		return Mth.lerp(partialTicks, chargeO, charge);
	}

	// ------------------------------------------------------------------------------------------------ ticking

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel) || !isAlive()) {
			return;
		}
		shortCooldown = Math.max(0, shortCooldown - 1);
		burstCooldown = Math.max(0, burstCooldown - 1);
		if (mode() != CHARGING) {
			LivingEntity target = getTarget();
			setMode(target != null && target.isAlive() ? HUNTING : IDLE);
		}
	}

	/** Its target, if it's alive, in burst range and in sight, and the burst is off cooldown. */
	private @Nullable LivingEntity burstTarget() {
		LivingEntity target = getTarget();
		return burstCooldown == 0 && target != null && target.isAlive() && distanceToSqr(target) < BURST_RANGE * BURST_RANGE
				&& getSensing().hasLineOfSight(target) ? target : null;
	}

	/**
	 * Charging a Static Burst: it stops dead (pre-empting the chase and the kicks), stares at its target while its
	 * screen brightens for {@link #CHARGE_TICKS} ticks, then goes off.
	 */
	private static final class StaticBurstGoal extends Goal {
		private final CathodeWalker walker;
		private int ticks;

		StaticBurstGoal(CathodeWalker walker) {
			this.walker = walker;
			setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return walker.burstTarget() != null;
		}

		@Override
		public boolean canContinueToUse() {
			return ticks > 0 && walker.isAlive();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void start() {
			ticks = CHARGE_TICKS;
			walker.getNavigation().stop();
			walker.setMode(CHARGING);
			walker.playSound(CathodeWalkers.CHARGE, 1.0f, 1.0f);
		}

		@Override
		public void tick() {
			LivingEntity target = walker.getTarget();
			if (target != null) {
				walker.getLookControl().setLookAt(target, 30.0f, 30.0f);
			}
			if (--ticks == 0 && walker.level() instanceof ServerLevel level) {
				walker.staticBurst(level);
			}
		}

		@Override
		public void stop() {
			if (ticks > 0) {
				// Cut short (its target's gone): a moment before it tries again.
				walker.burstCooldown = Math.max(walker.burstCooldown, 40);
			}
			ticks = 0;
			if (walker.mode() == CHARGING) {
				walker.setMode(walker.getTarget() != null ? HUNTING : IDLE);
			}
		}
	}

	/** Goes off: a roar of static, and snow over the screen of every player in range who's looking its way. */
	private void staticBurst(ServerLevel level) {
		burstCooldown = BURST_COOLDOWN + random.nextInt(BURST_COOLDOWN / 2);
		setMode(getTarget() != null ? HUNTING : IDLE);
		level.broadcastEntityEvent(this, BURST_EVENT);
		playSound(CathodeWalkers.STATIC_BURST, 2.0f, 0.9f + random.nextFloat() * 0.2f);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY(0.8), getZ(), 12, 0.4, 0.3, 0.4, 0.15);
		double screenY = getY(0.8);
		for (Player player : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(BURST_RANGE))) {
			if (!(player instanceof ServerPlayer watcher) || player.isSpectator() || player.distanceToSqr(this) > BURST_RANGE * BURST_RANGE
					|| !isLookingAtMe(player, GAZE_CONE, false, true, screenY, getEyeY()) || Herbalism.isCalm(player)) {
				continue;
			}
			ServerPlayNetworking.send(watcher, new StaticBurstPayload(OVERLAY_TICKS));
			if (!player.getAbilities().instabuild) {
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOWNESS_TICKS, 0), this);
			}
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide()) {
			return;
		}
		if (flashTicks > 0) {
			flashTicks--;
		}
		chargeO = charge;
		boolean charging = isAlive() && mode() == CHARGING;
		charge = Mth.approach(charge, charging ? 1.0f : 0.0f, charging ? 1.0f / CHARGE_TICKS : 0.34f);
		if (isAlive() && mode() == CHARGING && random.nextInt(3) == 0) {
			level().addParticle(ParticleTypes.ELECTRIC_SPARK, getRandomX(0.5), getY(0.55 + random.nextDouble() * 0.4), getRandomZ(0.5),
					0.0, 0.05, 0.0);
		}
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == BURST_EVENT) {
			flashTicks = 6;
		} else if (id == SHORT_EVENT) {
			for (int i = 0; i < 8; i++) {
				level().addParticle(ParticleTypes.ELECTRIC_SPARK, getRandomX(0.6), getY(0.45 + random.nextDouble() * 0.5), getRandomZ(0.6),
						random.nextGaussian() * 0.1, 0.1, random.nextGaussian() * 0.1);
			}
			level().addParticle(ParticleTypes.SMOKE, getX(), getY(1.0), getZ(), 0.0, 0.05, 0.0);
		} else {
			super.handleEntityEvent(id);
		}
	}

	// ------------------------------------------------------------------------------------------------ water

	/** Rain and water short it out (see {@link #hurtServer}), and splash water hurts it, as it does an enderman. */
	@Override
	public boolean isSensitiveToWater() {
		return true;
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	/**
	 * Vanilla hurts a water-sensitive mob with drowning damage every tick it's wet; that's swapped for a short circuit
	 * (sparks, a fizz and {@link #RAIN_DAMAGE} a second in rain, {@link #WATER_DAMAGE} every half second in water). A
	 * splash of water does {@link #SPLASH_DAMAGE}.
	 */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (source.is(DamageTypes.DROWN)) {
			if (shortCooldown > 0) {
				return false;
			}
			boolean soaked = isInWater();
			shortCooldown = soaked ? 10 : 20;
			level.broadcastEntityEvent(this, SHORT_EVENT);
			playSound(CathodeWalkers.SHORT, 0.8f, 0.9f + random.nextFloat() * 0.3f);
			return super.hurtServer(level, damageSources().source(CathodeWalkers.SHORT_CIRCUIT), soaked ? WATER_DAMAGE : RAIN_DAMAGE);
		}
		if (source.is(DamageTypes.INDIRECT_MAGIC) && source.getDirectEntity() instanceof AbstractThrownPotion splash
				&& splash.getItem().getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(PotionTags.HURTS_WATER_SENSITIVE_ENTITIES)) {
			level.broadcastEntityEvent(this, SHORT_EVENT);
			playSound(CathodeWalkers.SHORT, 1.0f, 0.8f);
			return super.hurtServer(level, source, SPLASH_DAMAGE);
		}
		return super.hurtServer(level, source, damage);
	}

	// ------------------------------------------------------------------------------------------------ fighting

	@Override
	public void swingForAttack(InteractionHand hand) {
		swing(hand, KICK, false);
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit) {
			// A kick lifts as well as shoves.
			target.setDeltaMovement(target.getDeltaMovement().add(0.0, 0.25, 0.0));
			target.needsSync = true;
		}
		return hit;
	}

	@Override
	protected void playAttackSound() {
		playSound(CathodeWalkers.KICK, 1.0f, 0.9f + random.nextFloat() * 0.2f);
	}

	// ------------------------------------------------------------------------------------------------ dying

	@Override
	protected void tickDeath() {
		if (level() instanceof ServerLevel level) {
			if (deathTime == 0) {
				// The screen implodes: a pop, a burst of glass out of the front of it.
				setMode(IDLE);
				Vec3 front = Vec3.directionFromRotation(0.0f, yBodyRot).scale(0.45);
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState()), getX() + front.x, getY(0.75),
						getZ() + front.z, 24, 0.2, 0.2, 0.2, 0.15);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX() + front.x, getY(0.75), getZ() + front.z, 10, 0.15, 0.15, 0.15, 0.2);
				playSound(CathodeWalkers.POP, 1.2f, 1.0f);
			} else if (deathTime == CLICK_OFF_TICK) {
				playSound(CathodeWalkers.CLICK_OFF, 1.0f, 1.0f);
			} else if (deathTime % 5 == 0) {
				level.sendParticles(ParticleTypes.SMOKE, getX(), getY(0.6), getZ(), 2, 0.2, 0.15, 0.2, 0.01);
			}
		}
		super.tickDeath();
	}

	// ------------------------------------------------------------------------------------------------ sounds

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return CathodeWalkers.AMBIENT;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 160;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return CathodeWalkers.HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return CathodeWalkers.DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		playSound(CathodeWalkers.STEP, 0.6f, 0.9f + random.nextFloat() * 0.25f);
	}
}
