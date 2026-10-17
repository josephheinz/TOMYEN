package com.jheinz.tomyen.crow;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.scarecrow.CropGuard;

/**
 * What a murder is doing, decided by its leader (every crow has one of these; only the leader's is used) about once a
 * second and read by every bird in it each tick. The modes:
 * <ul>
 * <li>{@link Mode#ROAM}: a loose flock wheeling about the sky, drifting toward one point and then another near home;
 * <li>{@link Mode#PERCH}: down on the fences, roofs and treetops round a spot, preening and cawing;
 * <li>{@link Mode#FORAGE}: down in a field, hopping about and pecking for seed;
 * <li>{@link Mode#RAID}: down on a farm, each bird taking a ripe crop of its own;
 * <li>{@link Mode#ROOST}: the night, asleep in the treetops (by the nest if there is one);
 * <li>{@link Mode#FLEE}: scattered, by a hit or a scarecrow, every bird up at once cawing;
 * <li>{@link Mode#MOB}: someone's at the nest: the murder wheels low over it, raucous.
 * </ul>
 * A scarecrow put up where they're feeding sends them off (and they stay away from that farm for a good while), and
 * the leader also watches for someone standing about with something shiny in hand, to send a bold bird to snatch it.
 */
public final class CrowFlock {
	public enum Mode {
		ROAM, PERCH, FORAGE, RAID, ROOST, FLEE, MOB;

		/** Whether the birds are meant to be down on something. */
		public boolean landed() {
			return this == PERCH || this == FORAGE || this == RAID || this == ROOST;
		}
	}

	/** A murder stays clear of a farm whose scarecrow scared it for this long (five minutes). */
	private static final int AVOID_TICKS = 6000;
	private static final int AVOID_RADIUS = 24;
	/** After a snatch, none for this long (two minutes). */
	private static final int SNATCH_COOLDOWN = 2400;
	/** A player has to stand this still (blocks moved between checks two seconds apart). */
	private static final double STILL = 0.25;
	/** Merging murders stop growing at this size. */
	static final int MAX_SIZE = 16;

	private final Crow leader;
	private Mode mode = Mode.ROAM;
	private @Nullable Vec3 anchor;
	private int modeTicks;
	/** Bumped every time the murder scatters, so each bird caws once for it. */
	private int scatterStamp;
	int size = 1;
	private int checkTimer;
	private long farmSearchAt;
	private final Deque<BlockPos> crops = new ArrayDeque<>();
	private final Map<Integer, Long> claims = new HashMap<>();
	private final List<Avoid> avoid = new ArrayList<>();
	private long snatchAt;
	private @Nullable UUID watched;
	private @Nullable Vec3 watchedPos;
	private int stillChecks;
	private boolean evenCheck;

	private record Avoid(BlockPos pos, long until) {
	}

	CrowFlock(Crow leader) {
		this.leader = leader;
	}

	public Mode mode() {
		return mode;
	}

	public Vec3 anchor() {
		return anchor != null ? anchor : leader.position();
	}

	public int scatterStamp() {
		return scatterStamp;
	}

	/** Every second or so, on the leader: keeps the plan up to date. */
	void tick(ServerLevel level) {
		RandomSource random = leader.getRandom();
		if (anchor == null) {
			startRoam(level, random);
		}
		if (modeTicks > 0) {
			modeTicks--;
		}
		if (--checkTimer > 0) {
			return;
		}
		checkTimer = 20;
		evenCheck = !evenCheck;
		long time = level.getGameTime();
		avoid.removeIf(a -> a.until < time);
		claims.values().removeIf(until -> until < time);
		boolean night = !level.isBrightOutside();
		switch (mode) {
			case FLEE, MOB -> {
				if (modeTicks <= 0) {
					if (night) {
						startRoost(level, random);
					} else {
						startRoam(level, random);
					}
				}
				return;
			}
			case ROOST -> {
				if (!night) {
					startRoam(level, random);
				}
				return;
			}
			default -> {
			}
		}
		if (night) {
			startRoost(level, random);
			return;
		}
		if (mode.landed() && CropGuard.scaresCrows(level, BlockPos.containing(anchor()))) {
			// A scarecrow's gone up (or they've wandered too near one): off they go, and they won't be back for a while.
			avoid.add(new Avoid(BlockPos.containing(anchor()), time + AVOID_TICKS));
			scatter(level, anchor(), 1.0);
			return;
		}
		if (mode == Mode.RAID && crops.isEmpty() && modeTicks > 100) {
			refillCrops(level);
			if (crops.isEmpty()) {
				modeTicks = 100;
			}
		}
		if (evenCheck) {
			watchPlayers(level, time, random);
			watchNest(level);
		}
		if (modeTicks <= 0) {
			next(level, random, time);
		}
	}

