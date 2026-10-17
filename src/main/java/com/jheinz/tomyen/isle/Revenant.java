package com.jheinz.tomyen.isle;

import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;

import com.jheinz.tomyen.MyMod;

/**
 * A player's Revenant: the ghost of them as they died, guarding their hoard on the Isle of the Damned. It wears and
 * wields copies of their death gear (the real things are in the hoard, so it drops nothing) and fights with whatever
 * it holds: swords and axes in close, bows and crossbows from range, and the mod's hitscan guns the way the Shrimp
 * does (see {@link RevenantAttackGoal}).
 *
 * <p>Its strength follows the gear: health from 16 (naked, bare-handed) to 50 (full armor and a heavy weapon, see
 * {@link Loadout#score}); the armor and weapon count for themselves too, through their own attributes. Each depth
 * multiplies its health and damage and quickens it ({@link IsleDepth}), and in the Abyss it shrugs off fire and
 * knockback. It's {@code #tomyen:evil}, so silver and holy light bite it, but it isn't holy-vulnerable.
 *
 * <p>It goes after its owner first, and anyone who hits it. Everyone near sees its boss bar and can join in; only the
 * owner gets the hoard ({@link RevenantManager#defeated}). It's never saved: {@link RevenantManager} raises it when
 * its owner comes near its arena and lays it to rest again when they leave, and it can't stray far from its arena.
 */
public class Revenant extends Monster {
	private static final EntityDataAccessor<ResolvableProfile> DATA_PROFILE = SynchedEntityData.defineId(Revenant.class,
			EntityDataSerializers.RESOLVABLE_PROFILE);
	private static final EntityDataAccessor<Integer> DATA_DEPTH = SynchedEntityData.defineId(Revenant.class, EntityDataSerializers.INT);
	/** Seconds-worth of ticks into its forming: it rises out of the ground at its arena's heart. */
	private static final EntityDataAccessor<Integer> DATA_FORMING = SynchedEntityData.defineId(Revenant.class, EntityDataSerializers.INT);

	public static final int FORMING_TICKS = 40;
	private static final float MIN_HEALTH = 16.0f;
	private static final float MAX_HEALTH = 50.0f;
	/** How far from its arena's heart it will chase. */
	public static final int LEASH = 22;

	private static final net.minecraft.resources.Identifier HEALTH_ID = MyMod.id("revenant.health");
	private static final net.minecraft.resources.Identifier DAMAGE_ID = MyMod.id("revenant.depth_damage");
	private static final net.minecraft.resources.Identifier SPEED_ID = MyMod.id("revenant.depth_speed");
	private static final net.minecraft.resources.Identifier KNOCKBACK_ID = MyMod.id("revenant.depth_knockback");

	private final ServerBossEvent bossEvent = new ServerBossEvent(UUID.randomUUID(), Component.translatable("entity.tomyen.revenant"),
			BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);
	private @Nullable UUID owner;
	private Loadout loadout = Loadout.EMPTY;

