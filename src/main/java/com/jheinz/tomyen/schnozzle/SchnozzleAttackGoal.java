package com.jheinz.tomyen.schnozzle;

import java.util.EnumSet;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.silver.HolySilverArmor;

/**
 * How the Schnozzle fights. It circles its target at range, drifting round and changing direction now and then,
 * and every {@value #VOLLEY_COOLDOWN}-odd ticks sniffs in hard for {@value #SNIFF_TICKS} ticks (nostrils flaring),
 * then fires {@value #MIN_SHOTS}-{@value #MAX_SHOTS} snot rockets from alternate nostrils. Anything within reach
 * of its tentacles gets lashed; up close it sometimes dives in to lash. Every so often, with the target near, it
 * rears back for {@value #SNEEZE_WINDUP_TICKS} ticks and sneezes, blasting everything in a cone in front of it away.
 */
class SchnozzleAttackGoal extends Goal {
	private static final int SNIFF_TICKS = 20;
	private static final int SHOT_INTERVAL = 6;
	private static final int MIN_SHOTS = 2;
	private static final int MAX_SHOTS = 4;
	private static final int VOLLEY_COOLDOWN = 90;
	private static final double VOLLEY_RANGE = 40.0;
	private static final int LASH_COOLDOWN = 25;
	/** How far its tentacles reach sideways from its middle, and down below it. */
	private static final double LASH_REACH = 4.0;
	private static final double LASH_DEPTH = 5.0;
	private static final int SNEEZE_WINDUP_TICKS = 30;
	private static final int SNEEZE_COOLDOWN = 300;
	private static final double SNEEZE_TRIGGER_RANGE = 11.0;
	private static final double SNEEZE_RANGE = 13.0;
	/** Cosine of the sneeze cone's half-angle (45 degrees). */
	private static final double SNEEZE_CONE_COS = 0.707;
	private static final float SNEEZE_DAMAGE = 3.0f;
	private static final double SNEEZE_PUSH = 2.6;
	private static final double ORBIT_RADIUS = 11.0;
	private static final int DIVE_TICKS = 60;

	private final Schnozzle schnozzle;
	private int stateTicks;
	private int shotsLeft;
	private int nostril;
	private int volleyCooldown;
	private int sneezeCooldown;
	private int lashCooldown;
	private int diveTicks;
	private int repathTicks;
	private float orbitAngle;
	/** Which way it circles: 1 or -1. */
	private int orbitDirection = 1;

	SchnozzleAttackGoal(Schnozzle schnozzle) {
		this.schnozzle = schnozzle;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = schnozzle.getTarget();
		return target != null && target.isAlive() && schnozzle.canAttack(target);
	}

	@Override
	public boolean canContinueToUse() {
		LivingEntity target = schnozzle.getTarget();
		if (target == null || !target.isAlive() || !schnozzle.canAttack(target)) {
			return false;
		}
		return !(target instanceof Player player) || !player.isCreative() && !player.isSpectator();
	}

	@Override
	public void start() {
		LivingEntity target = schnozzle.getTarget();
		Vec3 offset = schnozzle.position().subtract(target.position());
		orbitAngle = (float) Math.atan2(offset.z, offset.x);
		orbitDirection = schnozzle.getRandom().nextBoolean() ? 1 : -1;
		volleyCooldown = 30 + schnozzle.getRandom().nextInt(30);
		sneezeCooldown = 160 + schnozzle.getRandom().nextInt(200);
		repathTicks = 0;
		schnozzle.setAggressive(true);
	}

