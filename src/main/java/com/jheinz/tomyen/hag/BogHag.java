package com.jheinz.tomyen.hag;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A Bog Hag: a hunched, rotten swamp witch with a crooked staff, a marsh-light lantern, a shawl of moss and a hump.
 * She won't close with anyone: she backs off to {@link BogHagSpellGoal#KEEP_AWAY} blocks and lobs hexes
 * ({@link HexGlob}) that lay Hag's Curse; anyone who does get close gets a rap of the staff that knocks them back;
 * and pressed (someone close, or her half dead) she slams the staff down and calls a couple of slimes up out of the
 * bog, at most {@value #MAX_SLIMES} about her at once.
 * <p>
 * Her spell (none, hex or summon) is synced so the client can play its animation; clients count how long it's been
 * on ({@link #spellTime}).
 */
public class BogHag extends Monster {
	public static final int HEX_TICKS = 24;
	/** The hex leaves the staff this far into the cast, as it's thrust forward. */
	public static final int HEX_RELEASE = 14;
	public static final int SUMMON_TICKS = 40;
	/** The staff comes down this far into the summoning. */
	public static final int SUMMON_RELEASE = 26;
	public static final int MAX_SLIMES = 3;
	/** No other hag within this many blocks of a natural spawn: she walks alone. */
	private static final double CROWD_RADIUS = 48.0;
	private static final int MARSH_LIGHT = 0xC8F06A;

	public enum Spell {
		NONE, HEX, SUMMON;

		int ticks() {
			return this == HEX ? HEX_TICKS : this == SUMMON ? SUMMON_TICKS : 0;
		}
	}

	private static final EntityDataAccessor<Integer> DATA_SPELL = SynchedEntityData.defineId(BogHag.class, EntityDataSerializers.INT);

	// Client side: ticks since the current spell began (0 with none on).
	private int spellTime;

	public BogHag(EntityType<? extends BogHag> type, Level level) {
		super(type, level);
		xpReward = 10;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 30.0)
				.add(Attributes.MOVEMENT_SPEED, 0.25)
				.add(Attributes.ATTACK_DAMAGE, 3.0)
				.add(Attributes.ATTACK_KNOCKBACK, 1.5)
				.add(Attributes.ARMOR, 2.0)
				.add(Attributes.FOLLOW_RANGE, 24.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_SPELL, 0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new BogHagSpellGoal(this));
		goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
		goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	public Spell spell() {
		int id = entityData.get(DATA_SPELL);
		return id >= 0 && id < Spell.values().length ? Spell.values()[id] : Spell.NONE;
	}

	void setSpell(Spell spell) {
		entityData.set(DATA_SPELL, spell.ordinal());
	}

	/** Client side: how far through her spell she is, 0 to 1 (0 with none on). */
	public float spellProgress(float partialTicks) {
		Spell spell = spell();
		return spell == Spell.NONE ? 0.0f : Mth.clamp((spellTime + partialTicks) / spell.ticks(), 0.0f, 1.0f);
	}

	/** A hex landed: she cackles. */
	public void cackle() {
		playSound(BogHags.CACKLE, 1.2f, 0.85f + random.nextFloat() * 0.15f);
	}

	/** Lobs a hex at {@code target}, leading it a little, from the head of her staff. */
	void throwHex(ServerLevel level, LivingEntity target) {
		HexGlob glob = new HexGlob(level, this);
		Vec3 look = Vec3.directionFromRotation(0.0f, yBodyRot);
		glob.setPos(getX() + look.x * 0.5 - look.z * 0.3, getEyeY() + 0.2, getZ() + look.z * 0.5 + look.x * 0.3);
		Vec3 lead = target.getDeltaMovement().scale(6.0);
		double dx = target.getX() + lead.x - glob.getX();
		double dy = target.getY(0.6) - glob.getY();
		double dz = target.getZ() + lead.z - glob.getZ();
		double flat = Math.sqrt(dx * dx + dz * dz);
		glob.shoot(dx, dy + flat * 0.2, dz, 0.85f, 5.0f);
		level.addFreshEntity(glob);
		level.playSound(null, getX(), getY(), getZ(), BogHags.CAST, getSoundSource(), 1.0f, 0.8f + random.nextFloat() * 0.2f);
	}

	/** Slams the staff down and calls slimes up out of the bog, between her and {@code target}. */
	void summon(ServerLevel level, LivingEntity target) {
		int count = Math.min(2, MAX_SLIMES - slimesAbout());
		Vec3 toward = target.position().subtract(position()).multiply(1.0, 0.0, 1.0);
		toward = toward.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0f, yBodyRot) : toward.normalize();
		for (int i = 0; i < count; i++) {
			Slime slime = EntityTypes.SLIME.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (slime == null) {
				continue;
			}
			double side = i == 0 ? -1.2 : 1.2;
			double x = getX() + toward.x * 1.8 - toward.z * side;
			double z = getZ() + toward.z * 1.8 + toward.x * side;
			BlockPos ground = BlockPos.containing(x, getY() + 1.0, z);
			for (int down = 0; down < 3 && level.getBlockState(ground.below()).isAir(); down++) {
				ground = ground.below();
			}
			slime.snapTo(x, ground.getY(), z, random.nextFloat() * 360.0f, 0.0f);
			slime.finalizeSpawn(level, level.getCurrentDifficultyAt(ground), EntitySpawnReason.MOB_SUMMONED, null);
			slime.setSize(2, true);
			slime.setTarget(target);
			level.addFreshEntity(slime);
			level.sendParticles(ParticleTypes.ITEM_SLIME, x, ground.getY() + 0.3, z, 16, 0.4, 0.2, 0.4, 0.1);
			level.sendParticles(new DustParticleOptions(MARSH_LIGHT, 1.0f), x, ground.getY() + 0.5, z, 8, 0.3, 0.4, 0.3, 0.0);
		}
		level.playSound(null, getX(), getY(), getZ(), BogHags.SUMMON, getSoundSource(), 1.2f, 0.8f);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_SQUISH, getSoundSource(), 1.0f, 0.6f);
	}

	/** Slimes close by, hers or the bog's: she won't call more than {@value #MAX_SLIMES}. */
	int slimesAbout() {
		return level().getEntitiesOfClass(Slime.class, getBoundingBox().inflate(12.0)).size();
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			spellTime = spell() == Spell.NONE ? 0 : spellTime + 1;
			// Marsh-light motes off the lantern now and then, and the odd witchy wisp.
			if (random.nextInt(14) == 0) {
				Vec3 side = Vec3.directionFromRotation(0.0f, yBodyRot + 90.0f);
				Vec3 front = Vec3.directionFromRotation(0.0f, yBodyRot);
				level().addParticle(new DustParticleOptions(MARSH_LIGHT, 0.6f), getX() + side.x * 0.45 + front.x * 0.45,
						getY() + 0.75, getZ() + side.z * 0.45 + front.z * 0.45, 0.0, 0.02, 0.0);
			}
			if (spell() != Spell.NONE && random.nextInt(2) == 0) {
				level().addParticle(ParticleTypes.WITCH, getX() + random.nextGaussian() * 0.3, getY() + 1.9, getZ() + random.nextGaussian() * 0.3,
						0.0, 0.05, 0.0);
			}
		}
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit) {
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.WOOD_HIT, SoundSource.HOSTILE, 0.8f, 0.7f);
		}
		return hit;
	}

	public static boolean checkSpawnRules(EntityType<BogHag> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos,
			RandomSource random) {
		if (EntitySpawnReason.isSpawner(reason)) {
			return checkMobSpawnRules(type, level, reason, pos, random);
		}
		return checkMonsterSpawnRules(type, level, reason, pos, random)
				&& level.getLevel().isDarkOutside()
				&& level.getEntitiesOfClass(BogHag.class, new AABB(pos).inflate(CROWD_RADIUS)).isEmpty();
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return BogHags.AMBIENT;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 200;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return BogHags.HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return BogHags.DEATH;
	}
}
