package com.jheinz.tomyen.fae;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * Standing in a Fae Ring. A ring is any circle of mushrooms (red, brown or Fae) round the player's feet: at least
 * {@value #MIN_MUSHROOMS} of them {@value #INNER}-{@value #OUTER} blocks out, within a block up or down, spread over at
 * least {@value #MIN_SECTORS} of the eight directions; so worldgen rings, the Feywild's arrival ring and rings players
 * plant themselves all work.
 *
 * <p>In the Overworld at night, or in the Feywild at any time, a player standing in one charges it: sparkles gather
 * and chimes rise for {@value #CHARGE_TICKS} ticks, then it carries them across (see {@link Feywild}). Stepping out
 * resets the charge, and someone who's just arrived in a ring has to step out of it before it will take them back.
 */
public final class FaeRings {
	private static final int CHECK_INTERVAL = 5;
	public static final int CHARGE_TICKS = 100;
	private static final double INNER = 2.0;
	private static final double OUTER = 5.5;
	private static final int MIN_MUSHROOMS = 6;
	private static final int MIN_SECTORS = 6;

	private static final Map<UUID, Integer> CHARGE = new HashMap<>();
	/** Players who arrived in a ring and haven't stepped out of it yet. */
	private static final Set<UUID> ARRIVED = new HashSet<>();

	private FaeRings() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(FaeRings::tick);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if ((server.getTickCount() + player.getId()) % CHECK_INTERVAL == 0) {
				check(player);
			}
		}
	}

	private static void check(ServerPlayer player) {
		ServerLevel level = player.level();
		UUID id = player.getUUID();
		boolean open = Feywild.is(level) || level.dimension() == Level.OVERWORLD && level.isDarkOutside();
		if (player.isSpectator() || !open || !isInRing(level, player.blockPosition())) {
			CHARGE.remove(id);
			ARRIVED.remove(id);
			return;
		}
		if (ARRIVED.contains(id)) {
			return;
		}
		int charge = CHARGE.merge(id, CHECK_INTERVAL, Integer::sum);
		float progress = Math.min(1.0f, (float) charge / CHARGE_TICKS);
		// Sparkles rise from the ring, thicker as it charges, and draw in round the player.
		double spread = 3.5 * (1.0 - 0.6 * progress);
		for (int i = 0; i < 4 + (int) (progress * 20); i++) {
			float angle = level.getRandom().nextFloat() * Mth.TWO_PI;
			level.sendParticles(Fae.SPARKLE, player.getX() + Mth.cos(angle) * spread, player.getY() + level.getRandom().nextDouble() * 2.0 * progress,
					player.getZ() + Mth.sin(angle) * spread, 1, 0.0, 0.05, 0.0, 0.01);
		}
		if (charge % 20 == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), Fae.RING_CHIME, SoundSource.AMBIENT, 0.4f + 0.6f * progress,
					0.7f + progress);
		}
		if (charge >= CHARGE_TICKS) {
			CHARGE.remove(id);
			travel(player);
		}
	}

	/** Carries the player to the Feywild, or out of it, and marks them as arrived. */
	public static void travel(ServerPlayer player) {
		if (Feywild.is(player.level())) {
			Feywild.sendHome(player);
		} else {
			Feywild.sendIn(player);
		}
		ARRIVED.add(player.getUUID());
	}

	/** Whether there's a ring of mushrooms round these feet. */
	public static boolean isInRing(Level level, BlockPos feet) {
		int count = 0;
		int sectors = 0;
		int reach = Mth.ceil(OUTER);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				int d2 = dx * dx + dz * dz;
				if (d2 < INNER * INNER || d2 > OUTER * OUTER) {
					continue;
				}
				for (int dy = -1; dy <= 1; dy++) {
					if (level.getBlockState(pos.setWithOffset(feet, dx, dy, dz)).is(Fae.RING_MUSHROOMS)) {
						count++;
						sectors |= 1 << (int) ((Mth.atan2(dz, dx) / Mth.TWO_PI + 1.0) * 8.0) % 8;
						break;
					}
				}
			}
		}
		return count >= MIN_MUSHROOMS && Integer.bitCount(sectors) >= MIN_SECTORS;
	}
}
