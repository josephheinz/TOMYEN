package com.jheinz.tomyen.isle.shade;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.isle.IsleDepth;

/**
 * A shade on the Isle of the Damned: a dead mob come back as a ghost. One entity type for all of them; which mob it
 * was ({@link #mimicType}) is synced, and the client draws it with that mob's own renderer, see-through and tinted
 * (ShadeRenderer). Its hitbox is that mob's, scaled down for the biggest so none is more than
 * {@value #MAX_WIDTH} wide or {@value #MAX_HEIGHT} tall.
 *
 * <p>Every shade drifts rather than walks (no gravity, straight at its quarry, rising over what's in the way), and is
 * {@code #tomyen:evil}. What it does depends on its {@link Kind}:
 *
 * <ul>
 * <li>{@link Kind#TIDE}: a routine death, from the Soul Tide: weak, in waves.
 * <li>{@link Kind#ECHO}: a remembered one (named, an elite, or one a player killed): tougher, wearing the gear it died
 * in, under its own name if it had one.
 * <li>{@link Kind#BOSS}: a boss's shade: a mini-encounter with a boss bar. It lashes with soul fire from range, blasts
 * those who crowd it ({@link #NOVA_RANGE} blocks), and calls up echoes at two-thirds and one-third health.
 * <li>{@link Kind#PET}: a named pet come to find its owner: harmless and unharmable, it follows them for a minute, then
 * goes.
 * </ul>
 *
 * Strength scales with depth (health ×1, ×1.35, ×1.8 from the Shore to the Abyss; damage up a step a depth). Casters
 * ({@code #tomyen:shade_casters}: skeletons, blazes, ghasts, evokers...) keep their distance and lash too.
 */
public class Shade extends Monster {
	public enum Kind {
		TIDE, ECHO, BOSS, PET;

		static Kind of(int index) {
			return values()[Mth.clamp(index, 0, values().length - 1)];
		}
	}