	public Revenant(EntityType<? extends Revenant> type, Level level) {
		super(type, level);
		xpReward = 0;
		setCanPickUpLoot(false);
		setPersistenceRequired();
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			setDropChance(slot, 0.0f);
		}
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, MIN_HEALTH)
				.add(Attributes.MOVEMENT_SPEED, 0.27)
				.add(Attributes.ATTACK_DAMAGE, 1.0)
				.add(Attributes.FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_PROFILE, ResolvableProfile.Static.EMPTY);
		builder.define(DATA_DEPTH, 0);
		builder.define(DATA_FORMING, 0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new RevenantAttackGoal(this));
		goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 1.0));
		goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
				(target, level) -> owner != null && owner.equals(target.getUUID())));
	}

	/** Dresses it as the hoard's owner died, at the hoard's depth, and sets its strength from both. */
	public void become(Hoard hoard, BlockPos home) {
		owner = hoard.owner();
		loadout = hoard.loadout();
		entityData.set(DATA_PROFILE, hoard.profile());
		entityData.set(DATA_DEPTH, hoard.depth().index);
		entityData.set(DATA_FORMING, FORMING_TICKS);
		setHomeTo(home, LEASH);
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
				EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
			setItemSlot(slot, loadout.get(slot).copy());
		}
		IsleDepth depth = hoard.depth();
		float health = Mth.lerp(loadout.score(), MIN_HEALTH, MAX_HEALTH) * depth.healthMultiplier;
		setModifier(Attributes.MAX_HEALTH, HEALTH_ID, health - MIN_HEALTH, AttributeModifier.Operation.ADD_VALUE);
		setModifier(Attributes.ATTACK_DAMAGE, DAMAGE_ID, depth.damageMultiplier - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		setModifier(Attributes.MOVEMENT_SPEED, SPEED_ID, depth.speedBonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		if (depth == IsleDepth.ABYSS) {
			setModifier(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, 0.6, AttributeModifier.Operation.ADD_VALUE);
			addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
		}
		setHealth(getMaxHealth());
		Component name = Component.translatable("entity.tomyen.revenant.named", hoard.ownerName());
		setCustomName(name);
		setCustomNameVisible(false);
		bossEvent.setName(name);
	}

	private void setModifier(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
			net.minecraft.resources.Identifier id, double amount, AttributeModifier.Operation operation) {
		AttributeInstance instance = getAttribute(attribute);
		if (instance != null) {
			instance.removeModifier(id);
			if (amount != 0.0) {
				instance.addPermanentModifier(new AttributeModifier(id, amount, operation));
			}
		}
	}

	public @Nullable UUID owner() {
		return owner;
	}

	public boolean isOwner(@Nullable LivingEntity entity) {
		return entity != null && owner != null && owner.equals(entity.getUUID());
	}

	public ResolvableProfile profile() {
		return entityData.get(DATA_PROFILE);
	}

	public IsleDepth depth() {
		return IsleDepth.of(entityData.get(DATA_DEPTH));
	}

	/** 1 as it first appears, falling to 0 once it's fully formed. */
	public float forming(float partialTicks) {
		int left = entityData.get(DATA_FORMING);
		return left <= 0 ? 0.0f : Mth.clamp((left - partialTicks) / FORMING_TICKS, 0.0f, 1.0f);
	}

	public boolean isForming() {
		return entityData.get(DATA_FORMING) > 0;
	}

	@Override
	public void tick() {
		super.tick();
		int forming = entityData.get(DATA_FORMING);
		if (forming > 0 && !level().isClientSide()) {
			entityData.set(DATA_FORMING, forming - 1);
		}
		if (level().isClientSide()) {
			if (random.nextInt(4) == 0) {
				level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, getRandomX(0.5), getY() + random.nextDouble() * 1.8, getRandomZ(0.5), 0.0,
						0.02, 0.0);
			}
			if (isForming() && random.nextInt(2) == 0) {
				level().addParticle(ParticleTypes.SOUL, getRandomX(0.8), getY() + 0.1, getRandomZ(0.8), 0.0, 0.06, 0.0);
			}
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		bossEvent.setProgress(getHealth() / getMaxHealth());
		if (!RevenantManager.isTracked(this)) {
			// Not one the manager raised (a stray copy after a reload): lay it to rest.
			discard();
		}
	}

	@Override
	public boolean isImmobile() {
		return super.isImmobile() || isForming();
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		bossEvent.addPlayer(player);
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		bossEvent.removePlayer(player);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel level) {
			RevenantManager.defeated(level, this);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (isForming() || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.DROWN)) {
			return false;
		}
		return super.hurtServer(level, source, amount);
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
	protected int getBaseExperienceReward(ServerLevel level) {
		return 0;
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return Isle.REVENANT_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return Isle.REVENANT_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return Isle.REVENANT_DEATH;
	}

	/** A copy of its loadout's main-hand item, the one it fights with. */
	public ItemStack weapon() {
		return getMainHandItem();
	}

	public Optional<UUID> ownerId() {
		return Optional.ofNullable(owner);
	}
}