	@Override
	public void stop() {
		schnozzle.setAttackState(Schnozzle.IDLE);
		schnozzle.setAggressive(false);
		schnozzle.getNavigation().stop();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		LivingEntity target = schnozzle.getTarget();
		if (target == null) {
			return;
		}
		ServerLevel level = (ServerLevel) schnozzle.level();
		schnozzle.getLookControl().setLookAt(target, 30.0f, 30.0f);
		volleyCooldown = Math.max(0, volleyCooldown - 1);
		sneezeCooldown = Math.max(0, sneezeCooldown - 1);
		lashCooldown = Math.max(0, lashCooldown - 1);
		repathTicks = Math.max(0, repathTicks - 1);
		boolean sees = schnozzle.getSensing().hasLineOfSight(target);
		double distance = schnozzle.distanceTo(target);

		if (schnozzle.attackState() == Schnozzle.SNEEZE_WINDUP) {
			// Holds still, rearing back and drawing breath.
			schnozzle.getNavigation().stop();
			schnozzle.setDeltaMovement(schnozzle.getDeltaMovement().scale(0.7));
			if (--stateTicks <= 0) {
				sneeze(level, target);
				schnozzle.setAttackState(Schnozzle.IDLE);
				sneezeCooldown = SNEEZE_COOLDOWN + schnozzle.getRandom().nextInt(200);
				volleyCooldown = Math.max(volleyCooldown, 30);
			}
			return;
		}

		if (lashCooldown == 0 && inLashReach(target) && sees) {
			schnozzle.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			schnozzle.doHurtTarget(level, target);
			lashCooldown = LASH_COOLDOWN;
			diveTicks = 0;
		}

		switch (schnozzle.attackState()) {
			case Schnozzle.SNIFFING -> {
				if (--stateTicks <= 0) {
					schnozzle.setAttackState(Schnozzle.FIRING);
					shotsLeft = MIN_SHOTS + schnozzle.getRandom().nextInt(MAX_SHOTS - MIN_SHOTS + 1);
					stateTicks = 0;
				}
			}
			case Schnozzle.FIRING -> {
				if (--stateTicks <= 0) {
					fire(level, target, nostril);
					nostril = 1 - nostril;
					stateTicks = SHOT_INTERVAL;
					if (--shotsLeft <= 0) {
						schnozzle.setAttackState(Schnozzle.IDLE);
						volleyCooldown = VOLLEY_COOLDOWN + schnozzle.getRandom().nextInt(50);
					}
				}
			}
			default -> {
				if (sneezeCooldown == 0 && sees && distance < SNEEZE_TRIGGER_RANGE) {
					schnozzle.setAttackState(Schnozzle.SNEEZE_WINDUP);
					stateTicks = SNEEZE_WINDUP_TICKS;
					schnozzle.playSound(Schnozzles.SNEEZE_WINDUP, 3.0f, 0.9f + schnozzle.getRandom().nextFloat() * 0.1f);
					return;
				}
				if (volleyCooldown == 0 && sees && distance < VOLLEY_RANGE) {
					schnozzle.setAttackState(Schnozzle.SNIFFING);
					stateTicks = SNIFF_TICKS;
					schnozzle.playSound(Schnozzles.SNIFF, 3.0f, 0.9f + schnozzle.getRandom().nextFloat() * 0.15f);
				}
			}
		}
		move(target, distance);
	}

	/** Dives in to lash now and then when the target's close; otherwise circles it at range, at cruising height. */
	private void move(LivingEntity target, double distance) {
		if (diveTicks > 0) {
			diveTicks--;
			if (repathTicks == 0) {
				schnozzle.getNavigation().moveTo(target.getX(), target.getY() + 2.0, target.getZ(), 1.6);
				repathTicks = 5;
			}
			return;
		}
		if (schnozzle.attackState() == Schnozzle.IDLE && lashCooldown == 0 && distance < 10.0 && schnozzle.getRandom().nextInt(60) == 0) {
			diveTicks = DIVE_TICKS;
			repathTicks = 0;
			return;
		}
		if (repathTicks == 0) {
			if (schnozzle.getRandom().nextInt(6) == 0) {
				orbitDirection = -orbitDirection;
			}
			orbitAngle += orbitDirection * 25.0f * Mth.DEG_TO_RAD;
			double x = target.getX() + Mth.cos(orbitAngle) * ORBIT_RADIUS;
			double z = target.getZ() + Mth.sin(orbitAngle) * ORBIT_RADIUS;
			double y = Math.max(schnozzle.cruiseY(x, z), target.getY() + 3.0);
			if (!schnozzle.getNavigation().moveTo(x, y, z, 1.3)) {
				schnozzle.getMoveControl().setWantedPosition(x, y, z, 1.3);
			}
			repathTicks = 20;
		}
	}

