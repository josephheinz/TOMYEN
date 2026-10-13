package com.jheinz.mymod.alien;

import java.util.EnumSet;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.energy.EnergyBeam;
import com.jheinz.mymod.energy.EnergyWeaponItem;
import com.jheinz.mymod.energy.EnergyWeapons;
import com.jheinz.mymod.energy.RayGunItem;

/**
 * A little green alien, a bit over a block tall, nearly half of it head. Curious and cowardly: it wanders round its
 * {@link Ufo}, stops to study animals and odd blocks (scanning them with a little light), sidles up to players to stare
 * at them from a few blocks off and backs away if they come closer. Hurt, it runs (back to the saucer, which calls the
 * whole crew in and takes off), and now and then it zaps someone with its ray gun: whoever hurt it, or anyone who
 * startles it by getting right up close ({@value #ZAP_DAMAGE} damage, within {@value #ZAP_RANGE} blocks).
 *
 * <p>Sneaky, so it never stops anyone sleeping (it isn't a monster). But a crew member sent after a sleeper (see
 * {@link Abductions}) walks to their bed, opening doors on the way. Aliens left without a ship (thrown from a crash,
 * hatched from an egg) wander on their own, and despawn like any mob far from players; a crash's survivors are dazed
 * for a while first.
 */
public class Alien extends PathfinderMob {
	static final float ZAP_DAMAGE = 2.0f;
	static final int ZAP_RANGE = 12;
	private static final int FEAR_TICKS = 200;
	private static final int DAZE_TICKS = 300;

	private static final EntityDataAccessor<Boolean> DATA_DAZED = SynchedEntityData.defineId(Alien.class, EntityDataSerializers.BOOLEAN);

	private @Nullable UUID shipId;
	private @Nullable Ufo ship;
	private @Nullable BlockPos bed;
	private @Nullable LivingEntity scaredOf;
	private int fearTicks;
	private int dazeTicks;
	private int zapCooldown = 40;