	private static final EntityDataAccessor<String> DATA_MIMIC = SynchedEntityData.defineId(Shade.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Byte> DATA_KIND = SynchedEntityData.defineId(Shade.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Integer> DATA_DEPTH = SynchedEntityData.defineId(Shade.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_FORMING = SynchedEntityData.defineId(Shade.class, EntityDataSerializers.INT);
	/** 1 for a fresh death, falling towards 0.4 as its record fades: how solid it looks. */
	private static final EntityDataAccessor<Float> DATA_VIVIDNESS = SynchedEntityData.defineId(Shade.class, EntityDataSerializers.FLOAT);

	public static final int FORMING_TICKS = 30;
	public static final float MAX_WIDTH = 3.0f;
	public static final float MAX_HEIGHT = 3.6f;
	private static final float[] DEPTH_HEALTH = {1.0f, 1.35f, 1.8f};
	private static final float[] BOSS_HEALTH = {150.0f, 210.0f, 280.0f};
	private static final float[] BOSS_DAMAGE = {9.0f, 11.0f, 14.0f};
	static final double NOVA_RANGE = 4.5;
	private static final int PET_TICKS = 1200;
	private static final int ATTACK_INTERVAL = 20;

	private @Nullable EntityType<?> mimicCache;
	private String mimicCacheKey = "";

	// Server side only.
	private @Nullable DeathRecord record;
	private IsleSouls.@Nullable RegionKey region;
	private @Nullable UUID anchor;
	private @Nullable ServerBossEvent bossEvent;
	private int petTicks = PET_TICKS;
	private int summonStage;
	private int novaCooldown = 100;

	public Shade(EntityType<? extends Shade> type, Level level) {
		super(type, level);
		moveControl = new ShadeMoveControl(this);
		setNoGravity(true);
		xpReward = 3;
		setCanPickUpLoot(false);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			setDropChance(slot, 0.0f);
		}
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.25)
				.add(Attributes.FLYING_SPEED, 0.4)
				.add(Attributes.ATTACK_DAMAGE, 3.0)
				.add(Attributes.FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_MIMIC, "");
		builder.define(DATA_KIND, (byte) 0);
		builder.define(DATA_DEPTH, 0);
		builder.define(DATA_FORMING, FORMING_TICKS);
		builder.define(DATA_VIVIDNESS, 1.0f);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(1, new CombatGoal(this));
		goalSelector.addGoal(2, new FollowOwnerGoal(this));
		goalSelector.addGoal(6, new DriftGoal(this));
		goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this, Shade.class));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, (target, level) -> kind() != Kind.PET));
	}

	// --- What it was --------------------------------------------------------------------------------------------------

	/** Makes it the shade of a mob of that type: its look, its strength, its gear and name. */
	public void setup(EntityType<?> mimic, Kind kind, IsleDepth depth, @Nullable DeathRecord fromRecord, IsleSouls.@Nullable RegionKey fromRegion,
			float vividness) {
		entityData.set(DATA_MIMIC, BuiltInRegistries.ENTITY_TYPE.getKey(mimic).toString());
		entityData.set(DATA_KIND, (byte) kind.ordinal());
		entityData.set(DATA_DEPTH, depth.index);
		entityData.set(DATA_VIVIDNESS, vividness);
		entityData.set(DATA_FORMING, FORMING_TICKS);
		record = fromRecord;
		region = fromRegion;
		refreshDimensions();

		int d = depth.index;
		double base = mimicHealth(mimic);
		double health;
		double damage;
		boolean elite = mimic.builtInRegistryHolder().is(IsleShades.NOTABLE_DEAD);
		switch (kind) {
			case TIDE -> {
				health = Mth.clamp(base * 0.5, 6.0, 20.0) * DEPTH_HEALTH[d];
				damage = 2.5 + 1.5 * d;
				xpReward = 2 + d;
			}
			case BOSS -> {
				health = BOSS_HEALTH[d];
				damage = BOSS_DAMAGE[d];
				xpReward = 60 + 20 * d;
				setBase(Attributes.ARMOR, 8.0);
				setBase(Attributes.KNOCKBACK_RESISTANCE, 0.9);
				setBase(Attributes.FOLLOW_RANGE, 40.0);
			}
			case PET -> {
				health = 20.0;
				damage = 0.0;
				xpReward = 0;
				targetSelector.removeAllGoals(goal -> true);
			}
			default -> {
				health = Mth.clamp(base * 0.8, 10.0, 40.0) * DEPTH_HEALTH[d];
				damage = 3.5 + 2.0 * d;
				if (elite) {
					health *= 1.5;
					damage += 2.0;
				}
				xpReward = 6 + 3 * d + (elite ? 10 : 0);
			}
		}
		if (fromRecord != null && fromRecord.name().isPresent() && kind == Kind.ECHO) {
			health *= 1.25;
		}
		setBase(Attributes.MAX_HEALTH, health);
		setBase(Attributes.ATTACK_DAMAGE, damage);
		setBase(Attributes.FLYING_SPEED, 0.4 * (1.0 + depth.speedBonus) * (kind == Kind.TIDE ? 0.9 : 1.0));
		setHealth(getMaxHealth());

		if (fromRecord != null && kind != Kind.TIDE) {
			wear(fromRecord.gear());
		}
		Component name = fromRecord == null ? null : fromRecord.name().<Component>map(Component::literal).orElse(null);
		if (name != null) {
			setCustomName(name);
			setCustomNameVisible(true);
		}
		if (kind == Kind.BOSS) {
			Component title = name != null ? Component.translatable("entity.tomyen.shade.boss_named", name)
					: Component.translatable("entity.tomyen.shade.boss", mimic.getDescription());
			bossEvent = new ServerBossEvent(UUID.randomUUID(), title, BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_6);
			bossEvent.setCreateWorldFog(true);
			setCustomName(title);
			setCustomNameVisible(false);
		}
	}

	private void wear(List<Identifier> gear) {
		EquipmentSlot[] slots = SoulRecorder.gearSlots();
		for (int i = 0; i < Math.min(gear.size(), slots.length); i++) {
			Item item = BuiltInRegistries.ITEM.getValue(gear.get(i));
			if (item != null) {
				setItemSlot(slots[i], new ItemStack(item));
			}
		}
	}

	private void setBase(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
		AttributeInstance instance = getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	@SuppressWarnings("unchecked")
	static double mimicHealth(EntityType<?> type) {
		if (DefaultAttributes.hasSupplier(type)) {
			AttributeSupplier supplier = DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type);
			if (supplier.hasAttribute(Attributes.MAX_HEALTH)) {
				return supplier.getBaseValue(Attributes.MAX_HEALTH);
			}
		}
		return 20.0;
	}

	/** The mob it's the shade of, if that's a known type. */
	public @Nullable EntityType<?> mimicType() {
		String key = entityData.get(DATA_MIMIC);
		if (!key.equals(mimicCacheKey)) {
			mimicCacheKey = key;
			Identifier id = Identifier.tryParse(key);
			mimicCache = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
		}
		return mimicCache;
	}

	public Kind kind() {
		return Kind.of(entityData.get(DATA_KIND));
	}

	public IsleDepth depth() {
		return IsleDepth.of(entityData.get(DATA_DEPTH));
	}

	public float vividness() {
		return entityData.get(DATA_VIVIDNESS);
	}

	/** 1 as it first appears, falling to 0 once it's fully formed. */
	public float forming(float partialTicks) {
		int left = entityData.get(DATA_FORMING);
		return left <= 0 ? 0.0f : Mth.clamp((left - partialTicks) / FORMING_TICKS, 0.0f, 1.0f);
	}

	public boolean isForming() {
		return entityData.get(DATA_FORMING) > 0;
	}

	public boolean isCaster() {
		EntityType<?> mimic = mimicType();
		return kind() == Kind.BOSS || mimic != null && mimic.builtInRegistryHolder().is(IsleShades.SHADE_CASTERS);
	}

	/** How much smaller than the mob it was it's drawn and boxed, so the biggest fit. */
	public float mimicScale() {
		EntityType<?> mimic = mimicType();
		if (mimic == null) {
			return 1.0f;
		}
		EntityDimensions dims = mimic.getDimensions();
		return Math.min(1.0f, Math.min(MAX_WIDTH / Math.max(0.1f, dims.width()), MAX_HEIGHT / Math.max(0.1f, dims.height())));
	}

	@Override
	protected EntityDimensions getDefaultDimensions(Pose pose) {
		EntityType<?> mimic = mimicType();
		if (mimic == null) {
			return super.getDefaultDimensions(pose);
		}
		EntityDimensions dims = mimic.getDimensions();
		float scale = mimicScale();
		return EntityDimensions.scalable(dims.width() * scale, dims.height() * scale).withEyeHeight(dims.eyeHeight() * scale);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (DATA_MIMIC.equals(accessor)) {
			refreshDimensions();
		}
	}

	@Override
	protected Component getTypeName() {
		EntityType<?> mimic = mimicType();
		return mimic == null ? super.getTypeName() : Component.translatable("entity.tomyen.shade.of", mimic.getDescription());
	}

	// --- Manager bookkeeping ------------------------------------------------------------------------------------------

	public @Nullable DeathRecord record() {
		return record;
	}

	public IsleSouls.@Nullable RegionKey region() {
		return region;
	}

	public @Nullable UUID anchor() {
		return anchor;
	}

	void setAnchor(@Nullable UUID player) {
		anchor = player;
	}

	// --- Living -------------------------------------------------------------------------------------------------------

	@Override
	public void tick() {
		super.tick();
		int forming = entityData.get(DATA_FORMING);
		if (level().isClientSide()) {
			Kind kind = kind();
			int chance = kind == Kind.BOSS ? 2 : kind == Kind.TIDE ? 9 : 6;
			if (random.nextInt(chance) == 0) {
				level().addParticle(kind == Kind.PET ? ParticleTypes.WHITE_ASH : ParticleTypes.SOUL_FIRE_FLAME, getRandomX(0.6),
						getY() + random.nextDouble() * getBbHeight(), getRandomZ(0.6), 0.0, 0.015, 0.0);
			}
			if (forming > 0 && random.nextInt(2) == 0) {
				level().addParticle(ParticleTypes.SOUL, getRandomX(0.9), getY() + 0.1, getRandomZ(0.9), 0.0, 0.05, 0.0);
			}
			return;
		}
		if (forming > 0) {
			entityData.set(DATA_FORMING, forming - 1);
		}
		if (isAlive() && !ShadeManager.isTracked(this)) {
			ShadeManager.adopt(this);
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		Kind kind = kind();
		if (kind == Kind.PET && --petTicks <= 0) {
			ShadeManager.petGoes(this);
			return;
		}
		if (kind == Kind.BOSS) {
			bossStep(level);
		}
	}

	private void bossStep(ServerLevel level) {
		if (bossEvent != null) {
			bossEvent.setProgress(getHealth() / getMaxHealth());
		}
		float health = getHealth() / getMaxHealth();
		if (summonStage < 2 && health < (summonStage == 0 ? 0.67f : 0.34f)) {
			summonStage++;
			ShadeManager.summonEchoes(level, this, 2);
		}
		if (novaCooldown > 0) {
			novaCooldown--;
			return;
		}
		List<Player> close = level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(NOVA_RANGE),
				player -> !player.isSpectator() && !player.isCreative() && player.isAlive());
		if (close.isEmpty()) {
			return;
		}
		novaCooldown = 140;
		level.playSound(null, getX(), getY(), getZ(), IsleShades.BOSS_SHADE_NOVA, SoundSource.HOSTILE, 1.5f, 1.0f);
		for (int i = 0; i < 24; i++) {
			double angle = i * Math.PI * 2 / 24;
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX() + Math.cos(angle) * 1.5, getY() + 0.4, getZ() + Math.sin(angle) * 1.5, 1,
					Math.cos(angle) * 0.4, 0.02, Math.sin(angle) * 0.4, 0.3);
		}
		for (Player player : close) {
			DamageSource nova = damageSources().indirectMagic(this, this);
			float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.4f;
			player.hurtServer(level, nova, damage);
			player.knockback(1.6, getX() - player.getX(), getZ() - player.getZ(), nova, damage);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurtServer(level, source, amount);
		}
		if (isForming() || kind() == Kind.PET || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.DROWN)
				|| source.getEntity() instanceof Shade) {
			return false;
		}
		return super.hurtServer(level, source, amount);
	}

	@Override
	protected boolean considersEntityAsAlly(Entity entity) {
		return entity instanceof Shade || super.considersEntityAsAlly(entity);
	}

	@Override
	public boolean isPushable() {
		return kind() != Kind.PET && super.isPushable();
	}

	@Override
	public boolean isImmobile() {
		return super.isImmobile() || isForming();
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel level) {
			ShadeManager.died(level, this, source);
		}
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		if (killedByPlayer) {
			ShadeLoot.drop(level, this);
		}
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		if (bossEvent != null) {
			bossEvent.addPlayer(player);
		}
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		if (bossEvent != null) {
			bossEvent.removePlayer(player);
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		super.remove(reason);
		if (bossEvent != null) {
			bossEvent.removeAllPlayers();
		}
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	public void checkDespawn() {
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return kind() == Kind.PET ? IsleShades.PET_ECHO : IsleShades.SHADE_AMBIENT;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 200;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return IsleShades.SHADE_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return IsleShades.SHADE_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return kind() == Kind.BOSS ? 1.6f : 0.6f;
	}

	@Override
	public float getVoicePitch() {
		float base = kind() == Kind.BOSS ? 0.6f : kind() == Kind.PET ? 1.3f : 1.0f;
		return base * (0.9f + random.nextFloat() * 0.2f) / Math.max(0.5f, (float) Math.sqrt(getBbHeight() / 1.8f));
	}

	/** For /summon: {@code {mimic:"minecraft:zombie",kind:1,depth:2}}. Shades are never saved, so this is all it reads. */
	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		input.getString("mimic").map(Identifier::tryParse).flatMap(BuiltInRegistries.ENTITY_TYPE::getOptional).ifPresent(
				mimic -> setup(mimic, Kind.of(input.getIntOr("kind", Kind.ECHO.ordinal())), IsleDepth.of(input.getIntOr("depth", 0)), null, null,
						1.0f));
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putString("mimic", entityData.get(DATA_MIMIC));
		output.putInt("kind", kind().ordinal());
		output.putInt("depth", depth().index);
	}

	/** Where the ground (or the sea) is under a spot: shades hover a little over it. */
	static double surfaceY(Level level, double x, double z) {
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
	}

	// --- Moving and fighting ------------------------------------------------------------------------------------------

	/**
	 * Drifts straight at where it wants to be, as a vex does, but stays solid: it rises over whatever it bumps into.
	 */
	static final class ShadeMoveControl extends MoveControl<Shade> {
		ShadeMoveControl(Shade shade) {
			super(shade);
		}

		@Override
		public void tick() {
			if (operation != Operation.MOVE_TO) {
				mob.setDeltaMovement(mob.getDeltaMovement().multiply(0.8, 0.6, 0.8));
				return;
			}
			Vec3 delta = new Vec3(wantedX - mob.getX(), wantedY - mob.getY(), wantedZ - mob.getZ());
			double length = delta.length();
			if (length < 0.4) {
				operation = Operation.WAIT;
				mob.setDeltaMovement(mob.getDeltaMovement().scale(0.5));
				return;
			}
			double accel = speedModifier * mob.getAttributeValue(Attributes.FLYING_SPEED) * 0.05;
			Vec3 motion = mob.getDeltaMovement().add(delta.scale(accel / length));
			if (mob.horizontalCollision) {
				motion = new Vec3(motion.x, Math.max(motion.y, 0.12), motion.z);
			}
			mob.setDeltaMovement(motion);
			LivingEntity target = mob.getTarget();
			double faceX = target != null ? target.getX() - mob.getX() : motion.x;
			double faceZ = target != null ? target.getZ() - mob.getZ() : motion.z;
			if (faceX * faceX + faceZ * faceZ > 1.0E-4) {
				mob.setYRot(rotlerp(mob.getYRot(), (float) (Mth.atan2(faceZ, faceX) * Mth.RAD_TO_DEG) - 90.0f, 20.0f));
				mob.yBodyRot = mob.getYRot();
			}
		}
	}

	/**
	 * Closes in and strikes; or, for a caster, holds off at range and lashes with soul fire (a short wind-up the player
	 * can see and step out of the line of).
	 */
	static final class CombatGoal extends Goal {
		private static final int LASH_WINDUP = 16;
		private final Shade shade;
		private int attackCooldown;
		private int lashCooldown = 40;
		private int windup;
		private float orbit;

		CombatGoal(Shade shade) {
			this.shade = shade;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = shade.getTarget();
			return target != null && target.isAlive() && shade.kind() != Kind.PET && !shade.isForming();
		}

		@Override
		public void stop() {
			windup = 0;
			shade.setAggressive(false);
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity target = shade.getTarget();
			if (target == null || !(shade.level() instanceof ServerLevel level)) {
				return;
			}
			shade.setAggressive(true);
			shade.getLookControl().setLookAt(target, 30.0f, 30.0f);
			double distance = shade.distanceTo(target);
			attackCooldown = Math.max(0, attackCooldown - 1);
			if (shade.isCaster()) {
				if (windup > 0) {
					shade.getMoveControl().setWantedPosition(shade.getX(), shade.getY(), shade.getZ(), 0.2);
					if (windup % 4 == 0) {
						level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, shade.getX(), shade.getY(0.6), shade.getZ(), 4, 0.25, 0.3, 0.25, 0.01);
					}
					if (--windup == 0) {
						lash(level, target);
					}
				} else {
					keepRange(target, distance);
					if (--lashCooldown <= 0 && distance < 18.0 && shade.hasLineOfSight(target)) {
						windup = LASH_WINDUP;
						lashCooldown = shade.kind() == Kind.BOSS ? 45 : 70;
						level.playSound(null, shade.getX(), shade.getY(), shade.getZ(), IsleShades.SHADE_LASH, SoundSource.HOSTILE, 0.8f, 0.6f);
					}
				}
				if (shade.kind() != Kind.BOSS) {
					return;
				}
			} else {
				shade.getMoveControl().setWantedPosition(target.getX(), target.getY() + 0.1, target.getZ(), 1.0);
			}
			if (attackCooldown == 0 && shade.isWithinMeleeAttackRange(target) && shade.hasLineOfSight(target)) {
				attackCooldown = ATTACK_INTERVAL;
				shade.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				shade.doHurtTarget(level, target);
			}
		}

		private void keepRange(LivingEntity target, double distance) {
			Vec3 away = shade.position().subtract(target.position()).multiply(1.0, 0.0, 1.0);
			if (away.lengthSqr() < 1.0E-4) {
				away = new Vec3(1.0, 0.0, 0.0);
			}
			away = away.normalize();
			double want = shade.kind() == Kind.BOSS ? 6.0 : 8.0;
			orbit += 0.02f;
			Vec3 side = new Vec3(-away.z, 0.0, away.x).scale(Mth.sin(orbit) * 3.0);
			Vec3 spot = target.position().add(away.scale(want)).add(side);
			double y = Math.max(target.getY() + 1.0, surfaceY(shade.level(), spot.x, spot.z) + 1.0);
			shade.getMoveControl().setWantedPosition(spot.x, y, spot.z, distance > 14.0 ? 1.0 : 0.7);
		}

		private void lash(ServerLevel level, LivingEntity target) {
			Vec3 from = shade.getEyePosition();
			Vec3 to = target.getEyePosition();
			Vec3 step = to.subtract(from);
			int points = Math.max(4, (int) (step.length() * 1.5));
			for (int i = 0; i <= points; i++) {
				Vec3 at = from.add(step.scale(i / (double) points));
				level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, 1, 0.03, 0.03, 0.03, 0.0);
			}
			level.playSound(null, target.getX(), target.getY(), target.getZ(), IsleShades.SHADE_LASH, SoundSource.HOSTILE, 1.0f, 1.2f);
			if (shade.hasLineOfSight(target) && shade.distanceTo(target) < 20.0) {
				float damage = (float) shade.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.75f;
				if (target.hurtServer(level, shade.damageSources().indirectMagic(shade, shade), damage)) {
					target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 0), shade);
				}
			}
		}
	}

	/** A pet's shade: stays by its owner till it goes. */
	static final class FollowOwnerGoal extends Goal {
		private final Shade shade;

		FollowOwnerGoal(Shade shade) {
			this.shade = shade;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return shade.kind() == Kind.PET && shade.anchor != null && !shade.isForming();
		}

		@Override
		public void tick() {
			Player owner = shade.level().getPlayerByUUID(shade.anchor);
			if (owner == null) {
				return;
			}
			shade.getLookControl().setLookAt(owner, 20.0f, 20.0f);
			if (shade.distanceToSqr(owner) > 9.0) {
				Vec3 behind = owner.position().add(owner.getLookAngle().multiply(-1.5, 0.0, -1.5));
				shade.getMoveControl().setWantedPosition(behind.x, owner.getY() + 0.3, behind.z, 1.1);
			}
		}
	}

	/** With nothing to chase, it wanders a little, low over the ground. */
	static final class DriftGoal extends Goal {
		private final Shade shade;

		DriftGoal(Shade shade) {
			this.shade = shade;
			setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return shade.getTarget() == null && !shade.getMoveControl().hasWanted() && shade.getRandom().nextInt(reducedTickDelay(30)) == 0;
		}

		@Override
		public boolean canContinueToUse() {
			return false;
		}

		@Override
		public void start() {
			double x = shade.getX() + (shade.getRandom().nextDouble() - 0.5) * 14.0;
			double z = shade.getZ() + (shade.getRandom().nextDouble() - 0.5) * 14.0;
			double y = surfaceY(shade.level(), x, z) + 0.6 + shade.getRandom().nextDouble() * 1.5;
			shade.getMoveControl().setWantedPosition(x, y, z, 0.45);
		}
	}
}
