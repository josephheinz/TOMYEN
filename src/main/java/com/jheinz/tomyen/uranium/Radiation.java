package com.jheinz.tomyen.uranium;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.jheinz.tomyen.ModEffects;
import com.jheinz.tomyen.herbalism.HerbalRemedies;
import com.jheinz.tomyen.powerarmor.PowerArmor;
import com.jheinz.tomyen.powerarmor.PowerArmorSuit;

/**
 * Radiation Sickness, and how players catch it. The effect's remaining time is the dose: every second the radioactive
 * things in a player's inventory add to it, and digging uranium ore adds a lump. Away from them it wears off at the
 * usual second a second, so a little raw uranium never builds up, while a pocketful does.
 * <ul>
 * <li>Each raw uranium (an ore block or raw block counts as one, or nine) adds {@link #RAW_DOSE} ticks a second;
 * each loose enriched uranium {@link #ENRICHED_DOSE}. Depleted uranium and fuel rods (clad, so shielded) are safe,
 * and so is anything inside a bundle or shulker box.
 * <li>Digging uranium ore adds {@link #MINING_DOSE}.
 * <li>The dose is capped at {@link #MAX_DOSE}. It sets the level: I under {@link #LEVEL_II}, II (weakness) from
 * there, III (weakness and nausea, and half a heart every four seconds) from {@link #LEVEL_III}, IV (weakness II,
 * nausea, half a heart every two seconds) from {@link #LEVEL_IV}. The level falls again as the dose wears off.
 * </ul>
 * A full Hazmat Suit or a complete suit of power armor stops any new dose (the old one still has to wear off), and
 * players in creative or spectator mode take none. Anti-Radiation Tea's Radioprotection halves new doses.
 */
public final class Radiation {
	public static final int RAW_DOSE = 10;
	public static final int ENRICHED_DOSE = 40;
	public static final int MINING_DOSE = 600;
	public static final int MAX_DOSE = 15 * 60 * 20;
	public static final int LEVEL_II = 2 * 60 * 20;
	public static final int LEVEL_III = 5 * 60 * 20;
	public static final int LEVEL_IV = 10 * 60 * 20;
	/** Ticks between doses; symptoms are refreshed this often too. */
	private static final int INTERVAL = 20;
	private static final int SYMPTOM_TICKS = 100;

	private Radiation() {
	}

	static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(Radiation::tick);
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (state.is(Uranium.URANIUM_ORES) && player instanceof ServerPlayer serverPlayer) {
				irradiate(serverPlayer, MINING_DOSE);
			}
		});
	}

	/** The level (amplifier) a dose of this many ticks makes. */
	public static int level(int dose) {
		return dose >= LEVEL_IV ? 3 : dose >= LEVEL_III ? 2 : dose >= LEVEL_II ? 1 : 0;
	}

	/** Whether the player is shut off from radiation: by game mode, a full Hazmat Suit or complete power armor. */
	public static boolean isShielded(Player player) {
		if (player.isCreative() || player.isSpectator() || Uranium.wearsHazmatSuit(player)) {
			return true;
		}
		PowerArmorSuit suit = PowerArmor.worn(player);
		return suit != null && suit.complete();
	}

	/** Ticks of dose a second from what the player carries. */
	public static int carriedDose(Inventory inventory) {
		int dose = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(Uranium.RAW_URANIUM) || stack.is(Uranium.URANIUM_ORE.asItem()) || stack.is(Uranium.DEEPSLATE_URANIUM_ORE.asItem())) {
				dose += RAW_DOSE * stack.getCount();
			} else if (stack.is(Uranium.RAW_URANIUM_BLOCK.asItem())) {
				dose += RAW_DOSE * 9 * stack.getCount();
			} else if (stack.is(Uranium.ENRICHED_URANIUM)) {
				dose += ENRICHED_DOSE * stack.getCount();
			}
		}
		return dose;
	}

	/** Adds a dose to a player, unless they're shielded. */
	public static void irradiate(ServerPlayer player, int ticks) {
		ticks = HerbalRemedies.radiationDose(player, ticks);
		if (!isShielded(player) && ticks > 0) {
			MobEffectInstance current = player.getEffect(ModEffects.RADIATION_SICKNESS);
			int dose = Math.min(MAX_DOSE, (current == null ? 0 : current.getDuration()) + ticks);
			player.forceAddEffect(new MobEffectInstance(ModEffects.RADIATION_SICKNESS, dose, level(dose)), null);
		}
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.tickCount % INTERVAL != 0 || !player.isAlive()) {
				continue;
			}
			irradiate(player, carriedDose(player.getInventory()) * INTERVAL / 20);
			MobEffectInstance sickness = player.getEffect(ModEffects.RADIATION_SICKNESS);
			if (sickness == null) {
				continue;
			}
			// As the dose wears off, so does the level.
			int level = level(sickness.getDuration());
			if (level < sickness.getAmplifier()) {
				player.forceAddEffect(new MobEffectInstance(ModEffects.RADIATION_SICKNESS, sickness.getDuration(), level), null);
			}
			if (level >= 1) {
				player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, SYMPTOM_TICKS, level >= 3 ? 1 : 0, true, false, true));
			}
			if (level >= 2) {
				player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, SYMPTOM_TICKS, 0, true, false, true));
			}
		}
	}
}
