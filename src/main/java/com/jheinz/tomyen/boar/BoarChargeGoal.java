package com.jheinz.tomyen.boar;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A provoked boar's attack. It closes in until it has a run-up and a clear view, then stops and paws the ground (the
 * telegraph: a snort, a scraping hoof and kicked-up dirt), then charges: a fast, straight run at where its target stood,
 * which it can't steer. Whatever living thing it runs into takes a heavy ram and is thrown back. A charge that ends in a
 * wall or a tree trunk (or a raised shield) leaves it dazed for a few seconds, standing still and taking extra damage;
 * one that just runs out stops and turns. Up close it gores with its tusks instead, a quick toss of the head.
 */
class BoarChargeGoal extends Goal {
	private enum Phase { APPROACH, PAW, CHARGE, RECOVER, STUNNED }

	/** It wants at least this much run-up, and won't start a charge from further than this. */
	private static final double MIN_RUN = 3.5;
	private static final double MAX_RUN = 14.0;
	/** Closer than this it gores rather than charges. */
	private static final double GORE_RANGE = 2.2;
	private static final int PAW_TICKS = 28;
	private static final int CHARGE_TICKS = 40;
	private static final double CHARGE_SPEED = 0.42;
	/** How far ahead of its hitbox its snout reaches. */
	private static final double WALL_LOOKAHEAD = 0.5;
	private static final int STUN_TICKS = 70;
	private static final int RECOVER_TICKS = 30;
	private static final int GORE_COOLDOWN = 20;
	/** A charge hits this much harder than a gore, and throws its victim this hard. */
	private static final float RAM_DAMAGE = 1.5f;
	private static final double RAM_KNOCKBACK = 1.6;

	private final Boar boar;
	private Phase phase = Phase.APPROACH;
	private int ticks;
	private int goreCooldown;
	private Vec3 direction = Vec3.ZERO;

	BoarChargeGoal(Boar boar) {
		this.boar = boar;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = boar.getTarget();
		return !boar.isBaby() && target != null && target.isAlive() && boar.canAttack(target);
	}

	@Override
	public boolean canContinueToUse() {
		// A dazed boar stays dazed whatever happened to its target.
		return phase == Phase.STUNNED || canUse();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		boar.setAggressive(true);
		enter(Phase.APPROACH);
	}

	@Override
	public void stop() {
		boar.setState(Boar.IDLE);
		boar.getNavigation().stop();
		if (boar.getTarget() == null) {
			boar.setAggressive(false);
		}
	}

	private void enter(Phase next) {
		phase = next;
		ticks = 0;
		boar.setState(switch (next) {
			case PAW -> Boar.PAWING;
			case CHARGE -> Boar.CHARGING;
			case STUNNED -> Boar.STUNNED;
			default -> Boar.IDLE;
		});
	}

	@Override
	public void tick() {
		ticks++;
		goreCooldown = Math.max(0, goreCooldown - 1);
		ServerLevel level = getServerLevel(boar);
		LivingEntity target = boar.getTarget();
		if (phase == Phase.STUNNED) {
			stunned(level);
			return;
		}
		if (target == null) {
			return;
		}
		double distance = boar.distanceTo(target);
		switch (phase) {
			case APPROACH -> {
				boar.getLookControl().setLookAt(target, 30.0f, 30.0f);
				if (distance < GORE_RANGE + target.getBbWidth() / 2) {
					gore(level, target);
				} else if (distance >= MIN_RUN && distance <= MAX_RUN && boar.getSensing().hasLineOfSight(target) && boar.onGround()) {
					boar.getNavigation().stop();
					enter(Phase.PAW);
					boar.playSound(Boars.ANGRY, 1.0f, boar.getVoicePitch());
				} else if (distance < MIN_RUN) {
					// Too close for a run-up: back off a few steps, or gore if it can't.
					Vec3 away = boar.position().subtract(target.position()).normalize().scale(4.0).add(boar.position());
					if (ticks % 10 == 1) {
						boar.getNavigation().moveTo(away.x, away.y, away.z, 1.1);
					}
				} else if (ticks % 10 == 1) {
					boar.getNavigation().moveTo(target, 1.15);
				}
			}
			case PAW -> {
				boar.getNavigation().stop();
				face(target.position());
				boar.getLookControl().setLookAt(target, 30.0f, 30.0f);
				if (ticks % 9 == 4) {
					boar.playSound(Boars.PAW, 0.9f, 0.9f + boar.getRandom().nextFloat() * 0.2f);
					dirt(level, 6);
				}
				if (distance < GORE_RANGE + target.getBbWidth() / 2) {
					gore(level, target);
					enter(Phase.RECOVER);
				} else if (ticks >= PAW_TICKS) {
					Vec3 to = target.position().subtract(boar.position());
					direction = new Vec3(to.x, 0.0, to.z).normalize();
					face(target.position());
					enter(Phase.CHARGE);
					boar.playSound(Boars.CHARGE, 1.2f, boar.getVoicePitch());
				}
			}
			case CHARGE -> charge(level);
			case RECOVER -> {
				boar.getLookControl().setLookAt(target, 30.0f, 30.0f);
				if (distance < GORE_RANGE + target.getBbWidth() / 2) {
					gore(level, target);
				} else if (ticks % 10 == 1 && distance > MIN_RUN + 2.0) {
					boar.getNavigation().moveTo(target, 0.8);
				}
				if (ticks >= RECOVER_TICKS) {
					enter(Phase.APPROACH);
				}
			}
			default -> {
			}
		}
	}

