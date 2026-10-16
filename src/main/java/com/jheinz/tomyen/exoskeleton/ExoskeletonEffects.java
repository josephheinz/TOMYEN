package com.jheinz.tomyen.exoskeleton;

import java.util.Set;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.ItemStack;

/**
 * The server side of a worn exoskeleton, twice a second for every player:
 * <ul>
 * <li>Each installed module's bonuses are applied as transient attribute modifiers, and taken off again once the
 * suit or the module is.
 * <li>Anything that got into a slot the suit covers some other way (creative inventory, commands) is moved back
 * into the inventory, or dropped if it's full. The equip paths themselves refuse it; see {@link ExoskeletonItem#refusal}.
 * </ul>
 */
public final class ExoskeletonEffects {
	private static final int INTERVAL = 10;

	private ExoskeletonEffects() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(ExoskeletonEffects::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % INTERVAL != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			applyModules(player);
			if (ExoskeletonItem.isWearing(player)) {
				clearCoveredSlots(player);
			}
		}
	}

	private static void applyModules(ServerPlayer player) {
		Set<ExoModule> active = ExoskeletonItem.activeModules(player);
		for (ExoModule module : ExoModule.values()) {
			for (ExoModule.Bonus bonus : module.bonuses()) {
				AttributeInstance attribute = player.getAttribute(bonus.attribute());
				if (attribute == null) {
					continue;
				}
				if (active.contains(module)) {
					// Only when missing: re-adding would mark the attribute dirty and resend it every time.
					if (!attribute.hasModifier(module.modifierId())) {
						attribute.addTransientModifier(module.modifier(bonus));
					}
				} else {
					attribute.removeModifier(module.modifierId());
				}
			}
		}
	}

	private static void clearCoveredSlots(ServerPlayer player) {
		for (EquipmentSlot slot : ExoskeletonItem.coveredSlots()) {
			ItemStack worn = player.getItemBySlot(slot);
			if (!ExoskeletonItem.mayStayWithSuit(worn, slot)) {
				player.setItemSlot(slot, ItemStack.EMPTY);
				if (!player.getInventory().add(worn)) {
					player.drop(worn, false, Prediction.SERVER_ONLY);
				}
			}
		}
	}
}
