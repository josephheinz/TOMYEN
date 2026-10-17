package com.jheinz.tomyen.moose;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Now and then, a moose with nothing else to do picks a tree nearby, walks up to it and browses: neck stretched up
 * into the leaves, chewing, a few leaves falling. It doesn't strip them (the trees are none the worse), but a meal of
 * leaves mends it a little.
 */
class MooseBrowseGoal extends Goal {
	private static final int SEARCH = 7;
	private static final double CLOSE_ENOUGH = 2.3;

	private final Moose moose;
	private @Nullable BlockPos leaves;
	private int browsing;
	private int giveUp;

	MooseBrowseGoal(Moose moose) {
		this.moose = moose;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (moose.isVehicle() || moose.getTarget() != null || moose.isInLove() || moose.getRandom().nextInt(reducedTickDelay(300)) != 0) {
			return false;
		}
		leaves = findLeaves();
		return leaves != null;
	}

	@Override
	public boolean canContinueToUse() {
		return leaves != null && browsing > 0 && giveUp > 0 && moose.getTarget() == null && !moose.isVehicle()
				&& moose.level().getBlockState(leaves).is(BlockTags.LEAVES);
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		browsing = 60 + moose.getRandom().nextInt(60);
		giveUp = 200;
		moose.getNavigation().moveTo(leaves.getX() + 0.5, moose.getY(), leaves.getZ() + 0.5, 0.9);
	}

	@Override
	public void stop() {
		if (moose.action() == Moose.BROWSING) {
			moose.setAction(Moose.IDLE);
		}
		leaves = null;
		moose.getNavigation().stop();
	}

	@Override
	public void tick() {
		Vec3 at = Vec3.atCenterOf(leaves);
		moose.getLookControl().setLookAt(at.x, at.y, at.z, 20.0f, 40.0f);
		double dx = at.x - moose.getX();
		double dz = at.z - moose.getZ();
		if (dx * dx + dz * dz > CLOSE_ENOUGH * CLOSE_ENOUGH) {
			giveUp--;
			if (moose.getNavigation().isDone()) {
				moose.getNavigation().moveTo(at.x, moose.getY(), at.z, 0.9);
			}
			return;
		}
		moose.getNavigation().stop();
		moose.setAction(Moose.BROWSING);
		if (--browsing % 20 == 0 && moose.level() instanceof ServerLevel level) {
			moose.playSound(Mooses.EAT, 0.8f, 0.9f + moose.getRandom().nextFloat() * 0.2f);
			BlockState state = level.getBlockState(leaves);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y - 0.3, at.z, 6, 0.3, 0.2, 0.3, 0.02);
		}
		if (browsing <= 0) {
			moose.heal(2.0f);
		}
	}

	/** The nearest leaves at browsing height (one to four blocks up) within reach of somewhere it can stand. */
	private @Nullable BlockPos findLeaves() {
		BlockPos origin = moose.blockPosition();
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
		for (int dx = -SEARCH; dx <= SEARCH; dx++) {
			for (int dz = -SEARCH; dz <= SEARCH; dz++) {
				for (int dy = 1; dy <= 4; dy++) {
					probe.setWithOffset(origin, dx, dy, dz);
					if (!moose.level().getBlockState(probe).is(BlockTags.LEAVES)) {
						continue;
					}
					double distance = dx * dx + dz * dz + dy * 0.5;
					if (distance < bestDistance) {
						bestDistance = distance;
						best = probe.immutable();
					}
					break;
				}
			}
		}
		return best;
	}
}
