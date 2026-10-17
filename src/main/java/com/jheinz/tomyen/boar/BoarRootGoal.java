package com.jheinz.tomyen.boar;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Grazing: now and then a calm boar stops on grass or bare earth and roots about with its snout for a few seconds,
 * kicking up dirt. Sometimes it scuffs the grass into coarse dirt, and sometimes it turns something up (see
 * {@link Boar#finishRooting}).
 */
class BoarRootGoal extends Goal {
	private static final int ROOT_TICKS = 70;
	/** One in this many ticks a calm adult starts rooting (piglets more often: they're always at it). */
	private static final int CHANCE = 400;
	private static final int BABY_CHANCE = 200;

	private final Boar boar;
	private int ticks;

	BoarRootGoal(Boar boar) {
		this.boar = boar;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
	}

	@Override
	public boolean canUse() {
		if (boar.getTarget() != null || boar.state() != Boar.IDLE || !boar.onGround() || boar.isInLove()
				|| boar.getRandom().nextInt(reducedTickDelay(boar.isBaby() ? BABY_CHANCE : CHANCE)) != 0) {
			return false;
		}
		return Boar.canRootIn(boar.level().getBlockState(snout()));
	}

	@Override
	public boolean canContinueToUse() {
		return ticks < ROOT_TICKS && boar.getTarget() == null && boar.state() == Boar.ROOTING;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		ticks = 0;
		boar.setState(Boar.ROOTING);
		boar.getNavigation().stop();
	}

	@Override
	public void stop() {
		if (boar.state() == Boar.ROOTING) {
			boar.setState(Boar.IDLE);
		}
	}

	@Override
	public void tick() {
		ticks++;
		boar.getNavigation().stop();
		ServerLevel level = getServerLevel(boar);
		BlockPos ground = snout();
		BlockState block = level.getBlockState(ground);
		if (ticks % 12 == 6 && !block.isAir()) {
			Vec3 at = Vec3.atBottomCenterOf(ground.above());
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, block), at.x, at.y + 0.05, at.z, 5, 0.2, 0.05, 0.2, 0.05);
			if (ticks % 24 == 6) {
				boar.playSound(Boars.ROOT, 0.6f, boar.getVoicePitch());
			}
		}
		if (ticks == ROOT_TICKS - 10 && Boar.canRootIn(block)) {
			boar.finishRooting(level, ground);
		}
	}

	/** The ground under its snout: the block below, a little ahead. */
	private BlockPos snout() {
		Vec3 ahead = boar.position().add(boar.getLookAngle().multiply(1.0, 0.0, 1.0).normalize().scale(boar.getBbWidth() * 0.7));
		return BlockPos.containing(ahead.x, boar.getY() - 0.5, ahead.z);
	}
}
