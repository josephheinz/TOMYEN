package com.jheinz.mymod.werewolf;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import com.jheinz.mymod.herbalism.Wolfsbane;
import com.jheinz.mymod.silver.EvilBane;

/**
 * A cursed villager on a full-moon night (see {@link WerewolfCurse}): a hunched, lean wolf-man, fast and strong. It
 * hunts villagers first, then players and iron golems, and smashes its way through wooden doors on any difficulty.
 * Silver and holy things hurt it as they hurt the evil (it's in {@code #mymod:holy_vulnerable}); everything else
 * glances off its hide, doing only {@code 1 - }{@link #RESISTANCE} of its damage.
 * <p>
 * It carries the villager it was, saved whole (profession, trades, experience, gossip, name, home and workplace), and
 * at sunrise turns back into that villager where it stands, still cursed. Killed, it's gone, villager and all.
 */
public class Werewolf extends Monster implements EvilBane.Wary {
	/** How much of any hit that isn't silver or holy it shrugs off. */
	public static final float RESISTANCE = 0.5f;
	/** A howl lasts this long, standing still with its head thrown back. */
	public static final int HOWL_TICKS = 50;
	/** One in this many ticks it stops to howl, when nothing it's after is close. */
	private static final int HOWL_CHANCE = 500;
	/** It won't stop to howl with its target closer than this. */
	private static final double HOWL_TARGET_DISTANCE = 10.0;
	/** Smashing a wooden door down takes this long (a zombie's minimum is 240). */
	private static final int DOOR_BREAK_TICKS = 80;

	private static final EntityDataAccessor<Boolean> DATA_HOWLING = SynchedEntityData.defineId(Werewolf.class, EntityDataSerializers.BOOLEAN);

	/** The villager it was, as saved; null for one that never was a villager (a spawn egg's). */
	private @Nullable CompoundTag villager;
	/** Has seen a night out: only then does sunrise turn it back (so one made by day lasts until the next dawn). */
	private boolean sawNight;
	private int howlTicks;
	/** A silver round is about to hit (see EvilBane.Wary); used up by the next hit. */
	private boolean silverIncoming;
	// Client side: how far its head is thrown back in a howl, 0 to 1.
	private float howl;
	private float howlO;

	public Werewolf(EntityType<? extends Werewolf> type, Level level) {
		super(type, level);
		xpReward = 15;
		getNavigation().setCanOpenDoors(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 44.0)
				.add(Attributes.MOVEMENT_SPEED, 0.34)
				.add(Attributes.ATTACK_DAMAGE, 8.0)
				.add(Attributes.ARMOR, 4.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.4)
				.add(Attributes.STEP_HEIGHT, 1.0)
				.add(Attributes.FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_HOWLING, false);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new DoorSmashGoal(this));
		goalSelector.addGoal(2, new LeapAtTargetGoal(this, 0.4f));
		goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.15, false));
		goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.9));
		goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		// Villagers first, even through walls: it goes for the houses.
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, false));
		targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, true));
		targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
	}

	/** Remembers the villager it was. */
	void setVillager(@Nullable CompoundTag villager) {
		this.villager = villager;
	}

	@Nullable CompoundTag villager() {
		return villager;
	}

	void setSawNight(boolean sawNight) {
		this.sawNight = sawNight;
	}

	public boolean isHowling() {
		return entityData.get(DATA_HOWLING);
	}

	/** Throws its head back and howls, heard right across a village. */
	public void howl() {
		howlTicks = HOWL_TICKS;
		entityData.set(DATA_HOWLING, true);
		getNavigation().stop();
		playSound(Werewolves.HOWL, 4.0f, 0.95f + random.nextFloat() * 0.1f);
	}

	/** How far its head is thrown back, 0 to 1 (client side). */
	public float howlAmount(float partialTicks) {
		return Mth.lerp(partialTicks, howlO, howl);
	}

	@Override
	public void struckBySilver() {
		silverIncoming = true;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		// Wolfsbane (a coated blade or arrow) bites through the hide like silver, and harder.
		float wolfsbane = Wolfsbane.strike(level, this, source);
		boolean bane = silverIncoming || wolfsbane > 1.0f || EvilBane.isSilverOrHoly(source) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
		silverIncoming = false;
		return super.hurtServer(level, source, (bane ? damage : damage * (1.0f - RESISTANCE)) * wolfsbane);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (level() instanceof ServerLevel level) {
			if (howlTicks > 0) {
				getNavigation().stop();
				if (--howlTicks == 0) {
					entityData.set(DATA_HOWLING, false);
				}
			} else if (random.nextInt(HOWL_CHANCE) == 0 && onGround() && !howlBlocked()) {
				howl();
			}
			if (tickCount % 20 == 0 && isAlive()) {
				if (WerewolfCurse.isNight(level)) {
					sawNight = true;
				} else if (sawNight) {
					WerewolfCurse.revert(level, this);
				}
			}
		}
	}

	private boolean howlBlocked() {
		LivingEntity target = getTarget();
		return target != null && target.isAlive() && distanceTo(target) < HOWL_TARGET_DISTANCE;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			howlO = howl;
			howl = Mth.approach(howl, isHowling() ? 1.0f : 0.0f, isHowling() ? 0.15f : 0.1f);
			if (isHowling() && random.nextInt(3) == 0) {
				// Breath steaming off it in the night air.
				level().addParticle(ParticleTypes.POOF, getX() + getLookAngle().x * 0.6, getEyeY() + 0.3, getZ() + getLookAngle().z * 0.6,
						0.0, 0.03, 0.0);
			}
		}
	}

	/** A low swish of claws with every hit that lands. */
	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit) {
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 0.6f, 0.7f);
		}
		return hit;
	}

	// It's somebody: it never despawns. In peaceful it turns back into the villager instead of vanishing.
	@Override
	public void checkDespawn() {
		if (level().getDifficulty() == Difficulty.PEACEFUL && level() instanceof ServerLevel level) {
			WerewolfCurse.revert(level, this);
		} else {
			super.checkDespawn();
		}
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel level) {
			WerewolfCurse.releaseVillagerPois(level, this);
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (villager != null) {
			output.store("Villager", CompoundTag.CODEC, villager);
		}
		output.putBoolean("SawNight", sawNight);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		villager = input.read("Villager", CompoundTag.CODEC).orElse(null);
		sawNight = input.getBooleanOr("SawNight", false);
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return isHowling() ? null : Werewolves.GROWL;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 160;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return Werewolves.HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return Werewolves.DEATH;
	}

	/** Smashes wooden doors (only those: see DoorInteractGoal) on any difficulty, and quicker than a zombie. */
	private static class DoorSmashGoal extends BreakDoorGoal {
		DoorSmashGoal(Mob mob) {
			super(mob, difficulty -> true);
		}

		@Override
		protected int getDoorBreakTime() {
			return DOOR_BREAK_TICKS;
		}

		@Override
		public boolean canContinueToUse() {
			return mob.isAggressive() && super.canContinueToUse();
		}

		@Override
		public boolean canUse() {
			return mob.isAggressive() && super.canUse();
		}
	}
}