	/** The run: straight on at full tilt; ramming whatever it meets, dazed by whatever stops it. */
	private void charge(ServerLevel level) {
		boar.getNavigation().stop();
		boar.setYRot((float) (Mth.atan2(direction.z, direction.x) * Mth.RAD_TO_DEG) - 90.0f);
		boar.setYBodyRot(boar.getYRot());
		boar.setYHeadRot(boar.getYRot());
		Vec3 motion = boar.getDeltaMovement();
		boar.setDeltaMovement(direction.x * CHARGE_SPEED, motion.y, direction.z * CHARGE_SPEED);
		if (ticks % 3 == 0) {
			dirt(level, 3);
		}
		AABB reach = boar.getBoundingBox().inflate(0.25).expandTowards(direction.scale(0.6));
		List<LivingEntity> hit = level.getEntitiesOfClass(LivingEntity.class, reach,
				entity -> entity != boar && entity.isAlive() && !Boar.isKin(entity) && !entity.isSpectator()
						&& !(entity instanceof Player player && player.isCreative()));
		if (!hit.isEmpty()) {
			boolean blocked = false;
			for (LivingEntity victim : hit) {
				blocked |= ram(level, victim);
			}
			if (blocked) {
				stun(level);
			} else {
				boar.setDeltaMovement(boar.getDeltaMovement().multiply(0.2, 1.0, 0.2));
				enter(Phase.RECOVER);
			}
			return;
		}
		// A wall or trunk dead ahead (taller than it can step up): it hits it snout first.
		AABB ahead = boar.getBoundingBox().move(direction.x * WALL_LOOKAHEAD, boar.maxUpStep(), direction.z * WALL_LOOKAHEAD);
		if (ticks > 2 && (boar.horizontalCollision || !level.noCollision(boar, ahead))) {
			stun(level);
		} else if (ticks >= CHARGE_TICKS || boar.isInWater()) {
			enter(Phase.RECOVER);
		}
	}

	/** Rams one victim. Returns whether a raised shield took it (which dazes the boar). */
	private boolean ram(ServerLevel level, LivingEntity victim) {
		boolean shield = victim.isBlocking();
		float damage = (float) boar.getAttributeValue(Attributes.ATTACK_DAMAGE) * RAM_DAMAGE;
		DamageSource source = boar.damageSources().mobAttack(boar);
		if (victim.hurtServer(level, source, damage) || shield) {
			victim.knockback(RAM_KNOCKBACK * (shield ? 0.5 : 1.0), -direction.x, -direction.z, source, damage);
			victim.setDeltaMovement(victim.getDeltaMovement().add(0.0, shield ? 0.1 : 0.3, 0.0));
		}
		boar.setLastHurtMob(victim);
		level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), Boars.IMPACT, boar.getSoundSource(), 1.2f, 1.0f);
		return shield;
	}

	private void gore(ServerLevel level, LivingEntity target) {
		boar.getNavigation().stop();
		boar.getLookControl().setLookAt(target, 30.0f, 30.0f);
		if (goreCooldown == 0 && boar.isWithinMeleeAttackRange(target)) {
			goreCooldown = GORE_COOLDOWN;
			boar.swingForAttack(InteractionHand.MAIN_HAND);
			boar.doHurtTarget(level, target);
		}
	}

	private void stun(ServerLevel level) {
		enter(Phase.STUNNED);
		boar.setDeltaMovement(-direction.x * 0.25, 0.25, -direction.z * 0.25);
		boar.playSound(Boars.IMPACT, 1.2f, 0.8f);
		boar.playSound(Boars.STUNNED, 1.0f, boar.getVoicePitch());
		Vec3 front = boar.position().add(direction.scale(boar.getBbWidth())).add(0.0, boar.getBbHeight() * 0.6, 0.0);
		level.sendParticles(ParticleTypes.POOF, front.x, front.y, front.z, 8, 0.2, 0.2, 0.2, 0.02);
	}

	private void stunned(ServerLevel level) {
		boar.getNavigation().stop();
		Vec3 motion = boar.getDeltaMovement();
		boar.setDeltaMovement(motion.x * 0.5, motion.y, motion.z * 0.5);
		if (ticks % 6 == 0) {
			// Seeing stars: a little ring of sparks round its head.
			double angle = ticks * 0.6;
			Vec3 head = boar.getEyePosition().add(boar.getLookAngle().scale(0.4));
			level.sendParticles(ParticleTypes.CRIT, head.x + Math.cos(angle) * 0.4, head.y + 0.35, head.z + Math.sin(angle) * 0.4,
					1, 0.0, 0.0, 0.0, 0.0);
		}
		if (ticks >= STUN_TICKS) {
			enter(Phase.RECOVER);
		}
	}

	private void face(Vec3 point) {
		double dx = point.x - boar.getX();
		double dz = point.z - boar.getZ();
		float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
		boar.setYRot(Mth.rotLerp(0.4f, boar.getYRot(), yaw));
		boar.setYBodyRot(boar.getYRot());
	}

	/** Kicked-up dirt round its front hooves. */
	private void dirt(ServerLevel level, int count) {
		BlockPos below = boar.blockPosition().below();
		BlockState ground = level.getBlockState(below);
		if (ground.isAir()) {
			return;
		}
		Vec3 front = boar.position().add(boar.getLookAngle().multiply(1.0, 0.0, 1.0).normalize().scale(boar.getBbWidth() * 0.5));
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), front.x, front.y + 0.1, front.z, count, 0.2, 0.05, 0.2,
				0.1);
	}
}
