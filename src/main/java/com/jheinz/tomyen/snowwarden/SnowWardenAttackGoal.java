package com.jheinz.tomyen.snowwarden;

import java.util.EnumSet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * How the Snow Warden fights.
 *
 * <p>Meeting someone for the first time it stops, sniffs the air ({@link SnowWarden#SNIFFING}) and roars
 * ({@link SnowWarden#ROARING}); after that, until it calms down, it goes straight in. It lumbers after its target, and:
 * <ul>
 * <li>within reach, swings a fist overhead and down, right then left;</li>
 * <li>with two or more people within slam range, or now and then when someone's close, raises both fists and slams the
 * ground ({@link SnowWarden#slam});</li>
 * <li>with its target in sight between {@value #BLAST_MIN} and {@link SnowWarden#BLAST_RANGE} blocks off, leans back,
 * chest crystals blazing, for {@value SnowWarden#CHARGE_TICKS} ticks (step behind something and it fires anyway: the
 * beam goes through walls) and fires the frost blast ({@link SnowWarden#frostBlast});</li>
 * <li>with its target further off than a swing, lobs a {@link FrostBoulder}.</li>
 * </ul>
 * Each has its own cooldown, so a fight cycles through them.
 */
class SnowWardenAttackGoal extends Goal {
	private enum Phase { APPROACH, SNIFF, ROAR, SWING, SLAM, CHARGE, BLAST, THROW }

	private static final double BLAST_MIN = 5.0;
	private static final double THROW_MIN = 6.0;
	private static final double THROW_MAX = 26.0;
	private static final double SLAM_WITHIN = 4.5;

	private final SnowWarden warden;
	private Phase phase = Phase.APPROACH;
	private int timer;
	private int swingCooldown;
	private int slamCooldown;
	private int blastCooldown;
	private int throwCooldown;
	private int repath;
	private boolean leftNext;

	SnowWardenAttackGoal(SnowWarden warden) {
		this.warden = warden;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
	}

	@Override
	public boolean canUse() {
		return warden.fair(warden.getTarget());
	}

	@Override
	public boolean canContinueToUse() {
		// A blow under way finishes whatever happens to the target.
		return phase != Phase.APPROACH || warden.fair(warden.getTarget());
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		timer = 0;
		repath = 0;
		swingCooldown = 0;
		slamCooldown = 60;
		blastCooldown = 60 + warden.getRandom().nextInt(60);
		throwCooldown = 40;
		warden.setAggressive(true);
		if (warden.isAlerted()) {
			phase = Phase.APPROACH;
		} else {
			begin(Phase.SNIFF, SnowWarden.SNIFFING, SnowWarden.SNIFF_TICKS);
			warden.playSound(SnowWardens.SNIFF, 1.6f, 0.9f + warden.getRandom().nextFloat() * 0.15f);
		}
	}

	@Override
	public void stop() {
		phase = Phase.APPROACH;
		warden.setAction(SnowWarden.IDLE);
		warden.getNavigation().stop();
		warden.setAggressive(false);
	}

	@Override
	public void tick() {
		LivingEntity target = warden.getTarget();
		ServerLevel level = getServerLevel(warden);
		swingCooldown = Math.max(0, swingCooldown - 1);
		slamCooldown = Math.max(0, slamCooldown - 1);
		blastCooldown = Math.max(0, blastCooldown - 1);
		throwCooldown = Math.max(0, throwCooldown - 1);
		if (phase != Phase.APPROACH) {
			warden.getNavigation().stop();
			if (target != null) {
				face(target.position().subtract(warden.position()));
				warden.getLookControl().setLookAt(target, 30.0f, 30.0f);
			}
		}
		int elapsed = phaseTicks() - timer;
		switch (phase) {
			case APPROACH -> approach(level, target);
			case SNIFF -> {
				if (elapsed == SnowWarden.SNIFF_TICKS / 2) {
					warden.playSound(SnowWardens.SNIFF, 1.6f, 0.85f + warden.getRandom().nextFloat() * 0.15f);
				}
				if (--timer <= 0) {
					begin(Phase.ROAR, SnowWarden.ROARING, SnowWarden.ROAR_TICKS);
					warden.setAlerted();
				}
			}
			case ROAR -> {
				if (elapsed == 6) {
					warden.roar(level);
				}
				if (--timer <= 0) {
					idle();
				}
			}
			case SWING -> {
				if (elapsed == SnowWarden.SWING_HITS) {
					warden.swingHit(level, target);
				}
				if (--timer <= 0) {
					idle();
					swingCooldown = 6;
				}
			}
			case SLAM -> {
				if (elapsed == SnowWarden.SLAM_LANDS) {
					warden.slam(level);
				}
				if (--timer <= 0) {
					idle();
					slamCooldown = 100 + warden.getRandom().nextInt(60);
					swingCooldown = 10;
				}
			}
			case CHARGE -> {
				if (elapsed % 3 == 0) {
					warden.chargeParticles(level);
				}
				if (--timer <= 0) {
					begin(Phase.BLAST, SnowWarden.BLASTING, SnowWarden.BLAST_TICKS);
					warden.frostBlast(level, target);
				}
			}
			case BLAST -> {
				if (--timer <= 0) {
					idle();
					blastCooldown = 160 + warden.getRandom().nextInt(80);
					throwCooldown = Math.max(throwCooldown, 40);
				}
			}
			case THROW -> {
				if (elapsed == SnowWarden.THROW_RELEASE && warden.fair(target)) {
					warden.throwBoulder(level, target);
				}
				if (--timer <= 0) {
					idle();
					throwCooldown = 70 + warden.getRandom().nextInt(50);
				}
			}
		}
	}

	private void approach(ServerLevel level, LivingEntity target) {
		if (target == null) {
			return;
		}
		warden.getLookControl().setLookAt(target, 30.0f, 30.0f);
		double distance = warden.distanceTo(target);
		boolean sees = warden.getSensing().hasLineOfSight(target);
		boolean reach = warden.isWithinMeleeAttackRange(target);
		int crowd = level.getEntitiesOfClass(Player.class, warden.getBoundingBox().inflate(SnowWarden.SLAM_RADIUS),
				p -> warden.fair(p)).size();
		if (slamCooldown <= 0 && distance <= SLAM_WITHIN && (crowd >= 2 || warden.getRandom().nextInt(4) == 0)) {
			begin(Phase.SLAM, SnowWarden.SLAMMING, SnowWarden.SLAM_TICKS);
			return;
		}
		if (reach && swingCooldown <= 0) {
			begin(Phase.SWING, leftNext ? SnowWarden.SWING_LEFT : SnowWarden.SWING_RIGHT, SnowWarden.SWING_TICKS);
			leftNext = !leftNext;
			return;
		}
		if (blastCooldown <= 0 && sees && distance >= BLAST_MIN && distance <= SnowWarden.BLAST_RANGE) {
			begin(Phase.CHARGE, SnowWarden.CHARGING, SnowWarden.CHARGE_TICKS);
			warden.playSound(SnowWardens.BLAST_CHARGE, 2.0f, 1.0f);
			return;
		}
		if (throwCooldown <= 0 && sees && distance >= THROW_MIN && distance <= THROW_MAX) {
			begin(Phase.THROW, SnowWarden.THROWING, SnowWarden.THROW_TICKS);
			return;
		}
		if (!reach && --repath <= 0) {
			repath = 4 + warden.getRandom().nextInt(6);
			warden.getNavigation().moveTo(target, 1.15);
		}
	}

	private void begin(Phase next, byte action, int ticks) {
		phase = next;
		timer = ticks;
		warden.setAction(action);
		warden.getNavigation().stop();
	}

	private void idle() {
		phase = Phase.APPROACH;
		warden.setAction(SnowWarden.IDLE);
	}

	private int phaseTicks() {
		return switch (phase) {
			case APPROACH -> 0;
			case SNIFF -> SnowWarden.SNIFF_TICKS;
			case ROAR -> SnowWarden.ROAR_TICKS;
			case SWING -> SnowWarden.SWING_TICKS;
			case SLAM -> SnowWarden.SLAM_TICKS;
			case CHARGE -> SnowWarden.CHARGE_TICKS;
			case BLAST -> SnowWarden.BLAST_TICKS;
			case THROW -> SnowWarden.THROW_TICKS;
		};
	}

	/** Turns its whole body to face along a direction. */
	private void face(Vec3 toward) {
		if (toward.horizontalDistanceSqr() < 1.0E-6) {
			return;
		}
		float yaw = (float) (Mth.atan2(toward.z, toward.x) * Mth.RAD_TO_DEG) - 90.0f;
		warden.setYRot(Mth.rotLerp(0.3f, warden.getYRot(), yaw));
		warden.yBodyRot = warden.getYRot();
		warden.yHeadRot = warden.getYRot();
	}
}
