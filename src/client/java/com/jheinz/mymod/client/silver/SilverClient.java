package com.jheinz.mymod.client.silver;

import java.util.List;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.jheinz.mymod.silver.EvilBane;
import com.jheinz.mymod.silver.HolySilver;
import com.jheinz.mymod.silver.HolySilverArmor;

/**
 * Silver on the client: tooltips for silver's bite against the evil, the holy silver armor's ward and silver that can
 * be consecrated, and the faint golden glow that hangs about anyone in the full holy silver set.
 */
public final class SilverClient {
	private static final DustParticleOptions GLOW = new DustParticleOptions(0xFFF0B8, 0.6f);
	/** Chance each tick of a mote of light rising off someone in the full set. */
	private static final float GLOW_CHANCE = 0.2f;

	private SilverClient() {
	}

	public static void initialize() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> tooltip(stack, lines));
		ClientTickEvents.END_CLIENT_TICK.register(SilverClient::glow);
	}

	private static void tooltip(ItemStack stack, List<Component> lines) {
		// Just under the name, above vanilla's attribute lines.
		int at = Math.min(1, lines.size());
		EvilBane bane = EvilBane.of(stack);
		if (bane != null) {
			lines.add(at++, Component.translatable("item.mymod.evil_bane", formatMultiplier(bane.multiplier()))
					.withStyle(ChatFormatting.GOLD));
			if (bane.holy()) {
				lines.add(at++, Component.translatable("item.mymod.evil_bane.holy_fire").withStyle(ChatFormatting.GOLD));
			}
		}
		if (HolySilverArmor.isPiece(stack)) {
			lines.add(at++, Component.translatable("item.mymod.holy_silver_armor.piece",
					Math.round(HolySilverArmor.REDUCTION_PER_PIECE * 100)).withStyle(ChatFormatting.GOLD));
			lines.add(at++, Component.translatable("item.mymod.holy_silver_armor.full_set").withStyle(ChatFormatting.GRAY));
		}
		if (HolySilver.consecrated(stack.getItem()) != null) {
			lines.add(at, Component.translatable("item.mymod.silver.consecrate").withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	private static String formatMultiplier(float multiplier) {
		return multiplier == Math.round(multiplier) ? String.valueOf(Math.round(multiplier)) : String.valueOf(multiplier);
	}

	private static void glow(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.isPaused()) {
			return;
		}
		RandomSource random = minecraft.level.getRandom();
		for (Player player : minecraft.level.players()) {
			boolean ownFirstPerson = player == minecraft.getCameraEntity() && minecraft.options.getCameraType().isFirstPerson();
			if (ownFirstPerson || player.isInvisible() || random.nextFloat() >= GLOW_CHANCE || !HolySilverArmor.fullSet(player)) {
				continue;
			}
			minecraft.level.addParticle(GLOW, player.getRandomX(0.8), player.getRandomY(), player.getRandomZ(0.8), 0.0, 0.02, 0.0);
		}
	}
}
