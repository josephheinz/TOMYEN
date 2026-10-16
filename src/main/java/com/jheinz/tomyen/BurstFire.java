package com.jheinz.tomyen;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Fires the rest of a burst, one round per tick, after the first round goes out on the click. */
public final class BurstFire {
	private record Pending(InteractionHand hand, int remaining, long nextGameTime) {
	}

	private static final Map<UUID, Pending> PENDING = new HashMap<>();

	private BurstFire() {
	}

	public static void schedule(Player player, InteractionHand hand, int shots) {
		PENDING.put(player.getUUID(), new Pending(hand, shots, player.level().getGameTime() + 1));
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(BurstFire::tick);
	}

	private static void tick(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Pending> entry = it.next();
			Pending pending = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) {
				it.remove();
				continue;
			}
			ServerLevel level = (ServerLevel) player.level();
			if (level.getGameTime() < pending.nextGameTime()) {
				continue;
			}
			// Switching items or running dry ends the burst early.
			ItemStack held = player.getItemInHand(pending.hand());
			boolean fired = held.getItem() instanceof GunItem gun && gun.fireRound(level, player, held, pending.hand());
			if (!fired || pending.remaining() <= 1) {
				it.remove();
			} else {
				entry.setValue(new Pending(pending.hand(), pending.remaining() - 1, level.getGameTime() + 1));
			}
		}
	}
}
