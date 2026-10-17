package com.jheinz.tomyen.crow;

import java.util.Comparator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Crow nests: where a murder builds one (the very top of a tree's crown, in place of a leaf, so a bird can drop
 * straight in), and what the murder does when someone goes for it.
 */
public final class CrowNests {
	/** How far from itself a thief looks for a tree to nest in. */
	private static final int SITE_RANGE = 20;
	/** Crows guarding a nest come from this far. */
	private static final double GUARD_RANGE = 32.0;
	/** At most this many dive at a nest robber; the rest wheel overhead cawing. */
	private static final int DEFENDERS = 3;

	private CrowNests() {
	}

	public static boolean isNest(Level level, @Nullable BlockPos pos) {
		return pos != null && level.isLoaded(pos) && level.getBlockState(pos).is(Crows.CROW_NEST);
	}

	/** A treetop near origin where a nest could go, away from any other nest; or null. */
	public static @Nullable BlockPos findSite(ServerLevel level, BlockPos origin, RandomSource random) {
		for (int i = 0; i < 4; i++) {
			BlockPos top = CrowRaids.findArea(level, origin, SITE_RANGE, CrowRaids.Want.TREETOP, random, 6);
			if (top != null && canBuildAt(level, top) && !nestNear(level, top, 6)) {
				return top;
			}
		}
		return null;
	}

	/** A leaf at the top of a crown, open to the sky above. */
	public static boolean canBuildAt(Level level, BlockPos pos) {
		if (!level.isLoaded(pos) || !level.getBlockState(pos).is(BlockTags.LEAVES) || !level.getBlockState(pos.above()).isAir()) {
			return false;
		}
		var below = level.getBlockState(pos.below());
		return below.is(BlockTags.LEAVES) || below.is(BlockTags.LOGS);
	}

	/** Whether there's a nest within radius (true, to be safe, if any of that ground isn't loaded). */
	private static boolean nestNear(Level level, BlockPos pos, int radius) {
		if (!level.hasChunksAt(pos.getX() - radius, pos.getZ() - radius, pos.getX() + radius, pos.getZ() + radius)) {
			return true;
		}
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-radius, -3, -radius), pos.offset(radius, 3, radius))) {
			if (level.getBlockState(p).is(Crows.CROW_NEST)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Someone's at the nest: opening it (or breaking it, which is worse). Every crow it belongs to caws, the murder
	 * wheels over the nest, and up to {@value #DEFENDERS} of the boldest dive at the robber for a while.
	 */
	public static void alarm(ServerLevel level, BlockPos nest, Player robber, boolean breaking) {
		if (robber.isCreative() && !breaking) {
			return;
		}
		List<Crow> owners = level.getEntitiesOfClass(Crow.class, new AABB(nest).inflate(GUARD_RANGE),
				crow -> crow.isAlive() && nest.equals(crow.nest()));
		if (owners.isEmpty()) {
			return;
		}
		owners.getFirst().leader().flock.mob(level, nest);
		owners.sort(Comparator.comparingDouble(crow -> crow.distanceToSqr(robber)));
		int defenders = 0;
		for (Crow crow : owners) {
			if (defenders < DEFENDERS && crow.canDefend() && !robber.isCreative()) {
				crow.defend(robber);
				defenders++;
			}
			crow.cawSoon(level.getRandom().nextInt(15) + 1, true);
		}
		if (defenders > 0 && robber instanceof ServerPlayer player) {
			player.sendOverlayMessage(Component.translatable("entity.tomyen.crow.defend"));
		}
	}
}
