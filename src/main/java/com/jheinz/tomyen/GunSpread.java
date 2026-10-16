package com.jheinz.tomyen;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;

/**
 * Shot spread: each gun's resting inaccuracy plus bloom that builds with rapid fire and recovers between
 * shots. Sneaking tightens it, a scope removes the resting spread, and shooting mid-air loosens it.
 *
 * <p>The server decides where bullets go; the client runs the same model for its own shots so the
 * crosshair shows the spread.
 */
public final class GunSpread {
	/** Bloom falls to about a third this many ticks after the last shot. */
	private static final float RECOVERY_TICKS = 6.0f;
	private static final float MAX_BLOOM = 7.0f;
	private static final float SNEAK_FACTOR = 0.6f;
	private static final float AIRBORNE_PENALTY = 2.0f;

	private record Bloom(float amount, float time) {
	}

	private static final Map<UUID, Bloom> SERVER = new HashMap<>();
	private static Bloom client;

	private GunSpread() {
	}

	private static float bloomAt(Bloom bloom, float now) {
		return bloom == null ? 0.0f : bloom.amount() * (float) Math.exp(-(now - bloom.time()) / RECOVERY_TICKS);
	}

	private static Bloom afterShot(Bloom bloom, GunStats stats, float now) {
		return new Bloom(Math.min(MAX_BLOOM, bloomAt(bloom, now) + stats.bloomPerShot()), now);
	}

	/** Cone radius in degrees for a shot with the given bloom. */
	private static float spread(GunStats stats, Player player, float bloom) {
		boolean sneaking = player.isShiftKeyDown();
		float total = (stats.scoped() && sneaking ? 0.0f : stats.spreadDegrees()) + bloom;
		if (sneaking) {
			total *= SNEAK_FACTOR;
		}
		if (!player.onGround()) {
			total += AIRBORNE_PENALTY;
		}
		return total;
	}

	/** Spread for a shot the server is firing now; also adds that shot's bloom. */
	public static float serverShot(ServerPlayer player, GunStats stats) {
		float now = player.level().getGameTime();
		Bloom bloom = SERVER.get(player.getUUID());
		float spread = spread(stats, player, bloomAt(bloom, now));
		SERVER.put(player.getUUID(), afterShot(bloom, stats, now));
		return spread;
	}

	/** Records one of the local player's own shots, for the crosshair. */
	public static void clientShot(GunStats stats) {
		client = afterShot(client, stats, clientTime());
	}

	/** The local player's current spread, for the crosshair. */
	public static float clientSpread(GunStats stats, Player player) {
		return spread(stats, player, bloomAt(client, clientTime()));
	}

	private static float clientTime() {
		return Util.getMillis() / 50.0f;
	}
}
