package com.jheinz.tomyen.banshee;

import java.util.EnumSet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * The wail. Once her cooldown has run out and her quarry is in sight and within {@link #START_RANGE} blocks, she
 * stops, faces it and draws breath for {@link Banshee#WINDUP_TICKS} ticks (jaw dropping, hair lifting: the tell), then
 * screams for {@link Banshee#SCREAM_TICKS} (see {@link Banshee#scream}). Hitting her doesn't break it off.
 */
class BansheeWailGoal extends Goal {
	private static final double START_RANGE = 14.0;

	private final Banshee banshee;
	private int ticks;

	BansheeWailGoal(Banshee banshee) {
		this.banshee = banshee;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = banshee.getTarget();
		return banshee.canWail() && target != null && target.isAlive() && banshee.distanceToSqr(target) < START_RANGE * START_RANGE
				&& banshee.getSensing().hasLineOfSight(target);
	}

	@Override
	public boolean canContinueToUse() {
		return banshee.isAlive() && ticks < Banshee.WINDUP_TICKS + Banshee.SCREAM_TICKS;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		ticks = 0;
		banshee.getNavigation().stop();
		banshee.startWindup();
	}

	@Override
	public void tick() {
		ticks++;
		banshee.getNavigation().stop();
		LivingEntity target = banshee.getTarget();
		if (target != null) {
			banshee.getLookControl().setLookAt(target, 30.0f, 30.0f);
		}
		if (ticks == Banshee.WINDUP_TICKS && banshee.level() instanceof ServerLevel level) {
			banshee.scream(level);
		}
	}

	@Override
	public void stop() {
		if (banshee.isAlive()) {
			banshee.setWailState(Banshee.IDLE);
		}
		banshee.resetWailCooldown();
	}
}
