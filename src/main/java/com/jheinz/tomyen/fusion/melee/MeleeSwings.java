package com.jheinz.tomyen.fusion.melee;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Knowing when a melee swing was a charged one. A swing reaches the server as a punch, hit or miss
 * (ServerPunchSwingMixin); but a hit arrives first as an attack, and the attack spends the charge, so the charge of a
 * hit is noted as it starts (PlayerAttackChargeMixin).
 */
public final class MeleeSwings {
	/** How full the attack meter must be for a swing to count as charged. */
	public static final float CHARGED = 0.9f;
	private static final Map<UUID, Long> CHARGED_HITS = new HashMap<>();

	private MeleeSwings() {
	}

	/** A weapon that does something on every charged swing, hit or miss. */
	public interface SwingAbility {
		void swung(ServerLevel level, ServerPlayer player, ItemStack stack);
	}

	/** From Player.attack's head, before the charge is spent. */
	public static void attacking(Player player) {
		if (player instanceof ServerPlayer && player.getAttackStrengthScale(0.5f) >= CHARGED) {
			CHARGED_HITS.put(player.getUUID(), player.level().getGameTime());
		}
	}

	/** Whether the player's latest attack (this tick or the last) was charged. */
	public static boolean chargedHit(Player player) {
		Long at = CHARGED_HITS.get(player.getUUID());
		return at != null && player.level().getGameTime() - at <= 1;
	}

	/** From the punch packet: a charged swing (a miss with the meter full, or a hit noted as charged). */
	public static void punched(ServerPlayer player) {
		ItemStack stack = player.getMainHandItem();
		if (!(stack.getItem() instanceof SwingAbility ability) || player.isSpectator()) {
			return;
		}
		if (player.getAttackStrengthScale(0.5f) >= CHARGED || chargedHit(player)) {
			ability.swung(player.level(), player, stack);
		}
	}

	static void clear() {
		CHARGED_HITS.clear();
	}

	/** The "Fused" tag and grey lines saying what a fusion weapon does. */
	static void tooltip(Consumer<Component> tooltip, String... keys) {
		tooltip.accept(Component.translatable("item.tomyen.fused").withStyle(ChatFormatting.LIGHT_PURPLE));
		for (String key : keys) {
			tooltip.accept(Component.translatable(key).withStyle(ChatFormatting.GRAY));
		}
	}
}
