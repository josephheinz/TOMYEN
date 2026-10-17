package com.jheinz.tomyen.banshee;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.jheinz.tomyen.ModDamageTypes;
import com.jheinz.tomyen.holy.HolyLight;
import com.jheinz.tomyen.silver.EvilBane;
import com.jheinz.tomyen.starforge.Infusion;
import com.jheinz.tomyen.starforge.InfusionEffects;

/**
 * The Banshee: the ghost of a wailing woman, floating {@value #HOVER_HEIGHT} of a block off the ground with her gown
 * trailing away into wisps. She drifts through leaves as if they weren't there (and down through the canopy to the
 * forest floor), and over water.
 *
 * <p>She fights with a chill touch (a little damage and Slowness) and, every so often, her wail (see
 * {@link BansheeWailGoal}): a long indrawn breath as her jaw drops and her hair lifts, then a scream heard
 * {@value #WAIL_SOUND_RANGE} blocks away that blinds and darkens every player within {@value #WAIL_RANGE} blocks and
 * rouses the evil within {@value #ROUSE_RANGE} (the undead and anything in {@code #tomyen:evil}): they get a burst of
 * speed and come for her quarry.
 *
 * <p>She's a ghost: anything that isn't silver or holy does only {@code 1 - }{@link #GHOST_RESISTANCE} of its damage.
 * Anything holy (crucifix light, a holy silver blade or round, a Consecrated weapon) banishes her outright, whatever
 * her health, with a last scream as she comes apart. She fades away at sunrise if the sky can see her.
 */
public class Banshee extends Monster implements EvilBane.Wary {
	public static final double HOVER_HEIGHT = 0.5;
	/** How much of an ordinary blow passes through her. */
	public static final float GHOST_RESISTANCE = 0.5f;
	/** Players this close when she wails are blinded. */
	public static final double WAIL_RANGE = 16.0;
	/** The evil this close when she wails come running. */
	public static final double ROUSE_RANGE = 24.0;
	public static final float WAIL_SOUND_RANGE = 96.0f;
	/** The indrawn breath before the wail: her warning. */
	public static final int WINDUP_TICKS = 30;
	public static final int SCREAM_TICKS = 24;
	/** Natural spawns need no other banshee this close. */
	static final double CROWD_RADIUS = 64.0;
	private static final int GROUND_SEARCH = 12;
	public static final int DEATH_TICKS = 30;
	public static final int BANISH_TICKS = 16;

	public static final int IDLE = 0;
	public static final int WINDUP = 1;
	public static final int SCREAMING = 2;

	private static final int BLINDNESS_TICKS = 80;
	private static final int DARKNESS_TICKS = 160;
	private static final int ROUSED_TICKS = 200;
	private static final int CHILL_TICKS = 60;
	/** The flash as she's banished: holy light's pale gold. */
	private static final int BANISH_FLASH = 0xFFFFF3C4;

	private static final EntityDataAccessor<Byte> DATA_WAIL = SynchedEntityData.defineId(Banshee.class, EntityDataSerializers.BYTE);
	/** Struck by something holy: she dies coming apart rather than fading. */
	private static final EntityDataAccessor<Boolean> DATA_BANISHED = SynchedEntityData.defineId(Banshee.class, EntityDataSerializers.BOOLEAN);

	/** Ticks until she can wail again (server side). */
	private int wailCooldown;
	/** A silver round, holy or not, is about to hit (see EvilBane.Wary); used up by the next hit. */
	private boolean silverIncoming;
	private boolean holyIncoming;
	// Client side: how far through the wind-up she is, and how hard she's screaming, 0 to 1.
	private float windup;
	private float windupO;
	private float scream;
	private float screamO;