	private void next(ServerLevel level, RandomSource random, long time) {
		if (mode != Mode.ROAM) {
			startRoam(level, random);
			return;
		}
		if (CrowRaids.mayRaid(level) && time >= farmSearchAt) {
			farmSearchAt = time + 400;
			BlockPos farm = CrowRaids.findFarm(level, leader.blockPosition(), this::avoids);
			if (farm != null) {
				startRaid(level, farm);
				return;
			}
		}
		float roll = random.nextFloat();
		if (roll < 0.45f) {
			startLanded(level, random, Mode.PERCH, CrowRaids.Want.PERCH);
		} else if (roll < 0.85f) {
			startLanded(level, random, Mode.FORAGE, CrowRaids.Want.GROUND);
		} else {
			startRoam(level, random);
		}
	}

	private boolean avoids(BlockPos pos) {
		for (Avoid a : avoid) {
			if (a.pos.distSqr(pos) < AVOID_RADIUS * AVOID_RADIUS) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------------------------------------ modes

	/** Off across the sky toward somewhere near home (the nest, or where they were first seen), well up. */
	private void startRoam(ServerLevel level, RandomSource random) {
		BlockPos home = leader.nest() != null ? leader.nest() : leader.home();
		Vec3 target = null;
		for (int i = 0; i < 4 && target == null; i++) {
			float angle = random.nextFloat() * Mth.TWO_PI;
			double r = 8.0 + random.nextDouble() * 22.0;
			int x = Mth.floor(home.getX() + Mth.cos(angle) * r);
			int z = Mth.floor(home.getZ() + Mth.sin(angle) * r);
			if (level.hasChunkAt(x, z)) {
				target = new Vec3(x + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 7.0 + random.nextDouble() * 6.0, z + 0.5);
			}
		}
		set(Mode.ROAM, target != null ? target : leader.position().add(0.0, 6.0, 0.0), 200 + random.nextInt(240));
	}

	private void startLanded(ServerLevel level, RandomSource random, Mode landed, CrowRaids.Want want) {
		BlockPos spot = CrowRaids.findArea(level, leader.blockPosition(), 20, want, random, 12);
		if (spot == null || avoids(spot) || CropGuard.scaresCrows(level, spot)) {
			startRoam(level, random);
			return;
		}
		set(landed, CrowRaids.standPoint(level, spot.above()), 400 + random.nextInt(400));
	}

	private void startRaid(ServerLevel level, BlockPos farm) {
		set(Mode.RAID, Vec3.atBottomCenterOf(farm), 1200);
		refillCrops(level);
		leader.cawSoon(1, false);
	}

	private void refillCrops(ServerLevel level) {
		crops.clear();
		for (BlockPos crop : CrowRaids.ripeAround(level, BlockPos.containing(anchor()), 7)) {
			crops.add(crop);
		}
	}

	/** To the treetops for the night: round the nest if there's one, else any good crown nearby. */
	private void startRoost(ServerLevel level, RandomSource random) {
		BlockPos nest = leader.nest();
		Vec3 at;
		if (CrowNests.isNest(level, nest)) {
			at = Vec3.atBottomCenterOf(nest);
		} else {
			BlockPos tree = CrowRaids.findArea(level, leader.blockPosition(), 24, CrowRaids.Want.TREETOP, random, 16);
			at = tree != null ? CrowRaids.standPoint(level, tree.above()) : leader.position();
		}
		set(Mode.ROOST, at, 400);
	}

	/** Every bird up and away from the danger, cawing: strength 1 is a scarecrow, more for a hit. */
	public void scatter(ServerLevel level, Vec3 from, double strength) {
		Vec3 away = leader.position().subtract(from).multiply(1.0, 0.0, 1.0);
		if (away.lengthSqr() < 1.0E-4) {
			float angle = leader.getRandom().nextFloat() * Mth.TWO_PI;
			away = new Vec3(Mth.cos(angle), 0.0, Mth.sin(angle));
		}
		Vec3 to = leader.position().add(away.normalize().scale(20.0 + 6.0 * strength));
		int x = Mth.floor(to.x);
		int z = Mth.floor(to.z);
		double ground = level.hasChunkAt(x, z) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) : leader.getY();
		set(Mode.FLEE, new Vec3(to.x, Math.max(ground, leader.getY()) + 9.0, to.z), (int) (120 + 40 * strength));
		crops.clear();
		scatterStamp++;
	}

	/** Someone's at the nest: the whole murder wheels low over it, cawing. */
	public void mob(ServerLevel level, BlockPos nest) {
		set(Mode.MOB, Vec3.atBottomCenterOf(nest).add(0.0, 5.0, 0.0), 240);
		scatterStamp++;
	}

	private void set(Mode mode, Vec3 anchor, int ticks) {
		this.mode = mode;
		this.anchor = anchor;
		this.modeTicks = ticks;
		if (mode != Mode.RAID) {
			crops.clear();
		}
	}

	// ------------------------------------------------------------------------------------------------ sharing out

	/** A ripe crop no other bird has taken yet, nearest this one; or null. */
	public @Nullable BlockPos claimCrop(Crow crow) {
		BlockPos best = null;
		for (BlockPos crop : crops) {
			if (best == null || crop.distSqr(crow.blockPosition()) < best.distSqr(crow.blockPosition())) {
				best = crop;
			}
		}
		if (best != null) {
			crops.remove(best);
		}
		return best;
	}

	public boolean hasCrops() {
		return !crops.isEmpty();
	}

	public boolean isClaimed(ItemEntity item) {
		return claims.containsKey(item.getId());
	}

	public void claim(ItemEntity item, long until) {
		claims.put(item.getId(), until);
	}

	// ------------------------------------------------------------------------------------------------ watching

	/**
	 * Anyone standing still near the murder with something shiny in their hand: after two checks still (four seconds
	 * or so) there's a one-in-eight chance each check that the bird nearest them goes for it.
	 */
	private void watchPlayers(ServerLevel level, long time, RandomSource random) {
		if (time < snatchAt || mode == Mode.FLEE || mode == Mode.MOB || mode == Mode.ROOST || !CrowRaids.mayRaid(level)) {
			return;
		}
		Player player = level.getNearestPlayer(leader.getX(), leader.getY(), leader.getZ(), 18.0,
				entity -> EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(entity) && entity instanceof Player p
						&& p.getMainHandItem().is(Crows.SHINY));
		if (player == null) {
			watched = null;
			return;
		}
		if (!player.getUUID().equals(watched) || watchedPos == null) {
			watched = player.getUUID();
			watchedPos = player.position();
			stillChecks = 0;
			return;
		}
		stillChecks = player.position().distanceToSqr(watchedPos) < STILL * STILL ? stillChecks + 1 : 0;
		watchedPos = player.position();
		if (stillChecks < 2 || random.nextInt(8) != 0 || CropGuard.scaresCrows(level, player.blockPosition())) {
			return;
		}
		Crow thief = null;
		for (Crow crow : level.getEntitiesOfClass(Crow.class, player.getBoundingBox().inflate(14.0),
				crow -> crow.leader() == leader && crow.isFree())) {
			if (thief == null || crow.distanceToSqr(player) < thief.distanceToSqr(player)) {
				thief = crow;
			}
		}
		if (thief != null && thief.hasLineOfSight(player)) {
			thief.snatchFrom(player);
			snatchAt = time + SNATCH_COOLDOWN;
			stillChecks = 0;
		}
	}

	/** Someone climbing up to the nest gets a raucous warning: the murder wheels over it (only robbing it brings them down). */
	private void watchNest(ServerLevel level) {
		BlockPos nest = leader.nest();
		if (mode == Mode.MOB || nest == null || leader.distanceToSqr(Vec3.atCenterOf(nest)) > 48.0 * 48.0 || !CrowNests.isNest(level, nest)) {
			return;
		}
		Player near = level.getNearestPlayer(nest.getX() + 0.5, nest.getY() + 0.5, nest.getZ() + 0.5, 4.0,
				EntitySelector.NO_CREATIVE_OR_SPECTATOR::test);
		if (near != null) {
			mob(level, nest);
		}
	}
}