	public Alien(EntityType<? extends Alien> type, Level level) {
		super(type, level);
		xpReward = 5;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 12.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.FOLLOW_RANGE, 48.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_DAZED, false);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new ToBedGoal());
		goalSelector.addGoal(2, new BoardGoal());
		goalSelector.addGoal(3, new FleeGoal());
		goalSelector.addGoal(4, new ZapGoal());
		goalSelector.addGoal(5, new OpenDoorGoal(this, true));
		goalSelector.addGoal(6, new InspectGoal());
		goalSelector.addGoal(7, new CuriousGoal());
		goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.8));
		goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 12.0f));
		goalSelector.addGoal(10, new RandomLookAroundGoal(this));
	}

	// ------------------------------------------------------------------------------------------------ the ship

	/** Its saucer, if it has one and it's about (server side). */
	public @Nullable Ufo ship() {
		if (ship != null && (ship.isRemoved() || !ship.isAlive())) {
			ship = null;
		}
		if (ship == null && shipId != null && level() instanceof ServerLevel level && level.getEntity(shipId) instanceof Ufo ufo) {
			ship = ufo;
		}
		return ship;
	}

	void joinCrew(Ufo ufo) {
		ship = ufo;
		shipId = ufo.getUUID();
	}

	/** Its saucer was shot down: it's on its own now, and frightened. */
	void shipLost() {
		ship = null;
		shipId = null;
		bed = null;
		fearTicks = FEAR_TICKS;
	}

	/** Back aboard, by the ramp or beamed up. */
	void beamAboard() {
		if (level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.END_ROD, getX(), getY(0.5), getZ(), 12, 0.2, 0.4, 0.2, 0.05);
		}
		playSound(Aliens.UFO_TAKE, 0.8f, 1.6f);
		discard();
	}

	/** Thrown clear of a crash: it stands swaying for a while before it comes to. */
	void daze() {
		dazeTicks = DAZE_TICKS;
		fearTicks = FEAR_TICKS;
		entityData.set(DATA_DAZED, true);
	}

	public boolean isDazed() {
		return entityData.get(DATA_DAZED);
	}

	/** Sent after a sleeper: off to their bed. */
	void goToBed(BlockPos pos) {
		bed = pos;
	}

	void forgetBed() {
		bed = null;
		getNavigation().stop();
	}

	public @Nullable BlockPos bed() {
		return bed;
	}

	// ------------------------------------------------------------------------------------------------ ticking

	@Override
	public void aiStep() {
		super.aiStep();
		if (level().isClientSide()) {
			if (isDazed() && random.nextInt(4) == 0) {
				float angle = tickCount * 0.4f;
				level().addParticle(ParticleTypes.ENCHANTED_HIT, getX() + Mth.cos(angle) * 0.4, getY() + getBbHeight() + 0.1,
						getZ() + Mth.sin(angle) * 0.4, 0.0, 0.0, 0.0);
			}
			return;
		}
		fearTicks = Math.max(0, fearTicks - 1);
		zapCooldown = Math.max(0, zapCooldown - 1);
		if (dazeTicks > 0 && --dazeTicks == 0) {
			entityData.set(DATA_DAZED, false);
		}
		if (scaredOf != null && (!scaredOf.isAlive() || fearTicks == 0)) {
			scaredOf = null;
		}
		if (shipId != null && ship() == null && tickCount % 100 == 0 && level().getEntity(shipId) == null) {
			// Its saucer's gone (or out of reach): stranded.
			shipId = null;
		}
	}

	@Override
	public boolean isImmobile() {
		return super.isImmobile() || isDazed();
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && isAlive()) {
			fearTicks = FEAR_TICKS;
			bed = null;
			if (source.getEntity() instanceof LivingEntity by && by != this) {
				scaredOf = by;
			}
			Ufo ufo = ship();
			if (ufo != null) {
				ufo.alarm(scaredOf);
			}
			for (Alien mate : level.getEntitiesOfClass(Alien.class, getBoundingBox().inflate(24.0), mate -> mate != this && mate.ship() == ufo)) {
				mate.bed = null;
				mate.fearTicks = FEAR_TICKS;
			}
		}
		return hurt;
	}

	@Override
	public boolean removeWhenFarAway(double distanceSq) {
		return shipId == null;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return Aliens.ALIEN_AMBIENT;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 160;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return Aliens.ALIEN_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return Aliens.ALIEN_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * 1.1f;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.storeNullable("Ship", UUIDUtil.CODEC, shipId);
		output.putInt("Dazed", dazeTicks);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		shipId = input.read("Ship", UUIDUtil.CODEC).orElse(null);
		dazeTicks = input.getIntOr("Dazed", 0);
		entityData.set(DATA_DAZED, dazeTicks > 0);
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		GroundPathNavigation navigation = new GroundPathNavigation(this, level);
		navigation.setCanOpenDoors(true);
		return navigation;
	}

	// ------------------------------------------------------------------------------------------------ goals

	/** Not when it's dazed or busy being frightened. */
	private boolean calm() {
		return !isDazed() && fearTicks == 0 && bed == null;
	}

	/** Off to a sleeper's bed (see {@link Abductions}): quick, quiet, opening doors. */
	private class ToBedGoal extends Goal {
		ToBedGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return bed != null && !isDazed();
		}

		@Override
		public void tick() {
			if (bed == null) {
				return;
			}
			getLookControl().setLookAt(Vec3.atCenterOf(bed));
			if (getNavigation().isDone() || tickCount % 20 == 0) {
				getNavigation().moveTo(bed.getX() + 0.5, bed.getY(), bed.getZ() + 0.5, 1.3);
			}
		}

		@Override
		public void stop() {
			getNavigation().stop();
		}
	}

	/** Called back aboard: up the ramp, quick. */
	private class BoardGoal extends Goal {
		BoardGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			Ufo ufo = ship();
			return ufo != null && ufo.isRecalling() && !isDazed();
		}

		@Override
		public void tick() {
			Ufo ufo = ship();
			if (ufo == null) {
				return;
			}
			Vec3 foot = ufo.rampFoot();
			if (position().distanceToSqr(foot) < 2.5) {
				beamAboard();
			} else if (getNavigation().isDone() || tickCount % 20 == 0) {
				getNavigation().moveTo(foot.x, foot.y, foot.z, 1.4);
			}
		}
	}

	/** Hurt: run, away from whoever did it. */
	private class FleeGoal extends Goal {
		FleeGoal() {
			setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return fearTicks > 0 && !isDazed();
		}

		@Override
		public void tick() {
			if (!getNavigation().isDone() && tickCount % 30 != 0) {
				return;
			}
			Vec3 from = scaredOf != null ? scaredOf.position() : position().add(random.nextGaussian(), 0.0, random.nextGaussian());
			Vec3 away = DefaultRandomPos.getPosAway(Alien.this, 16, 7, from);
			if (away != null) {
				getNavigation().moveTo(away.x, away.y, away.z, 1.5);
			}
		}
	}

	/**
	 * Zaps whoever hurt it, or someone who's startled it by coming right up close: it takes aim for half a second, then
	 * fires.
	 */
	private class ZapGoal extends Goal {
		private @Nullable LivingEntity target;
		private int aim;

		ZapGoal() {
			setFlags(EnumSet.of(Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (zapCooldown > 0 || isDazed() || bed != null) {
				return false;
			}
			if (scaredOf != null && scaredOf.isAlive() && distanceToSqr(scaredOf) < ZAP_RANGE * ZAP_RANGE && hasLineOfSight(scaredOf)
					&& random.nextInt(20) == 0) {
				target = scaredOf;
				return true;
			}
			Player close = level().getNearestPlayer(Alien.this, 2.5);
			if (close != null && !close.isCreative() && !close.isSpectator() && !close.isShiftKeyDown() && random.nextInt(60) == 0) {
				target = close;
				return true;
			}
			return false;
		}

		@Override
		public boolean canContinueToUse() {
			return target != null && target.isAlive() && aim < 10;
		}

		@Override
		public void start() {
			aim = 0;
			swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			if (target == null) {
				return;
			}
			getLookControl().setLookAt(target);
			if (++aim == 10 && level() instanceof ServerLevel level && hasLineOfSight(target)) {
				Vec3 from = getEyePosition().add(getLookAngle().scale(0.4)).add(0.0, -0.3, 0.0);
				Vec3 at = target.getEyePosition().add(0.0, -0.3, 0.0);
				Vec3 end = EnergyWeaponItem.trace(level, Alien.this, from, at.subtract(from), ZAP_RANGE, 0, 0, false,
						(hit, point) -> hit.hurtServer(level, EnergyWeaponItem.damage(level, EnergyWeapons.ALIEN_RAY, Alien.this), ZAP_DAMAGE));
				EnergyBeam.send(level, Alien.this, from, end, RayGunItem.BEAM_COLOR, 0.12f, 220);
				playSound(EnergyWeapons.RAY_ZAP, 1.0f, 1.3f + random.nextFloat() * 0.2f);
				zapCooldown = 60 + random.nextInt(60);
			}
		}

		@Override
		public void stop() {
			target = null;
		}
	}

	/** Goes to study an animal nearby, or failing that some block, scanning it with little flashes of light. */
	private class InspectGoal extends Goal {
		private @Nullable Animal animal;
		private @Nullable Vec3 spot;
		private int studying;

		InspectGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (!calm() || random.nextInt(120) != 0) {
				return false;
			}
			animal = null;
			for (Animal near : level().getEntitiesOfClass(Animal.class, getBoundingBox().inflate(12.0, 4.0, 12.0))) {
				if (animal == null || distanceToSqr(near) < distanceToSqr(animal)) {
					animal = near;
				}
			}
			if (animal == null) {
				spot = DefaultRandomPos.getPos(Alien.this, 8, 3);
			}
			studying = 60 + random.nextInt(60);
			return animal != null || spot != null;
		}

		@Override
		public boolean canContinueToUse() {
			return calm() && studying > 0 && (animal == null || animal.isAlive());
		}

		@Override
		public void tick() {
			Vec3 at = animal != null ? animal.position().add(0.0, animal.getBbHeight() * 0.5, 0.0) : spot;
			if (at == null) {
				return;
			}
			getLookControl().setLookAt(at);
			if (distanceToSqr(at) > 6.0) {
				if (getNavigation().isDone() || tickCount % 20 == 0) {
					getNavigation().moveTo(at.x, at.y, at.z, 0.9);
				}
				return;
			}
			getNavigation().stop();
			studying--;
			if (studying % 15 == 0 && level() instanceof ServerLevel level) {
				// A little scan.
				level.sendParticles(ParticleTypes.GLOW, at.x, at.y, at.z, 3, 0.25, 0.25, 0.25, 0.0);
				if (random.nextInt(3) == 0) {
					playAmbientSound();
				}
			}
		}

		@Override
		public void stop() {
			animal = null;
			spot = null;
		}
	}

	/** Sidles up to a player to stare at them from a few blocks off, backing away if they come closer. */
	private class CuriousGoal extends Goal {
		private @Nullable Player player;
		private int staring;

		CuriousGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (!calm() || random.nextInt(80) != 0) {
				return false;
			}
			player = level().getNearestPlayer(Alien.this, 16.0);
			staring = 100 + random.nextInt(100);
			return player != null && !player.isSpectator();
		}

		@Override
		public boolean canContinueToUse() {
			return calm() && player != null && player.isAlive() && staring > 0 && distanceToSqr(player) < 24.0 * 24.0;
		}

		@Override
		public void tick() {
			if (player == null) {
				return;
			}
			staring--;
			getLookControl().setLookAt(player, 30.0f, 30.0f);
			double distance = distanceTo(player);
			if (distance < 3.0) {
				Vec3 away = DefaultRandomPos.getPosAway(Alien.this, 6, 3, player.position());
				if (away != null) {
					getNavigation().moveTo(away.x, away.y, away.z, 1.1);
				}
			} else if (distance > 5.0) {
				if (getNavigation().isDone() || tickCount % 20 == 0) {
					getNavigation().moveTo(player, 0.8);
				}
			} else {
				getNavigation().stop();
			}
		}

		@Override
		public void stop() {
			player = null;
		}
	}
}
