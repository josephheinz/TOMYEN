package com.jheinz.mymod;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Guns are two-handed, so they never go in the off hand. The swap-hands key, the off-hand slot and the
 * inventory's swap-with-off-hand key all refuse them (see the mixins); anything that slips past those
 * (commands, other mods) is moved back into the inventory here.
 */
public final class GunOffhand {
	private GunOffhand() {
	}

	public static boolean isGun(ItemStack stack) {
		return stack.getItem() instanceof GunItem;
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(GunOffhand::tick);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ItemStack offhand = player.getItemInHand(InteractionHand.OFF_HAND);
			if (isGun(offhand)) {
				player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
				if (!player.getInventory().add(offhand)) {
					player.drop(offhand, false, Prediction.SERVER_ONLY);
				}
			}
		}
	}
}