	private boolean inLashReach(LivingEntity target) {
		double dx = target.getX() - schnozzle.getX();
		double dz = target.getZ() - schnozzle.getZ();
		double dy = target.getY() - schnozzle.getY();
		return dx * dx + dz * dz < LASH_REACH * LASH_REACH && dy > -LASH_DEPTH && dy < schnozzle.getBbHeight();
	}

	/** One snot rocket out of a nostril, kicked out to that side and up so the volley curls in on the target. */
	private void fire(ServerLevel level, LivingEntity target, int side) {
		Vec3 from = schnozzle.nostril(side);
		Vec3 aim = target.getBoundingBox().getCenter().subtract(from).normalize();
		Vec3 right = new Vec3(-aim.z, 0.0, aim.x);
		if (right.lengthSqr() > 1.0E-4) {
			right = right.normalize();
		}
		Vec3 direction = aim.add(right.scale(side == 0 ? -0.35 : 0.35)).add(0.0, 0.25, 0.0).normalize();
		SnotRocket rocket = new SnotRocket(level, schnozzle, from, target);
		rocket.launch(direction.scale(SnotRocket.SPEED));
		level.addFreshEntity(rocket);
		level.playSound(null, from.x, from.y, from.z, Schnozzles.LAUNCH, SoundSource.HOSTILE, 2.0f, 0.8f + schnozzle.getRandom().nextFloat() * 0.3f);
		level.sendParticles(ParticleTypes.ITEM_SLIME, from.x, from.y, from.z, 8, 0.15, 0.15, 0.15, 0.1);
		level.sendParticles(ParticleTypes.CLOUD, from.x, from.y, from.z, 4, 0.1, 0.1, 0.1, 0.05);
	}

	/** Everything living in a cone in front of the nose takes a little damage and is blown away, coated in snot. */
	private void sneeze(ServerLevel level, LivingEntity target) {
		Vec3 origin = schnozzle.nostril(0).add(schnozzle.nostril(1)).scale(0.5);
		Vec3 direction = target.getBoundingBox().getCenter().subtract(origin).normalize();
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, schnozzle.getBoundingBox().inflate(SNEEZE_RANGE),
				victim -> victim != schnozzle && victim.isAlive() && !victim.isSpectator())) {
			Vec3 to = victim.getBoundingBox().getCenter().subtract(origin);
			double distance = to.length();
			if (distance > SNEEZE_RANGE || distance < 1.0E-3 || to.scale(1.0 / distance).dot(direction) < SNEEZE_CONE_COS) {
				continue;
			}
			if (victim instanceof Player player && player.isCreative()) {
				continue;
			}
			victim.hurtServer(level, schnozzle.damageSources().mobAttack(schnozzle), SNEEZE_DAMAGE);
			if (HolySilverArmor.fullSet(victim)) {
				// Holy silver turns the whole blast aside, not just its damage.
				continue;
			}
			double resist = Mth.clamp(victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0);
			double push = SNEEZE_PUSH * (1.0 - 0.6 * distance / SNEEZE_RANGE) * (1.0 - 0.7 * resist);
			victim.push(to.scale(1.0 / distance).scale(push).add(0.0, 0.5 * push / SNEEZE_PUSH, 0.0));
			victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0), schnozzle);
		}

		var random = schnozzle.getRandom();
		for (int i = 0; i < 40; i++) {
			Vec3 spray = direction.add(random.nextGaussian() * 0.3, random.nextGaussian() * 0.3, random.nextGaussian() * 0.3).normalize();
			level.sendParticles(ParticleTypes.SNEEZE, origin.x, origin.y, origin.z, 0, spray.x, spray.y, spray.z, 0.6 + random.nextDouble() * 0.6);
		}
		for (int i = 1; i <= 6; i++) {
			Vec3 along = origin.add(direction.scale(i * SNEEZE_RANGE / 7.0));
			level.sendParticles(ParticleTypes.ITEM_SLIME, along.x, along.y, along.z, 10 + i * 2, 0.3 * i, 0.3 * i, 0.3 * i, 0.1);
		}
		level.sendParticles(ParticleTypes.GUST, origin.x, origin.y, origin.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, origin.x, origin.y, origin.z, Schnozzles.SNEEZE, SoundSource.HOSTILE, 4.0f, 0.8f + random.nextFloat() * 0.15f);
	}
}
