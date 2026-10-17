package com.jheinz.tomyen.moose;

import java.util.EnumSet;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * How a grown moose fights: a bull charges, a cow stomps.
 *
 * <p>A bull closes to within charging distance, then stops, faces his target and paws the ground for a moment
 * ({@link Moose#WINDING_UP}: a snort, dust off the hoof), and charges in a straight line along the way he was facing
 * when he set off ({@link Moose#CHARGING}: a bellow). Step aside during the wind-up and he thunders past. Whoever he
 * catches takes the full weight of the antlers and is thrown back and up; if he runs into a wall or a tree instead,
 * he's dazed a moment. Up close he has no room to run, so he lowers his head and lunges after a shorter wind-up.
 *
 * <p>A cow walks straight in, rears up onto her hind legs ({@link Moose#STOMPING}) and brings both forehooves down on
 * whatever's in front of her, a blow that knocks a player flat back.
 */
class MooseAttackGoal extends Goal {
	private enum Phase { APPROACH, WIND_UP, CHARGE, RECOVER, STOMP }

	private static final int WIND_UP_TICKS = 24;
	private static final int LUNGE_WIND_UP_TICKS = 10;
	private static final int CHARGE_TICKS = 32;
	/** Blocks a tick: about half again a sprinting player's pace. */
	private static final double CHARGE_SPEED = 0.42;
	private static final double CHARGE_FROM = 16.0;
	private static final double LUNGE_WITHIN = 4.0;
	private static final float CHARGE_DAMAGE_FACTOR = 1.45f;
	private static final double STOMP_REACH = 3.2;

	private final Moose moose;
	private Phase phase = Phase.APPROACH;
	private int timer;
	private int cooldown;
	private int repath;
	private Vec3 direction = Vec3.ZERO;

	MooseAttackGoal(Moose moose) {
		this.moose = moose;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
	}

	@Override
	public boolean canUse() {
		return !moose.isBaby() && !moose.isVehicle() && moose.fair(moose.getTarget());
	}

	@Override
	public boolean canContinueToUse() {
		// A charge or a stomp under way finishes whatever happens to the target.
		return !moose.isVehicle() && (phase == Phase.CHARGE || phase == Phase.STOMP || moose.fair(moose.getTarget()));
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		phase = Phase.APPROACH;
		timer = 0;
		cooldown = 10;
		repath = 0;
		moose.setAggressive(true);
	}

	@Override
	public void stop() {
		phase = Phase.APPROACH;
		moose.setAction(Moose.IDLE);
		moose.getNavigation().stop();
		moose.setAggressive(false);
	}

	@Override
	public void tick() {
		LivingEntity target = moose.getTarget();
		ServerLevel level = getServerLevel(moose);
		if (cooldown > 0) {
			cooldown--;
		}
		switch (phase) {
			case APPROACH -> approach(level, target);
			case WIND_UP -> windUp(level, target);
			case CHARGE -> charge(level, target);
			case RECOVER -> {
				moose.getNavigation().stop();
				if (--timer <= 0) {
					phase = Phase.APPROACH;
				}
			}
			case STOMP -> stomp(level, target);
		}
	}

	private void approach(ServerLevel level, LivingEntity target) {
		if (target == null) {
			return;
		}
		moose.setAction(Moose.IDLE);
		moose.getLookControl().setLookAt(target, 30.0f, 30.0f);
		double distance = moose.distanceTo(target);
		if (moose.isBull()) {
			boolean sees = moose.getSensing().hasLineOfSight(target);
			if (cooldown <= 0 && sees && distance <= CHARGE_FROM) {
				begin(Phase.WIND_UP, distance < LUNGE_WITHIN ? LUNGE_WIND_UP_TICKS : WIND_UP_TICKS);
				moose.setAction(Moose.WINDING_UP);
				moose.getNavigation().stop();
				moose.playSound(Mooses.SNORT, 1.4f, 0.85f + moose.getRandom().nextFloat() * 0.2f);
				return;
			}
			chase(target, 1.2);
		} else {
			if (cooldown <= 0 && distance <= STOMP_REACH && moose.getSensing().hasLineOfSight(target)) {
				begin(Phase.STOMP, Moose.STOMP_TICKS);
				moose.setAction(Moose.STOMPING);
				moose.getNavigation().stop();
				moose.playSound(Mooses.SNORT, 1.2f, 1.05f);
				return;
			}
			chase(target, 1.3);
		}
	}

	private void chase(LivingEntity target, double speed) {
		if (--repath <= 0) {
			repath = 4 + moose.getRandom().nextInt(6);
			moose.getNavigation().moveTo(target, speed);
		}
	}

	private void begin(Phase next, int ticks) {
		phase = next;
		timer = ticks;
	}

	private void windUp(ServerLevel level, LivingEntity target) {
		moose.getNavigation().stop();
		if (target != null) {
			face(target.position().subtract(moose.position()));
			moose.getLookControl().setLookAt(target, 30.0f, 30.0f);
		}
		// Dust kicked up as the hoof scrapes back.
		if (timer % 6 == 0) {
			Vec3 hoof = moose.hoofPosition();
			BlockState ground = level.getBlockState(moose.blockPosition().below());
			if (!ground.isAir()) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), hoof.x, hoof.y + 0.1, hoof.z, 8, 0.2, 0.05, 0.2, 0.1);
			}
		}
		if (--timer > 0) {
			return;
		}
		Vec3 toward = target != null ? target.position().subtract(moose.position()) : Vec3.directionFromRotation(0.0f, moose.getYRot());
		direction = new Vec3(toward.x, 0.0, toward.z).normalize();
		if (direction.lengthSqr() < 1.0E-4) {
			direction = Vec3.directionFromRotation(0.0f, moose.getYRot());
		}
		begin(Phase.CHARGE, CHARGE_TICKS);
		moose.setAction(Moose.CHARGING);
		moose.playSound(Mooses.BELLOW, 2.0f, 0.95f + moose.getRandom().nextFloat() * 0.1f);
	}

	private void charge(ServerLevel level, LivingEntity target) {
		face(direction);
		Vec3 motion = moose.getDeltaMovement();
		moose.setDeltaMovement(direction.x * CHARGE_SPEED, motion.y, direction.z * CHARGE_SPEED);
		// The antlers lead: a point just ahead of the chest, about head height.
		Vec3 front = moose.position().add(direction.scale(1.3)).add(0.0, 1.1, 0.0);
		if (moose.fair(target) && (target.getBoundingBox().inflate(0.7).contains(front)
				|| target.getBoundingBox().intersects(moose.getBoundingBox().inflate(0.2)))) {
			float damage = (float) moose.getAttributeValue(Attributes.ATTACK_DAMAGE) * CHARGE_DAMAGE_FACTOR;
			if (moose.strike(level, target, damage, direction, 2.2, 0.45)) {
				moose.playSound(Mooses.CHARGE_HIT, 1.6f, 0.9f + moose.getRandom().nextFloat() * 0.2f);
				level.sendParticles(ParticleTypes.CRIT, front.x, front.y, front.z, 14, 0.3, 0.3, 0.3, 0.3);
			}
			recover(25, 50);
			return;
		}
		if (moose.horizontalCollision) {
			// Ran into something: dazed for a moment, its head shaking.
			moose.playSound(Mooses.CHARGE_HIT, 1.2f, 0.7f);
			level.sendParticles(ParticleTypes.POOF, front.x, front.y, front.z, 6, 0.3, 0.3, 0.3, 0.02);
			recover(45, 40);
			return;
		}
		if (--timer <= 0) {
			recover(15, 30);
		}
	}

	private void recover(int ticks, int rest) {
		moose.setAction(Moose.IDLE);
		moose.setDeltaMovement(moose.getDeltaMovement().multiply(0.3, 1.0, 0.3));
		begin(Phase.RECOVER, ticks);
		cooldown = rest;
	}

	private void stomp(ServerLevel level, LivingEntity target) {
		moose.getNavigation().stop();
		if (target != null) {
			face(target.position().subtract(moose.position()));
		}
		int elapsed = Moose.STOMP_TICKS - timer;
		if (elapsed == Moose.STOMP_LANDS) {
			Vec3 hoof = moose.hoofPosition();
			moose.playSound(Mooses.STOMP, 1.5f, 0.9f + moose.getRandom().nextFloat() * 0.15f);
			BlockState ground = level.getBlockState(moose.blockPosition().below());
			if (!ground.isAir()) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), hoof.x, hoof.y + 0.1, hoof.z, 24, 0.5, 0.05, 0.5,
						0.15);
			}
			if (moose.fair(target) && target.position().distanceTo(hoof) <= 2.0 + target.getBbWidth()) {
				Vec3 push = target.position().subtract(moose.position());
				push = new Vec3(push.x, 0.0, push.z).normalize();
				moose.strike(level, target, (float) moose.getAttributeValue(Attributes.ATTACK_DAMAGE), push, 1.2, 0.2);
			}
		}
		if (--timer <= 0) {
			moose.setAction(Moose.IDLE);
			phase = Phase.APPROACH;
			cooldown = 18;
		}
	}

	/** Turns its whole body to face along a direction. */
	private void face(Vec3 toward) {
		if (toward.horizontalDistanceSqr() < 1.0E-6) {
			return;
		}
		float yaw = (float) (Mth.atan2(toward.z, toward.x) * Mth.RAD_TO_DEG) - 90.0f;
		moose.setYRot(Mth.rotLerp(0.4f, moose.getYRot(), yaw));
		moose.yBodyRot = moose.getYRot();
		moose.yHeadRot = moose.getYRot();
	}
}
