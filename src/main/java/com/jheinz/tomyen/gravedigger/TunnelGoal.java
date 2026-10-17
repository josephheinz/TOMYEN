package com.jheinz.tomyen.gravedigger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * The grave digger's way in: when he has a target he can't walk to (shut in a house, behind a wall, across a moat), he
 * digs down {@code 2-3} blocks, tunnels toward them under whatever's in the way, and digs back up through the floor
 * beside them.
 * <p>
 * He goes a block at a time, a two-high cell (feet and head), and moves himself through the cells he's cleared (no
 * gravity while he's underground). He can only dig {@link #canDig diggable} blocks: soft ground quickly,
 * natural stone slowly, never anything in {@code #tomyen:grave_digger_proof}, nothing with a block entity, and nothing
 * that would let water or lava in. Blocked, he tries the other way round, then deeper (down to
 * {@link #MAX_EXTRA_DEPTH} below the planned level), then sideways; blocked coming up, he tries the next column over.
 * Everything he digs is destroyed without drops, and only while {@code mobGriefing} is on.
 * <p>
 * Underground he gives himself away: muffled digging under the floor above him, and, as he closes in, cracks spreading
 * through the floor at their feet and dirt spitting up through it.
 */
final class TunnelGoal extends Goal {
	/** How often (ticks) he checks whether he can get at his target. */
	private static final int CHECK_INTERVAL = 10;
	/** He has to have had no way through for this many checks in a row before he starts digging. */
	private static final int STUCK_CHECKS = 4;
	/** He'll only dig toward a target this close (blocks, horizontally). */
	private static final double MAX_RANGE = 20.0;
	/** ...this far above him, or this far below. */
	private static final int MAX_RISE = 6;
	private static final int MAX_DROP = 8;
	/** How much deeper than planned he'll go to get under foundations. */
	private static final int MAX_EXTRA_DEPTH = 3;
	/** The most cells a tunnel runs before he gives up. */
	private static final int MAX_STEPS = 48;
	/** The most times he'll go round something. */
	private static final int MAX_DETOURS = 8;
	/** Closer than this (in cells) the floor at the target's feet starts to crack. */
	private static final int WARN_DISTANCE = 5;
	/** Ticks to move from one cell to the next. */
	private static final int GLIDE_TICKS = 8;
	/** Muffled digging under the floor, once in this many ticks. */
	private static final int MUFFLED_INTERVAL = 25;
	private static final int DONE_COOLDOWN = 100;
	private static final int FAIL_COOLDOWN = 400;

	private enum Phase {
		DESCEND, TUNNEL, ASCEND, EMERGE
	}

	private enum Dig {
		DIGGING, DONE, BLOCKED
	}

	private final GraveDigger digger;
	private long nextCheck;
	private int stuckChecks;

	private @Nullable Phase phase;
	private boolean finished;
	private boolean succeeded;
	private int startY;
	private int tunnelY;
	private int lowestY;
	private int goalY;
	private int steps;
	private int detours;
	private int ticks;
	/** The cell he last left: he doesn't go back into it. */
	private @Nullable BlockPos previous;
	/** The way he's stepping, kept until the step is done or blocked. */
	private @Nullable Direction stepping;
	private @Nullable BlockPos digPos;
	private int digTicks;
	private int digNeeded;
	private @Nullable Vec3 glideFrom;
	private @Nullable Vec3 glideTo;
	private int glideTick;
	private boolean finishAfterGlide;
	private @Nullable BlockPos crackPos;

	TunnelGoal(GraveDigger digger) {
		this.digger = digger;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
	}

	@Override
	public boolean canUse() {
		ServerLevel level = getServerLevel(digger);
		long now = level.getGameTime();
		if (now < nextCheck) {
			return false;
		}
		nextCheck = now + CHECK_INTERVAL;
		LivingEntity target = digger.getTarget();
		if (!griefing(level) || target == null || !target.isAlive() || !digger.onGround() || digger.isInWater()) {
			stuckChecks = 0;
			return false;
		}
		double dx = target.getX() - digger.getX();
		double dz = target.getZ() - digger.getZ();
		int rise = target.getBlockY() - digger.getBlockY();
		if (dx * dx + dz * dz > MAX_RANGE * MAX_RANGE || rise > MAX_RISE || rise < -MAX_DROP || digger.distanceToSqr(target) < 4.0) {
			stuckChecks = 0;
			return false;
		}
		Path path = digger.getNavigation().createPath(target, 0);
		if (path != null && path.canReach()) {
			stuckChecks = 0;
			return false;
		}
		if (++stuckChecks < STUCK_CHECKS) {
			return false;
		}
		stuckChecks = 0;
		BlockPos feet = digger.blockPosition();
		return canDig(level, feet.below()) && passable(level, feet) && passable(level, feet.above());
	}

	@Override
	public boolean canContinueToUse() {
		return !finished && digger.isAlive();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		LivingEntity target = digger.getTarget();
		BlockPos feet = digger.blockPosition();
		phase = Phase.DESCEND;
		finished = false;
		succeeded = false;
		finishAfterGlide = false;
		steps = 0;
		detours = 0;
		ticks = 0;
		previous = null;
		stepping = null;
		startY = feet.getY();
		int depth = 2 + digger.getRandom().nextInt(2);
		tunnelY = Math.min(feet.getY(), target != null ? target.getBlockY() : feet.getY()) - depth;
		lowestY = tunnelY - MAX_EXTRA_DEPTH;
		digger.getNavigation().stop();
		digger.setTunnelling(true);
		// Square up over the middle of the block first, so he drops cleanly into a one-wide hole.
		glide(Vec3.atBottomCenterOf(feet));
	}

	@Override
	public void stop() {
		ServerLevel level = getServerLevel(digger);
		clearDig(level);
		clearCrack(level);
		digger.setTunnelling(false);
		digger.setDigMode(GraveDigger.DigMode.NONE);
		digger.setDeltaMovement(Vec3.ZERO);
		glideTo = null;
		stepping = null;
		phase = null;
		nextCheck = level.getGameTime() + (succeeded ? DONE_COOLDOWN : FAIL_COOLDOWN);
	}

	@Override
	public void tick() {
		ServerLevel level = getServerLevel(digger);
		LivingEntity target = digger.getTarget();
		if (target == null || !target.isAlive() || !griefing(level) || phase == null) {
			finish(false);
			return;
		}
		ticks++;
		digger.getNavigation().stop();
		digger.setDeltaMovement(Vec3.ZERO);
		BlockPos feet = digger.blockPosition();
		warn(level, feet, target);
		if (glideTo != null) {
			glideStep();
			return;
		}
		// They came to him: fight in the open.
		if (digger.distanceToSqr(target) < 3.0 && digger.hasLineOfSight(target)) {
			finish(true);
			return;
		}
		switch (phase) {
			case DESCEND -> descend(level, feet);
			case TUNNEL -> tunnel(level, feet, target);
			case ASCEND -> ascend(level, feet, target);
			case EMERGE -> emerge(level, feet, target);
		}
	}

	// ------------------------------------------------------------------------------------------- the phases

	private void descend(ServerLevel level, BlockPos feet) {
		if (feet.getY() <= tunnelY) {
			phase = Phase.TUNNEL;
			return;
		}
		digger.setDigMode(GraveDigger.DigMode.DOWN);
		BlockPos below = feet.below();
		face(Vec3.atCenterOf(below).add(digger.getLookAngle()));
		switch (dig(level, below)) {
			case DONE -> glide(Vec3.atBottomCenterOf(below));
			case BLOCKED -> {
				// Can't get any deeper here: tunnel from where he is if he's deep enough to pass under a wall.
				if (feet.getY() <= startY - 2) {
					tunnelY = feet.getY();
					phase = Phase.TUNNEL;
				} else {
					finish(false);
				}
			}
			case DIGGING -> {
			}
		}
	}

	private void tunnel(ServerLevel level, BlockPos feet, LivingEntity target) {
		digger.setDigMode(GraveDigger.DigMode.FORWARD);
		BlockPos goal = target.blockPosition();
		int dx = goal.getX() - feet.getX();
		int dz = goal.getZ() - feet.getZ();
		if (stepping == null && Math.abs(dx) + Math.abs(dz) <= 1) {
			goalY = Math.max(goal.getY(), feet.getY() + 1);
			phase = Phase.ASCEND;
			return;
		}
		if (steps >= MAX_STEPS || detours > MAX_DETOURS) {
			finish(false);
			return;
		}
		if (stepping != null) {
			Dig result = step(level, feet, stepping);
			if (result != Dig.DIGGING) {
				stepping = null;
			}
			if (result != Dig.BLOCKED) {
				return;
			}
		}
		// Straight at them, the long way first; the other way round; deeper, under foundations; then sideways.
		Direction primary = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
		Direction secondary = Math.abs(dx) >= Math.abs(dz) ? (dz > 0 ? Direction.SOUTH : dz < 0 ? Direction.NORTH : null)
				: (dx > 0 ? Direction.EAST : dx < 0 ? Direction.WEST : null);
		for (Direction way : new Direction[] {primary, secondary}) {
			if (way != null && open(level, feet.relative(way)) && !feet.relative(way).equals(previous)) {
				stepping = way;
				return;
			}
		}
		if (feet.getY() > lowestY && canDig(level, feet.below())) {
			tunnelY = feet.getY() - 1;
			digger.setDigMode(GraveDigger.DigMode.DOWN);
			if (dig(level, feet.below()) == Dig.DONE) {
				glide(Vec3.atBottomCenterOf(feet.below()));
			}
			return;
		}
		for (Direction way : new Direction[] {primary.getClockWise(), primary.getCounterClockWise()}) {
			if (open(level, feet.relative(way)) && !feet.relative(way).equals(previous)) {
				detours++;
				stepping = way;
				return;
			}
		}
		finish(false);
	}

	private void ascend(ServerLevel level, BlockPos feet, LivingEntity target) {
		digger.setDigMode(GraveDigger.DigMode.UP);
		if (stepping != null) {
			// Coming up one column over, round a floor he can't get through.
			Dig result = step(level, feet, stepping);
			if (result != Dig.DIGGING) {
				stepping = null;
			}
			return;
		}
		if (feet.getY() >= goalY) {
			phase = Phase.EMERGE;
			return;
		}
		BlockPos above = feet.above(2);
		face(Vec3.atCenterOf(above).add(digger.getLookAngle()));
		Dig result = dig(level, above);
		if (result == Dig.DONE) {
			glide(Vec3.atBottomCenterOf(feet.above()));
		} else if (result == Dig.BLOCKED) {
			if (++detours > MAX_DETOURS) {
				finish(false);
				return;
			}
			for (Direction way : towards(feet, target.blockPosition())) {
				if (open(level, feet.relative(way)) && open(level, feet.relative(way).above(2))
						&& !feet.relative(way).equals(previous)) {
					stepping = way;
					return;
				}
			}
			finish(false);
		}
	}

	/** Up through the floor: off the hole he came out of and onto solid floor, as near them as he can. */
	private void emerge(ServerLevel level, BlockPos feet, LivingEntity target) {
		digger.setDigMode(GraveDigger.DigMode.NONE);
		for (Direction way : towards(feet, target.blockPosition())) {
			BlockPos next = feet.relative(way);
			if (passable(level, next) && passable(level, next.above()) && !passable(level, next.below())) {
				finishAfterGlide = true;
				glide(Vec3.atBottomCenterOf(next));
				return;
			}
		}
		finish(true);
	}

	// ------------------------------------------------------------------------------------------- moving and digging

	/** Clears the next cell (feet and head) that way, a block at a time, then moves into it. */
	private Dig step(ServerLevel level, BlockPos feet, Direction way) {
		BlockPos next = feet.relative(way);
		face(Vec3.atCenterOf(next.above()));
		for (BlockPos cell : new BlockPos[] {next.above(), next}) {
			if (!passable(level, cell)) {
				Dig result = dig(level, cell);
				return result == Dig.BLOCKED ? Dig.BLOCKED : Dig.DIGGING;
			}
		}
		steps++;
		glide(Vec3.atBottomCenterOf(next));
		return Dig.DONE;
	}

	/** A cell he can get into: each of its two blocks open already or diggable. */
	private static boolean open(ServerLevel level, BlockPos feet) {
		return (passable(level, feet) || canDig(level, feet)) && (passable(level, feet.above()) || canDig(level, feet.above()));
	}

	/** One tick's work on a block: the shovel's scrape, crumbs of it flying, the crack spreading; then it's gone. */
	private Dig dig(ServerLevel level, BlockPos pos) {
		if (passable(level, pos)) {
			clearDig(level);
			return Dig.DONE;
		}
		if (!canDig(level, pos)) {
			clearDig(level);
			return Dig.BLOCKED;
		}
		BlockState state = level.getBlockState(pos);
		if (!pos.equals(digPos)) {
			clearDig(level);
			digPos = pos.immutable();
			digTicks = 0;
			digNeeded = digTicks(level, pos, state);
		}
		digTicks++;
		if (digTicks % 4 == 1) {
			level.playSound(null, pos, state.getSoundType().getHitSound(), SoundSource.HOSTILE, 0.5f, 0.7f);
		}
		if (digTicks % 10 == 1) {
			level.playSound(null, pos, GraveDiggers.DIG, SoundSource.HOSTILE, 0.8f, 0.9f + digger.getRandom().nextFloat() * 0.2f);
		}
		if (digTicks % 3 == 0) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
					4, 0.3, 0.3, 0.3, 0.05);
		}
		level.destroyBlockProgress(digger.getId(), pos, Math.min(9, digTicks * 10 / digNeeded));
		if (digTicks < digNeeded) {
			return Dig.DIGGING;
		}
		clearDig(level);
		// Dug out and thrown away: nothing drops, so he's no use as a quarry.
		level.destroyBlock(pos, false, digger);
		return Dig.DONE;
	}

	/** Soft ground goes quickly; stone takes him a few seconds a block. */
	private static int digTicks(ServerLevel level, BlockPos pos, BlockState state) {
		float hardness = Math.max(0.0f, state.getDestroySpeed(level, pos));
		return state.is(GraveDiggers.DIGGABLE) ? 10 + Mth.ceil(hardness * 12.0f) : 30 + Mth.ceil(hardness * 20.0f);
	}

	private void clearDig(ServerLevel level) {
		if (digPos != null) {
			level.destroyBlockProgress(digger.getId(), digPos, -1);
			digPos = null;
		}
	}

	private void glide(Vec3 to) {
		glideFrom = digger.position();
		glideTo = to;
		glideTick = 0;
		if (Math.abs(to.x - glideFrom.x) + Math.abs(to.z - glideFrom.z) > 0.5) {
			face(to.add(0.0, digger.getEyeHeight(), 0.0));
		}
	}

	private void glideStep() {
		Vec3 from = glideFrom;
		Vec3 to = glideTo;
		glideTick++;
		float t = Math.min(1.0f, glideTick / (float) GLIDE_TICKS);
		if (t >= 1.0f) {
			previous = digger.blockPosition();
			digger.setPos(to);
			glideTo = null;
			if (finishAfterGlide) {
				finish(true);
			}
		} else {
			digger.setPos(from.lerp(to, t));
		}
	}

	private void face(Vec3 point) {
		double dx = point.x - digger.getX();
		double dz = point.z - digger.getZ();
		if (dx * dx + dz * dz < 1.0e-4) {
			return;
		}
		float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
		digger.setYRot(yaw);
		digger.yBodyRot = yaw;
		digger.setYHeadRot(yaw);
	}

	// ------------------------------------------------------------------------------------------- the warning

	/** Under their floor: muffled digging above him, and closing in, cracks and dirt coming up at their feet. */
	private void warn(ServerLevel level, BlockPos feet, LivingEntity target) {
		BlockPos goal = target.blockPosition();
		if (phase == Phase.EMERGE || feet.getY() >= startY || feet.getY() >= goal.getY() - 1) {
			clearCrack(level);
			return;
		}
		BlockPos above = new BlockPos(feet.getX(), goal.getY() - 1, feet.getZ());
		if (ticks % MUFFLED_INTERVAL == 0) {
			level.playSound(null, above.getX() + 0.5, above.getY() + 1.0, above.getZ() + 0.5, GraveDiggers.DIG_MUFFLED, SoundSource.HOSTILE,
					1.0f, 0.85f + digger.getRandom().nextFloat() * 0.2f);
		}
		int distance = Math.abs(goal.getX() - feet.getX()) + Math.abs(goal.getZ() - feet.getZ());
		BlockState floor = level.getBlockState(above);
		if (distance > WARN_DISTANCE || floor.isAir()) {
			clearCrack(level);
			return;
		}
		if (!above.equals(crackPos)) {
			clearCrack(level);
			crackPos = above;
		}
		level.destroyBlockProgress(crackId(), above, Math.min(9, (WARN_DISTANCE - distance) * 2));
		if (ticks % 10 == 0) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, floor), above.getX() + 0.5, above.getY() + 1.02, above.getZ() + 0.5,
					2 + WARN_DISTANCE - distance, 0.25, 0.0, 0.25, 0.02);
		}
	}

	private void clearCrack(ServerLevel level) {
		if (crackPos != null) {
			level.destroyBlockProgress(crackId(), crackPos, -1);
			crackPos = null;
		}
	}

	/** A breaker id for the warning cracks that no entity has (they're negative), so they don't clash with his digging. */
	private int crackId() {
		return -1 - digger.getId();
	}

	// ------------------------------------------------------------------------------------------- helpers

	private void finish(boolean success) {
		finished = true;
		succeeded = success;
	}

	/** The four ways round, nearest the target first. */
	private static List<Direction> towards(BlockPos from, BlockPos target) {
		List<Direction> ways = new ArrayList<>(Direction.Plane.HORIZONTAL.stream().toList());
		ways.sort(Comparator.comparingDouble(way -> from.relative(way).distSqr(target)));
		return ways;
	}

	private static boolean griefing(ServerLevel level) {
		return level.getGameRules().get(GameRules.MOB_GRIEFING);
	}

	/** Open for him to stand in: nothing to bump into, and no water or lava. */
	static boolean passable(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
	}

	/**
	 * Whether he can dig this block: soft ground or natural stone (by the tags), never anything grave-digger-proof,
	 * unbreakable, holding a block entity, or with water or lava next to it.
	 */
	static boolean canDig(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || state.is(GraveDiggers.PROOF) || state.hasBlockEntity() || !state.getFluidState().isEmpty()
				|| !(state.is(GraveDiggers.DIGGABLE) || state.is(GraveDiggers.SOFT_STONE)) || state.getDestroySpeed(level, pos) < 0.0f
				|| !level.getWorldBorder().isWithinBounds(pos)) {
			return false;
		}
		for (Direction side : Direction.values()) {
			if (!level.getFluidState(pos.relative(side)).isEmpty()) {
				return false;
			}
		}
		return true;
	}
}