	public Banshee(EntityType<? extends Banshee> type, Level level) {
		super(type, level);
		xpReward = 20;
		wailCooldown = 60 + random.nextInt(60);
		setNoGravity(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 36.0)
				.add(Attributes.ATTACK_DAMAGE, 3.0)
				.add(Attributes.MOVEMENT_SPEED, 0.26)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.5)
				.add(Attributes.FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_WAIL, (byte) IDLE);
		builder.define(DATA_BANISHED, false);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(1, new BansheeWailGoal(this));
		goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
		goalSelector.addGoal(5, new BansheeDriftGoal(this));
		goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
		targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, true));
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		return new BansheeNavigation(this, level);
	}

	/**
	 * Dark, at night, and no other banshee within {@link #CROWD_RADIUS} blocks; spawners and eggs skip all of that.
	 * (She isn't kept to the surface: a banshee in a cave is no less a banshee.)
	 */
	public static boolean checkSpawnRules(EntityType<Banshee> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos,
			RandomSource random) {
		if (EntitySpawnReason.isSpawner(reason)) {
			return checkMobSpawnRules(type, level, reason, pos, random);
		}
		return checkMonsterSpawnRules(type, level, reason, pos, random)
				&& level.getLevel().isDarkOutside()
				&& level.getEntitiesOfClass(Banshee.class, new AABB(pos).inflate(CROWD_RADIUS)).isEmpty();
	}

	// Only stays put while she's after someone (or named, which Mob handles).
	@Override
	public boolean removeWhenFarAway(double distanceSq) {
		return getTarget() == null && super.removeWhenFarAway(distanceSq);
	}

	// --- The wail --------------------------------------------------------------------------------------------------

	public int wailState() {
		return entityData.get(DATA_WAIL);
	}

	void setWailState(int state) {
		entityData.set(DATA_WAIL, (byte) state);
	}

	boolean canWail() {
		return wailCooldown <= 0;
	}

	void resetWailCooldown() {
		wailCooldown = 200 + random.nextInt(120);
	}

	/** Draws breath: the warning, heard a fair way off. */
	void startWindup() {
		setWailState(WINDUP);
		playSound(Banshees.WAIL_WINDUP, 2.0f, 0.9f + random.nextFloat() * 0.15f);
	}

	/**
	 * The scream: blinds the players near her, and rouses the evil around her to come for her quarry (or for the
	 * nearest player, if she has none).
	 */
	void scream(ServerLevel level) {
		setWailState(SCREAMING);
		level.playSound(null, getX(), getEyeY(), getZ(), Banshees.WAIL, SoundSource.HOSTILE, 4.0f, 0.95f + random.nextFloat() * 0.1f);
		Vec3 mouth = mouth();
		level.sendParticles(ParticleTypes.SONIC_BOOM, mouth.x, mouth.y, mouth.z, 1, 0.0, 0.0, 0.0, 0.0);
		shockRing(level, mouth, 0.6);

		for (Player player : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(WAIL_RANGE),
				p -> EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(p) && p.distanceToSqr(this) < WAIL_RANGE * WAIL_RANGE)) {
			player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLINDNESS_TICKS), this);
			player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, DARKNESS_TICKS), this);
		}

		LivingEntity quarry = getTarget();
		if (quarry == null || !quarry.isAlive()) {
			quarry = level.getNearestPlayer(getX(), getY(), getZ(), ROUSE_RANGE, EntitySelector.NO_CREATIVE_OR_SPECTATOR);
		}
		if (quarry == null) {
			return;
		}
		for (Mob mob : level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(ROUSE_RANGE), this::rouses)) {
			mob.setTarget(quarry);
			mob.addEffect(new MobEffectInstance(MobEffects.SPEED, ROUSED_TICKS, 1), this);
			mob.getNavigation().moveTo(quarry, 1.2);
			mob.playAmbientSound();
			level.sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getEyeY() + 0.3, mob.getZ(), 4, 0.25, 0.2, 0.25, 0.02);
		}
	}

	/** What the wail wakes: the evil within range, other than banshees and the Wither. */
	private boolean rouses(Mob mob) {
		return mob != this && mob.isAlive() && HolyLight.isEvil(mob) && !(mob instanceof Banshee) && !(mob instanceof WitherBoss)
				&& mob.distanceToSqr(this) < ROUSE_RANGE * ROUSE_RANGE;
	}

	/** A ring of souls flying out flat from the point. */
	private void shockRing(ServerLevel level, Vec3 at, double speed) {
		int count = 32;
		for (int i = 0; i < count; i++) {
			float angle = i * Mth.TWO_PI / count;
			// Count 0: one particle, moving along the offset.
			level.sendParticles(ParticleTypes.SCULK_SOUL, at.x, at.y, at.z, 0, Mth.cos(angle), 0.05, Mth.sin(angle), speed);
		}
	}

	/** About where her mouth is. */
	public Vec3 mouth() {
		float yaw = yBodyRot * Mth.DEG_TO_RAD;
		return new Vec3(getX() - Mth.sin(yaw) * 0.3, getY() + 1.75, getZ() + Mth.cos(yaw) * 0.3);
	}

	/** Client side: the wind-up, 0 to 1. */
	public float windupAmount(float partialTicks) {
		return Mth.lerp(partialTicks, windupO, windup);
	}

	/** Client side: the scream, 0 to 1. */
	public float screamAmount(float partialTicks) {
		return Mth.lerp(partialTicks, screamO, scream);
	}

	public boolean isBanished() {
		return entityData.get(DATA_BANISHED);
	}

	// --- Combat ----------------------------------------------------------------------------------------------------

	/** The chill touch: a cold, weak blow that slows. */
	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit) {
			playSound(Banshees.TOUCH, 1.0f, 0.9f + random.nextFloat() * 0.2f);
			if (target instanceof LivingEntity living) {
				living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, CHILL_TICKS, 1), this);
			}
			level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY(0.6), target.getZ(), 10, 0.25, 0.3, 0.25, 0.02);
		}
		return hit;
	}

	@Override
	public void struckBySilver() {
		silverIncoming = true;
	}

	@Override
	public void struckBy(EvilBane bane) {
		silverIncoming = true;
		holyIncoming = bane.holy();
	}

	/** Whether a blow is holy: crucifix light, a holy silver weapon, or a Consecrated one. */
	public static boolean isHoly(DamageSource source) {
		if (source.is(ModDamageTypes.HOLY_LIGHT)) {
			return true;
		}
		ItemStack weapon = source.getWeaponItem();
		EvilBane bane = weapon == null ? null : EvilBane.of(weapon);
		if (bane != null && bane.holy()) {
			return true;
		}
		InfusionEffects.Strike strike = InfusionEffects.strike(source);
		return strike != null && strike.infusion() == Infusion.CONSECRATED;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean holy = holyIncoming || isHoly(source);
		boolean silver = silverIncoming || EvilBane.isSilverOrHoly(source) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
		holyIncoming = false;
		silverIncoming = false;
		if (holy && !isDeadOrDying() && !isInvulnerableTo(level, source)) {
			return banish(level, source);
		}
		return super.hurtServer(level, source, silver ? damage : damage * (1.0f - GHOST_RESISTANCE));
	}

	/** One holy touch and she's gone, with a last scream as she comes apart. */
	private boolean banish(ServerLevel level, DamageSource source) {
		entityData.set(DATA_BANISHED, true);
		// Far more than she has, so that armor, resistance or a recent hit can't leave her standing.
		boolean hurt = super.hurtServer(level, source, getMaxHealth() * 100.0f);
		if (!hurt || !isDeadOrDying()) {
			entityData.set(DATA_BANISHED, false);
			return hurt;
		}
		setWailState(SCREAMING);
		level.playSound(null, getX(), getEyeY(), getZ(), Banshees.WAIL, SoundSource.HOSTILE, 4.0f, 1.35f);
		Vec3 mouth = mouth();
		level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, BANISH_FLASH), mouth.x, mouth.y, mouth.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, getX(), getY(0.6), getZ(), 30, 0.3, 0.6, 0.3, 0.15);
		shockRing(level, mouth, 0.9);
		return true;
	}

	// --- Floating --------------------------------------------------------------------------------------------------

	/**
	 * Eases up or down to her hover height over the ground here or just ahead (so she rises over steps and walls before
	 * she reaches them), bobbing a little.
	 */
	private void hover() {
		Vec3 motion = getDeltaMovement();
		Vec3 ahead = new Vec3(motion.x, 0.0, motion.z);
		double ground = groundBelow(getX(), getZ());
		if (ahead.lengthSqr() > 1.0e-4) {
			ahead = ahead.normalize().scale(0.8);
			ground = Math.max(ground, groundBelow(getX() + ahead.x, getZ() + ahead.z));
		}
		int state = wailState();
		double height = HOVER_HEIGHT + 0.1 * Mth.sin(tickCount * 0.08f) + (state == IDLE ? 0.0 : 0.15);
		double rise = Mth.clamp((ground + height - getY()) * 0.15, -0.12, 0.2);
		setDeltaMovement(motion.x, rise, motion.z);
		resetFallDistance();
	}

	/** The top of the first solid block below, ignoring leaves (she sinks through them) and counting water's surface. */
	private double groundBelow(double x, double z) {
		BlockPos.MutableBlockPos pos = BlockPos.containing(x, getY() + 1.0, z).mutable();
		for (int i = 0; i <= GROUND_SEARCH; i++) {
			BlockState state = level().getBlockState(pos);
			if (!state.getFluidState().isEmpty()) {
				return pos.getY() + 0.9;
			}
			VoxelShape shape = state.getCollisionShape(level(), pos);
			if (!shape.isEmpty() && !state.is(BlockTags.LEAVES)) {
				return pos.getY() + shape.max(Direction.Axis.Y);
			}
			pos.move(Direction.DOWN);
		}
		return getY() - GROUND_SEARCH;
	}

	/** Whether all she'd bump into this tick is leaves, which she passes straight through. */
	private boolean onlyLeavesAhead() {
		AABB box = getBoundingBox().expandTowards(getDeltaMovement()).deflate(1.0e-3);
		VoxelShape swept = Shapes.create(box);
		boolean leaves = false;
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
				BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
			BlockState state = level().getBlockState(pos);
			VoxelShape shape = state.getCollisionShape(level(), pos);
			if (shape.isEmpty() || !Shapes.joinIsNotEmpty(shape.move(pos), swept, BooleanOp.AND)) {
				continue;
			}
			if (!state.is(BlockTags.LEAVES)) {
				return false;
			}
			leaves = true;
		}
		return leaves;
	}

	// Floating, she only gets a sliver of her speed in the air; give her about her walking pace.
	@Override
	protected float getFlyingSpeed() {
		return getSpeed() * 0.25f;
	}

	@Override
	public void aiStep() {
		if (!level().isClientSide() && isAlive()) {
			hover();
		}
		super.aiStep();
		if (level() instanceof ServerLevel level && isAlive()) {
			// A round is announced just before it lands, in the same tick; don't let a stray warning carry over.
			silverIncoming = false;
			holyIncoming = false;
			if (wailCooldown > 0 && wailState() == IDLE) {
				wailCooldown--;
			}
			if (tickCount % 20 == 0 && level.isBrightOutside() && level.canSeeSky(BlockPos.containing(getX(), getEyeY(), getZ()))
					&& !requiresCustomPersistence()) {
				fadeAtDawn(level);
			}
		}
	}

	/** Sunrise finds her under the open sky: she sighs and is gone (no drops). */
	private void fadeAtDawn(ServerLevel level) {
		level.sendParticles(ParticleTypes.SOUL, getX(), getY(0.5), getZ(), 16, 0.3, 0.6, 0.3, 0.03);
		level.sendParticles(ParticleTypes.POOF, getX(), getY(0.5), getZ(), 10, 0.3, 0.6, 0.3, 0.02);
		level.playSound(null, getX(), getY(), getZ(), Banshees.AMBIENT, SoundSource.HOSTILE, 1.0f, 0.7f);
		discard();
	}

	@Override
	public void tick() {
		noPhysics = isAlive() && !level().isClientSide() && onlyLeavesAhead();
		super.tick();
		noPhysics = false;
		if (level().isClientSide()) {
			windupO = windup;
			screamO = scream;
			int state = wailState();
			windup = Mth.approach(windup, state == IDLE ? 0.0f : 1.0f, state == IDLE ? 0.08f : 1.0f / WINDUP_TICKS);
			scream = Mth.approach(scream, state == SCREAMING ? 1.0f : 0.0f, state == SCREAMING ? 0.3f : 0.06f);
			if (isAlive() && random.nextInt(12) == 0) {
				// Cold motes coming off her hem.
				level().addParticle(ParticleTypes.SNOWFLAKE, getRandomX(0.6), getY() + random.nextDouble() * 0.4, getRandomZ(0.6),
						0.0, -0.01, 0.0);
			}
			if (state == SCREAMING && random.nextInt(2) == 0) {
				Vec3 mouth = mouth();
				level().addParticle(ParticleTypes.SCULK_SOUL, mouth.x, mouth.y, mouth.z, (random.nextDouble() - 0.5) * 0.2, 0.05,
						(random.nextDouble() - 0.5) * 0.2);
			}
		}
	}

	/** Fades and sinks over {@link #DEATH_TICKS} ticks; banished, she flies apart in {@link #BANISH_TICKS}. */
	@Override
	protected void tickDeath() {
		deathTime++;
		boolean banished = isBanished();
		if (level() instanceof ServerLevel level) {
			if (banished) {
				level.sendParticles(ParticleTypes.END_ROD, getX(), getY(0.6), getZ(), 3, 0.3, 0.5, 0.3, 0.08);
				level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY(0.6), getZ(), 2, 0.3, 0.5, 0.3, 0.04);
			} else if (deathTime % 3 == 0) {
				level.sendParticles(ParticleTypes.SOUL, getX(), getY(0.4), getZ(), 1, 0.3, 0.4, 0.3, 0.02);
			}
			if (deathTime >= (banished ? BANISH_TICKS : DEATH_TICKS) && !isRemoved()) {
				level.broadcastEntityEvent(this, (byte) 60);
				remove(RemovalReason.KILLED);
			}
		}
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	@Override
	protected void checkFallDamage(double fallY, boolean onGround, BlockState onState, BlockPos pos) {
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource source) {
		return false;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return wailState() == IDLE ? Banshees.AMBIENT : null;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 200;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return Banshees.HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return isBanished() ? Banshees.BANISHED : Banshees.DEATH;
	}
}
