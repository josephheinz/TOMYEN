package com.jheinz.mymod.launcher;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Locking a homing rocket onto a target, decided on the server. While a player aims down the launcher's sight
 * (sneaking with it shouldered) with the guidance module fitted and a homing rocket seated, the living thing
 * nearest their crosshair (within {@link #ACQUIRE_DEGREES}, {@link #RANGE} blocks and in plain sight) is tracked;
 * after {@link #LOCK_TICKS} on it, it's locked. Drifting off for more than {@link #GRACE_TICKS} starts over (the
 * lock holds within the wider {@link #HOLD_DEGREES}, so a small wobble doesn't lose it). The player is sent the
 * state every tick for the HUD. Firing takes the lock, checked once more.
 */
public final class LockOn {
	public static final int LOCK_TICKS = 60;
	public static final double RANGE = 120.0;
	private static final int GRACE_TICKS = 5;
	private static final double ACQUIRE_DEGREES = 2.5;
	private static final double HOLD_DEGREES = 4.0;

	private record Lock(int target, long start, long lastSeen, boolean locked) {
	}

	private static final Map<UUID, Lock> LOCKS = new HashMap<>();

	private LockOn() {
	}

	/** Whether this player is set up to lock on right now. */
	private static boolean aiming(ServerPlayer player) {
		ItemStack held = player.getMainHandItem();
		return player.isShiftKeyDown() && held.getItem() instanceof RocketLauncherItem
				&& RocketLauncherItem.shouldered(held, player.level()) && RocketLauncherItem.hasGuidance(held)
				&& RocketLauncherItem.rocketToFire(held, player) == Launchers.PG7_HOMING_ROCKET;
	}

	static void tick(ServerPlayer player) {
		Lock lock = LOCKS.get(player.getUUID());
		if (!aiming(player)) {
			if (lock != null) {
				drop(player);
			}
			return;
		}
		long now = player.level().getGameTime();
		LivingEntity seen = sight(player, lock);
		if (seen != null && lock != null && seen.getId() == lock.target()) {
			lock = new Lock(lock.target(), lock.start(), now, lock.locked() || now - lock.start() >= LOCK_TICKS);
		} else if (lock == null || now - lock.lastSeen() > GRACE_TICKS) {
			lock = seen == null ? null : new Lock(seen.getId(), now, now, false);
		}
		if (lock == null) {
			if (LOCKS.containsKey(player.getUUID())) {
				drop(player);
			}
			return;
		}
		LOCKS.put(player.getUUID(), lock);
		ServerPlayNetworking.send(player,
				new Launchers.LockState(lock.target(), (int) Math.min(LOCK_TICKS, now - lock.start()), lock.locked()));
	}

	/** The living thing closest to the crosshair that can be locked, preferring the one already being tracked. */
	private static LivingEntity sight(ServerPlayer player, Lock lock) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0f);
		AABB along = new AABB(eye, eye.add(look.scale(RANGE))).inflate(RANGE * Math.tan(Math.toRadians(HOLD_DEGREES)) + 1.0);
		LivingEntity best = null;
		double bestAngle = Double.MAX_VALUE;
		for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, along,
				entity -> entity != player && entity.isAlive() && !entity.isSpectator() && entity.isPickable())) {
			Vec3 to = entity.getBoundingBox().getCenter().subtract(eye);
			double distance = to.length();
			if (distance > RANGE || distance < 1.0) {
				continue;
			}
			// Off the crosshair by this much, allowing for how wide the target looks.
			double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, look.dot(to) / distance))))
					- Math.toDegrees(Math.atan(entity.getBbWidth() / 2.0 / distance));
			double limit = lock != null && entity.getId() == lock.target() ? HOLD_DEGREES : ACQUIRE_DEGREES;
			if (angle <= limit && angle < bestAngle && visible(player, entity)) {
				best = entity;
				bestAngle = angle;
			}
		}
		return best;
	}

	private static boolean visible(Player player, Entity entity) {
		// Its eyes or its middle in plain view.
		return player.hasLineOfSight(entity) || player.level().clip(new ClipContext(player.getEyePosition(),
				entity.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
				.getType() == HitResult.Type.MISS;
	}

	/** Takes the player's lock for a shot: the target if it's locked and still alive, in range and in sight. */
	static Entity take(Player player) {
		Lock lock = LOCKS.get(player.getUUID());
		if (lock == null || !(player instanceof ServerPlayer serverPlayer)) {
			return null;
		}
		drop(serverPlayer);
		Entity target = player.level().getEntity(lock.target());
		if (!lock.locked() || target == null || !target.isAlive() || target.distanceTo(player) > RANGE || !visible(player, target)) {
			return null;
		}
		return target;
	}

	private static void drop(ServerPlayer player) {
		LOCKS.remove(player.getUUID());
		ServerPlayNetworking.send(player, Launchers.LockState.NONE);
	}

	static void forget(ServerPlayer player) {
		LOCKS.remove(player.getUUID());
	}
}
