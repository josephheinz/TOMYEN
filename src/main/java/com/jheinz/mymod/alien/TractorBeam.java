package com.jheinz.mymod.alien;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A tractor beam's hold on whatever it's lifting: an invisible entity the victim rides, so the server moves them and
 * they can't walk or sneak off it (see PlayerTractorBeamMixin). Its renderer draws the beam itself, a glowing cone from
 * the saucer (or, carrying off a sleeper, from somewhere high overhead) down to the victim.
 * <ul>
 * <li>Lifting an animal for a {@link Ufo}, it rises {@value #ANIMAL_RISE} blocks a tick, drifting in under the saucer,
 * the animal turning slowly as it goes, until the animal's into the hull. It lets go (and the animal floats down) if the
 * saucer stops beaming, goes, or starts to crash, or if someone puts the animal on a lead.
 * <li>Carrying off a player for {@link Abductions}, it lifts them {@value #PLAYER_RISE} blocks a tick for
 * {@value #PLAYER_TICKS} ticks and hands them over to be dropped somewhere far away.
 * </ul>
 * Nothing about it is saved: reloaded, it has no rider and goes at once.
 */
public class TractorBeam extends Entity {
	static final double ANIMAL_RISE = 0.06;
	static final double PLAYER_RISE = 0.1;
	public static final int PLAYER_TICKS = 100;
	private static final float SPIN = 6.0f;

	/** The saucer it's coming from, or -1 for a beam straight down out of the sky. */
	private static final EntityDataAccessor<Integer> DATA_SOURCE = SynchedEntityData.defineId(TractorBeam.class, EntityDataSerializers.INT);

	private @Nullable Ufo ufo;
	private boolean kidnapping;
	private int ticks;

	public TractorBeam(EntityType<? extends TractorBeam> type, Level level) {
		super(type, level);
		noPhysics = true;
		setNoGravity(true);
	}

	/** Starts lifting an animal into this saucer; null if it couldn't take hold. */
	static @Nullable TractorBeam liftAnimal(ServerLevel level, LivingEntity animal, Ufo ufo) {
		TractorBeam beam = create(level, animal);
		beam.ufo = ufo;
		beam.entityData.set(DATA_SOURCE, ufo.getId());
		if (!animal.startRiding(beam, true, false)) {
			beam.discard();
			return null;
		}
		level.playSound(null, animal.getX(), animal.getY(), animal.getZ(), Aliens.UFO_BEAM, SoundSource.HOSTILE, 2.0f, 1.0f);
		return beam;
	}

	/** Starts carrying off a player who's been taken from their bed. */
	static @Nullable TractorBeam liftPlayer(ServerLevel level, ServerPlayer player) {
		TractorBeam beam = create(level, player);
		beam.kidnapping = true;
		if (player.isPassenger()) {
			player.stopRiding();
		}
		if (!player.startRiding(beam, true, false)) {
			beam.discard();
			return null;
		}
		player.sendOverlayMessage(Component.translatable("entity.mymod.ufo.abducted").withStyle(ChatFormatting.GREEN));
		return beam;
	}

	private static TractorBeam create(ServerLevel level, LivingEntity victim) {
		TractorBeam beam = new TractorBeam(Aliens.TRACTOR_BEAM, level);
		beam.snapTo(victim.getX(), victim.getY(), victim.getZ(), victim.getYRot(), 0.0f);
		level.addFreshEntity(beam);
		return beam;
	}

	/** Where the beam comes from, as of this tick on this side: its saucer's emitter, or high overhead. */
	public Vec3 source(float partialTicks) {
		Entity source = level().getEntity(entityData.get(DATA_SOURCE));
		return source != null ? source.getPosition(partialTicks) : getPosition(partialTicks).add(0.0, 24.0, 0.0);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		if (!(getFirstPassenger() instanceof LivingEntity rider) || !rider.isAlive()) {
			discard();
			return;
		}
		ticks++;
		if (kidnapping) {
			setPos(getX(), getY() + PLAYER_RISE, getZ());
			if (ticks % 3 == 0) {
				level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1.0, getZ(), 2, 0.5, 0.8, 0.5, 0.02);
			}
			if (ticks >= PLAYER_TICKS && rider instanceof ServerPlayer player) {
				ejectPassengers();
				discard();
				Abductions.deliver(player);
			}
			return;
		}
		if (ufo == null || !ufo.isAlive() || ufo.isRemoved() || ufo.isCrashing() || !ufo.isBeaming()
				|| rider instanceof Mob mob && mob.isLeashed()) {
			release();
			return;
		}
		if (getY() + rider.getBbHeight() >= ufo.getY() + 0.2) {
			ejectPassengers();
			discard();
			ufo.tookAboard(rider);
			return;
		}
		// Up, and in under the middle of the saucer.
		setPos(getX() + (ufo.getX() - getX()) * 0.02, getY() + ANIMAL_RISE, getZ() + (ufo.getZ() - getZ()) * 0.02);
		if (ticks % 4 == 0) {
			level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + rider.getBbHeight() * 0.5, getZ(), 1, 0.6, 0.6, 0.6, 0.01);
		}
		if (ticks % 40 == 0) {
			level.playSound(null, getX(), getY(), getZ(), Aliens.UFO_BEAM, SoundSource.HOSTILE, 1.5f, 1.0f + ticks * 0.002f);
		}
	}

	/** Lets go: the rider floats gently back down. */
	void release() {
		if (getFirstPassenger() instanceof LivingEntity rider) {
			ejectPassengers();
			rider.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200));
			rider.resetFallDistance();
		}
		discard();
	}

	/** The rider hangs right where the beam has it; anything but a player turns slowly as it goes up. */
	@Override
	protected void positionRider(Entity passenger, MoveFunction moveFunction) {
		moveFunction.accept(passenger, getX(), getY(), getZ());
		if (passenger instanceof LivingEntity living && !(passenger instanceof Player)) {
			living.setYRot(living.getYRot() + SPIN);
			living.setYBodyRot(living.getYRot());
			living.setYHeadRot(living.getYRot());
		}
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		return position();
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengers().isEmpty();
	}

	@Override
	public boolean dismountsUnderwater() {
		return false;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_SOURCE, -1);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}
}
