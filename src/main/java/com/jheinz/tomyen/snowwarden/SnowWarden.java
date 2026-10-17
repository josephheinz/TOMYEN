package com.jheinz.tomyen.snowwarden;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
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
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Snow Warden: a warden of packed snow and ice, three blocks tall, with a carved pumpkin for a head. It wanders the
 * snowy plains, leaving snow where it walks as a snow golem does, frost drifting off it.
 *
 * <p>The first time it notices someone it stops, sniffs the air with its ice tendrils twitching, and roars. Then it
 * fights (see {@link SnowWardenAttackGoal}): up close it swings its fists ({@value #SWING_TICKS}-tick swings, the
 * full weight of its {@code ATTACK_DAMAGE}), and when it's crowded, or now and then anyway, it raises both fists and
 * <b>slams</b> the ground, hurting, throwing back and slowing everyone within {@value #SLAM_RADIUS} blocks. Further off
 * it leans back, its chest crystals blazing, and fires a <b>frost blast</b> ({@link #frostBlast}): a beam of ice that a
 * shield only halves, freezing and slowing whoever it hits; or it lobs a {@link FrostBoulder}.
 *
 * <p>It's a boss: a health bar for everyone within {@value #BOSS_RANGE} blocks while it's fighting. Fire hurts it
 * {@value #FIRE_FACTOR} times as much, and anything hurts it more while it's burning; it can't freeze. Once it has gone
 * after a player, or been hurt by one, it stays put; until then it despawns like any monster when no one's near.
 *
 * <p>Natural spawns ({@link #checkSnowWardenSpawnRules}): out under the open sky, on snow, ice or grass, never within
 * {@value #LONER_RADIUS} blocks of another, and only one try in {@value #SPAWN_CHANCE} that gets that far.
 */
public class SnowWarden extends Monster {
	public static final byte IDLE = 0;
	public static final byte SWING_RIGHT = 1;
	public static final byte SWING_LEFT = 2;
	public static final byte SLAMMING = 3;
	/** Leaning back, drawing in the cold: the frost blast is coming. */
	public static final byte CHARGING = 4;
	public static final byte BLASTING = 5;
	public static final byte THROWING = 6;
	public static final byte SNIFFING = 7;
	public static final byte ROARING = 8;

	public static final int SWING_TICKS = 20;
	/** When in the swing the fist lands. */
	public static final int SWING_HITS = 12;
	public static final int SLAM_TICKS = 32;
	public static final int SLAM_LANDS = 19;
	public static final int CHARGE_TICKS = 36;
	public static final int BLAST_TICKS = 14;
	public static final int THROW_TICKS = 24;
	public static final int THROW_RELEASE = 16;
	public static final int SNIFF_TICKS = 36;
	public static final int ROAR_TICKS = 44;

	public static final double SLAM_RADIUS = 5.0;
	public static final double BLAST_RANGE = 24.0;
	public static final float FIRE_FACTOR = 3.0f;
	/** Anything else, while it's burning. */
	private static final float BURNING_FACTOR = 1.25f;
	private static final double BOSS_RANGE = 32.0;
	static final double LONER_RADIUS = 96.0;
	static final int SPAWN_CHANCE = 8;
	/** Its slam's share of a fist. */
	private static final float SLAM_FACTOR = 0.6f;
	private static final float BLAST_DAMAGE = 14.0f;
	/** A raised shield facing it takes this share off a frost blast (and the beam chips the shield). */
	private static final float SHIELD_BLOCKS = 0.5f;
	/** How far past fully frozen its chill can push someone. */
	private static final int CHILL_OVER = 100;
	/** No target this long, and it forgets it ever roared. */
	private static final int CALM_TICKS = 600;

	private static final EntityDataAccessor<Byte> DATA_ACTION = SynchedEntityData.defineId(SnowWarden.class, EntityDataSerializers.BYTE);

	private final ServerBossEvent bossEvent = new ServerBossEvent(java.util.UUID.randomUUID(), Component.translatable("entity.tomyen.snow_warden"),
			BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10);

	// Server side.
	/** It has gone after a player, or been hurt by one: it stays. */
	private boolean engaged;
	/** It has sniffed out and roared at someone already: no more ceremony until it calms down. */
	private boolean alerted;
	private int calmTicks;

	// Client side.
	private byte clientAction = IDLE;
	private int actionTicks;
	private float charge;
	private float chargeO;
	private float sniff;
	private float sniffO;

	public SnowWarden(EntityType<? extends SnowWarden> type, Level level) {
		super(type, level);
		xpReward = 50;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 200.0)
				.add(Attributes.ARMOR, 12.0)
				.add(Attributes.ARMOR_TOUGHNESS, 4.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
				.add(Attributes.MOVEMENT_SPEED, 0.27)
				.add(Attributes.ATTACK_DAMAGE, 20.0)
				.add(Attributes.ATTACK_KNOCKBACK, 1.5)
				.add(Attributes.FOLLOW_RANGE, 32.0)
				.add(Attributes.STEP_HEIGHT, 1.1);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ACTION, IDLE);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new SnowWardenAttackGoal(this));
		goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7));
		goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	// --- Spawning ---------------------------------------------------------------------------------------------

	/**
	 * Spawners and eggs as any mob; natural spawns (day or night: the cold is what it wants) only out under the sky on
	 * snow, ice or grass, alone for {@link #LONER_RADIUS} blocks, and one try in {@link #SPAWN_CHANCE}.
	 */
	public static boolean checkSnowWardenSpawnRules(EntityType<SnowWarden> type, ServerLevelAccessor level, EntitySpawnReason reason,
			BlockPos pos, RandomSource random) {
		if (!checkAnyLightMonsterSpawnRules(type, level, reason, pos, random)) {
			return false;
		}
		if (reason != EntitySpawnReason.NATURAL) {
			return true;
		}
		BlockState ground = level.getBlockState(pos.below());
		return random.nextInt(SPAWN_CHANCE) == 0
				&& level.canSeeSky(pos)
				&& (ground.is(BlockTags.SNOW) || ground.is(BlockTags.ICE) || ground.is(Blocks.GRASS_BLOCK))
				&& level.getEntitiesOfClass(SnowWarden.class, new AABB(pos).inflate(LONER_RADIUS)).isEmpty();
	}

	/** Its spawn predicate covers where it may stand: monsters' usual wish for darkness doesn't apply. */
	@Override
	public boolean checkSpawnRules(LevelAccessor level, EntitySpawnReason reason) {
		return true;
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
			@Nullable SpawnGroupData groupData) {
		alerted = false;
		return super.finalizeSpawn(level, difficulty, reason, groupData);
	}

	@Override
	public boolean removeWhenFarAway(double distanceSq) {
		return !engaged && super.removeWhenFarAway(distanceSq);
	}

	// --- State ------------------------------------------------------------------------------------------------

	public byte action() {
		return entityData.get(DATA_ACTION);
	}

	void setAction(byte action) {
		entityData.set(DATA_ACTION, action);
	}

	boolean isAlerted() {
		return alerted;
	}

	void setAlerted() {
		alerted = true;
		calmTicks = 0;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("Engaged", engaged);
		output.putBoolean("Alerted", alerted);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		engaged = input.getBooleanOr("Engaged", false);
		alerted = input.getBooleanOr("Alerted", false);
		if (hasCustomName()) {
			bossEvent.setName(getDisplayName());
		}
	}

	@Override
	public void setCustomName(@Nullable Component name) {
		super.setCustomName(name);
		bossEvent.setName(getDisplayName());
	}

	// --- Ticking ----------------------------------------------------------------------------------------------

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			clientTick();
		}
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (level() instanceof ServerLevel level) {
			leaveSnow(level);
		}
	}

	/** Snow where it walks, as a snow golem leaves, where it's cold enough for snow to lie. */
	private void leaveSnow(ServerLevel level) {
		if (!level.getGameRules().get(GameRules.MOB_GRIEFING) || (getX() == xo && getZ() == zo)) {
			return;
		}
		BlockState snow = Blocks.SNOW.defaultBlockState();
		for (int i = 0; i < 4; i++) {
			BlockPos pos = BlockPos.containing(getX() + ((i % 2) * 2 - 1) * 0.45, getY(), getZ() + ((i / 2) * 2 - 1) * 0.45);
			if (level.getBlockState(pos).isAir() && snow.canSurvive(level, pos)
					&& level.getBiome(pos).value().coldEnoughToSnow(pos, level.getSeaLevel())) {
				level.setBlockAndUpdate(pos, snow);
			}
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		LivingEntity target = getTarget();
		if (target instanceof Player) {
			engaged = true;
		}
		if (target == null && alerted && ++calmTicks > CALM_TICKS) {
			alerted = false;
		}
		if (target != null) {
			calmTicks = 0;
		}
		if (tickCount % 10 == 0) {
			updateBossBar(level);
		}
		bossEvent.setProgress(getHealth() / getMaxHealth());
	}

	/** The health bar, for everyone near while it's fighting. */
	private void updateBossBar(ServerLevel level) {
		boolean fighting = getTarget() != null || action() != IDLE;
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

	// --- Damage -----------------------------------------------------------------------------------------------

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (source.is(DamageTypeTags.IS_FIRE)) {
			damage *= FIRE_FACTOR;
		} else if (isOnFire()) {
			damage *= BURNING_FACTOR;
		}
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && source.getEntity() instanceof Player) {
			engaged = true;
		}
		if (hurt && source.is(DamageTypeTags.IS_FIRE) && random.nextInt(3) == 0) {
			// Melting: steam off it, and drips.
			level.sendParticles(ParticleTypes.CLOUD, getX(), getY(0.6), getZ(), 4, 0.5, 0.6, 0.5, 0.01);
			level.sendParticles(ParticleTypes.FALLING_WATER, getX(), getY(0.5), getZ(), 6, 0.6, 0.8, 0.6, 0.0);
		}
		return hurt;
	}

	@Override
	public boolean canFreeze() {
		return false;
	}

	/** Chills someone: frosted over and freezing (past fully frozen, so it lasts a moment), unless they can't freeze. */
	public static void chill(LivingEntity victim, int ticks) {
		if (ticks > 0 && victim.canFreeze()) {
			int cap = victim.getTicksRequiredToFreeze() + CHILL_OVER;
			victim.setTicksFrozen(Math.min(cap, Math.max(victim.getTicksFrozen(), 0) + ticks));
		}
	}

	/** Whether it'll go for this one: alive, attackable, and not in creative or spectating. */
	boolean fair(@Nullable LivingEntity target) {
		return target != null && target.isAlive() && canAttack(target)
				&& !(target instanceof Player player && (player.isCreative() || player.isSpectator()));
	}

	// --- Attacks (driven by SnowWardenAttackGoal) -------------------------------------------------------------

	/** Where its chest crystals are: the frost blast comes from here. */
	Vec3 chest() {
		float yaw = yBodyRot * Mth.DEG_TO_RAD;
		return position().add(-Mth.sin(yaw) * 0.7, 1.95, Mth.cos(yaw) * 0.7);
	}

	/** About where its fists come down in a slam: on the ground just ahead of it. */
	Vec3 slamPoint() {
		float yaw = yBodyRot * Mth.DEG_TO_RAD;
		return position().add(-Mth.sin(yaw) * 1.6, 0.1, Mth.cos(yaw) * 1.6);
	}

	/** A fist landing: the ordinary melee blow, if the target's still in reach, thrown back and a little up. */
	void swingHit(ServerLevel level, LivingEntity target) {
		playSound(SnowWardens.SWING, 1.6f, 0.9f + random.nextFloat() * 0.15f);
		if (fair(target) && isWithinMeleeAttackRange(target) && doHurtTarget(level, target)) {
			target.setDeltaMovement(target.getDeltaMovement().add(0.0, 0.3, 0.0));
			target.needsSync = true;
			Vec3 at = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0);
			level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0.05);
		}
	}

	/** Both fists into the ground: everyone near is hurt (less toward the edge), thrown back and up, and slowed. */
	void slam(ServerLevel level) {
		Vec3 at = slamPoint();
		playSound(SnowWardens.SLAM, 2.4f, 0.85f + random.nextFloat() * 0.1f);
		BlockState ground = level.getBlockState(BlockPos.containing(at).below());
		if (ground.isAir()) {
			ground = Blocks.SNOW_BLOCK.defaultBlockState();
		}
		// A ring of snow and broken ground thrown out from where the fists came down.
		int count = 40;
		for (int i = 0; i < count; i++) {
			float angle = i * Mth.TWO_PI / count;
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.1, at.z, 0, Mth.cos(angle), 0.2,
					Mth.sin(angle), 0.6);
			level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y + 0.2, at.z, 0, Mth.cos(angle), 0.05, Mth.sin(angle), 0.35);
		}
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * SLAM_FACTOR;
		DamageSource source = damageSources().mobAttack(this);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(SLAM_RADIUS, 2.5, SLAM_RADIUS),
				e -> !(e instanceof SnowWarden) && e.isAlive() && !(e instanceof Player p && (p.isCreative() || p.isSpectator())))) {
			double distance = Math.sqrt(victim.distanceToSqr(at.x, victim.getY(), at.z));
			if (distance > SLAM_RADIUS) {
				continue;
			}
			float falloff = (float) Math.max(0.4, 1.0 - distance / (SLAM_RADIUS + 1.0));
			if (victim.hurtServer(level, source, damage * falloff)) {
				Vec3 away = victim.position().subtract(at);
				away = new Vec3(away.x, 0.0, away.z);
				away = away.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0f, getYRot()) : away.normalize();
				victim.knockback(1.4 * falloff, -away.x, -away.z, source, damage);
				victim.setDeltaMovement(victim.getDeltaMovement().add(0.0, 0.45 * falloff, 0.0));
				victim.needsSync = true;
				victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), this);
				chill(victim, 60);
			}
		}
	}

	/** A few flakes drawn in toward its chest while it charges the frost blast. */
	void chargeParticles(ServerLevel level) {
		Vec3 chest = chest();
		for (int i = 0; i < 3; i++) {
			Vec3 from = chest.add(random.nextGaussian() * 1.6, random.nextGaussian() * 1.2, random.nextGaussian() * 1.6);
			Vec3 in = chest.subtract(from);
			level.sendParticles(ParticleTypes.SNOWFLAKE, from.x, from.y, from.z, 0, in.x, in.y, in.z, 0.12);
		}
	}

	/**
	 * The frost blast: a beam of ice from its chest to the target, through anything in the way, as the warden's sonic
	 * boom is. Whoever it hits (within {@link #BLAST_RANGE}) takes {@value #BLAST_DAMAGE} (scaled by difficulty), is
	 * knocked back, frozen over and slowed. It ignores shields, but a shield raised toward it takes {@link #SHIELD_BLOCKS}
	 * off the damage and the chill, and is chipped.
	 */
	void frostBlast(ServerLevel level, @Nullable LivingEntity target) {
		Vec3 from = chest();
		playSound(SnowWardens.BLAST, 3.0f, 0.95f + random.nextFloat() * 0.1f);
		if (!fair(target)) {
			return;
		}
		Vec3 to = target.getEyePosition().add(0.0, -0.3, 0.0);
		Vec3 line = to.subtract(from);
		double length = line.length();
		if (length > BLAST_RANGE || length < 1.0E-3) {
			return;
		}
		Vec3 dir = line.normalize();
		DustParticleOptions frost = new DustParticleOptions(0xA8F0FF, 1.6f);
		for (double d = 1.0; d < length; d += 0.5) {
			Vec3 p = from.add(dir.scale(d));
			level.sendParticles(ParticleTypes.SNOWFLAKE, p.x, p.y, p.z, 2, 0.08, 0.08, 0.08, 0.01);
			level.sendParticles(frost, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
			if (((int) (d * 2)) % 6 == 0) {
				level.sendParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
		boolean shielded = target.isBlocking() && target.getLookAngle().dot(dir) < -0.2;
		float share = shielded ? 1.0f - SHIELD_BLOCKS : 1.0f;
		if (shielded) {
			ItemStack shield = target.getItemBlockingWith();
			InteractionHand hand = target.getUsedItemHand();
			if (shield != null && !shield.isEmpty()) {
				shield.hurtAndBreak(8, target, hand);
			}
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f,
					0.8f);
		}
		DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
				.getOrThrow(SnowWardens.FROST_BLAST_DAMAGE), this);
		if (target.hurtServer(level, source, BLAST_DAMAGE * share)) {
			target.knockback(0.8 * share, -dir.x, -dir.z, source, BLAST_DAMAGE);
			target.needsSync = true;
		}
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, shielded ? 60 : 100, shielded ? 0 : 1), this);
		chill(target, (int) (180 * share));
		level.sendParticles(ParticleTypes.SNOWFLAKE, to.x, to.y, to.z, 30, 0.4, 0.5, 0.4, 0.08);
	}

	/** Lobs a big snowball in an arc at the target. */
	void throwBoulder(ServerLevel level, LivingEntity target) {
		FrostBoulder boulder = new FrostBoulder(level, this);
		float yaw = yBodyRot * Mth.DEG_TO_RAD;
		boulder.setPos(getX() - Mth.sin(yaw) * 0.6 + Mth.cos(yaw) * 0.8, getY() + 3.0, getZ() + Mth.cos(yaw) * 0.6 + Mth.sin(yaw) * 0.8);
		double dx = target.getX() - boulder.getX();
		double dy = target.getY(0.4) - boulder.getY();
		double dz = target.getZ() - boulder.getZ();
		double flat = Math.sqrt(dx * dx + dz * dz);
		boulder.shoot(dx, dy + flat * 0.22, dz, 1.3f, 3.0f);
		level.addFreshEntity(boulder);
		playSound(SnowWardens.THROW, 1.6f, 0.8f + random.nextFloat() * 0.15f);
	}

	/** The roar on first spotting someone: a gust of snow off it. */
	void roar(ServerLevel level) {
		playSound(SnowWardens.ROAR, 3.0f, 0.95f + random.nextFloat() * 0.1f);
		Vec3 head = position().add(0.0, 2.8, 0.0);
		int count = 32;
		for (int i = 0; i < count; i++) {
			float angle = i * Mth.TWO_PI / count;
			level.sendParticles(ParticleTypes.SNOWFLAKE, head.x, head.y, head.z, 0, Mth.cos(angle), 0.05, Mth.sin(angle), 0.4);
		}
		for (Player player : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(16.0), EntitySelector.NO_CREATIVE_OR_SPECTATOR)) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0), this);
		}
	}

	// --- Client animation -------------------------------------------------------------------------------------

	private void clientTick() {
		byte action = action();
		if (action != clientAction) {
			clientAction = action;
			actionTicks = 0;
		} else {
			actionTicks++;
		}
		chargeO = charge;
		sniffO = sniff;
		charge = Mth.approach(charge, action == CHARGING ? 1.0f : 0.0f, action == CHARGING ? 1.0f / CHARGE_TICKS * 1.6f : 0.12f);
		sniff = Mth.approach(sniff, action == SNIFFING ? 1.0f : 0.0f, 0.15f);
		// Frost drifting off it; streaming in toward the chest as it charges.
		if (random.nextInt(3) == 0) {
			level().addParticle(ParticleTypes.SNOWFLAKE, getRandomX(0.8), getY() + random.nextDouble() * getBbHeight(), getRandomZ(0.8),
					0.0, -0.02, 0.0);
		}
		if (action == CHARGING && random.nextInt(2) == 0) {
			level().addParticle(ParticleTypes.END_ROD, getRandomX(1.2), getY() + 1.4 + random.nextDouble() * 1.2, getRandomZ(1.2), 0.0, 0.01, 0.0);
		}
	}

	private float progress(byte action, int ticks, float partialTicks) {
		return clientAction == action ? Mth.clamp((actionTicks + partialTicks) / ticks, 0.0f, 1.0f) : 0.0f;
	}

	public float swingProgress(float partialTicks) {
		return Math.max(progress(SWING_RIGHT, SWING_TICKS, partialTicks), progress(SWING_LEFT, SWING_TICKS, partialTicks));
	}

	public float slamProgress(float partialTicks) {
		return progress(SLAMMING, SLAM_TICKS, partialTicks);
	}

	public float blastProgress(float partialTicks) {
		return progress(BLASTING, BLAST_TICKS, partialTicks);
	}

	public float throwProgress(float partialTicks) {
		return progress(THROWING, THROW_TICKS, partialTicks);
	}

	public float roarProgress(float partialTicks) {
		return progress(ROARING, ROAR_TICKS, partialTicks);
	}

	public float chargeAmount(float partialTicks) {
		return Mth.lerp(partialTicks, chargeO, charge);
	}

	public float sniffAmount(float partialTicks) {
		return Mth.lerp(partialTicks, sniffO, sniff);
	}

	// --- Sounds -----------------------------------------------------------------------------------------------

	@Override
	public int getMaxHeadYRot() {
		return 40;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SnowWardens.AMBIENT;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 160;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SnowWardens.HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SnowWardens.DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		playSound(SnowWardens.STEP, 1.0f, 1.0f);
	}

	@Override
	protected float getSoundVolume() {
		return 1.5f;
	}
}
