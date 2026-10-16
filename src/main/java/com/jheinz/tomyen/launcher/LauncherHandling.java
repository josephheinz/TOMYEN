package com.jheinz.tomyen.launcher;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Runs each player's held launcher from the server tick: a launcher that has just come into the hand (switched
 * to, moved into the selected slot, or held on joining) starts being hoisted; one that leaves the hand mid-hoist
 * or mid-reload has that cancelled, so a rocket is never used up by an unfinished reload; and a reload that has
 * run its time seats the rocket.
 */
final class LauncherHandling {
	private record Held(ItemStack stack, int slot) {
	}

	private static final Map<UUID, Held> HELD = new HashMap<>();

	private LauncherHandling() {
	}

	static void tick(ServerPlayer player) {
		ItemStack stack = player.getMainHandItem();
		int slot = player.getInventory().getSelectedSlot();
		Held last = HELD.get(player.getUUID());
		boolean same = last != null && last.stack() == stack && last.slot() == slot;
		if (last != null && !same && last.stack().getItem() instanceof RocketLauncherItem) {
			last.stack().remove(Launchers.LAUNCHER_ACTION);
		}
		if (!(stack.getItem() instanceof RocketLauncherItem launcher)) {
			HELD.remove(player.getUUID());
			return;
		}
		HELD.put(player.getUUID(), new Held(stack, slot));

		long now = player.level().getGameTime();
		LauncherAction action = stack.get(Launchers.LAUNCHER_ACTION);
		if (!same || action == null) {
			launcher.startEquip(player, stack, now);
		} else if (action.kind() == LauncherAction.Kind.RELOAD && now >= action.end()) {
			launcher.completeReload(player, stack, action, now);
		}
	}

	static void forget(ServerPlayer player) {
		HELD.remove(player.getUUID());
	}
}
