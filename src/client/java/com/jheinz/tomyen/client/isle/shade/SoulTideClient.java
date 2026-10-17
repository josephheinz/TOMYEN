package com.jheinz.tomyen.client.isle.shade;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.isle.IsleDimension;
import com.jheinz.tomyen.isle.shade.IsleShades;

/**
 * How the Soul Tide shows on the Isle, all from one number the server syncs (0 calm to 1; see ShadeManager), eased in
 * over a few seconds so nothing jumps:
 *
 * <ul>
 * <li>the fog closes in: its far edge comes in by up to half, its near edge by more;
 * <li>souls rise out of the ground round you, a few a second at a high tide;
 * <li>past {@value #HUD_FROM} a line in the top left, "The Soul Tide runs high", or past {@value #HUD_SURGE},
 * "surges", with a five-pip gauge.
 * </ul>
 */
public final class SoulTideClient {
	private static final float HUD_FROM = 0.4f;
	private static final float HUD_SURGE = 0.7f;
	private static final int SOUL = 0x7FE7F0;
	private static float shown;

	private SoulTideClient() {
	}

	public static void initialize() {
		ClientTickEvents.END_CLIENT_TICK.register(SoulTideClient::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, MyMod.id("isle_soul_tide"), SoulTideClient::extractHud);
	}

	private static boolean onIsle(Level level) {
		return level != null && level.dimension() == IsleDimension.LEVEL;
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;
		if (player == null || level == null || minecraft.isPaused()) {
			return;
		}
		Float synced = player.getAttached(IsleShades.TIDE);
		float target = onIsle(level) && synced != null ? synced : 0.0f;
		shown += (target - shown) * 0.02f;
		if (Math.abs(target - shown) < 0.001f) {
			shown = target;
		}
		if (!onIsle(level) || shown < 0.05f) {
			return;
		}
		RandomSource random = level.getRandom();
		float souls = shown * 1.2f;
		while (souls > 0.0f) {
			if (souls >= 1.0f || random.nextFloat() < souls) {
				double x = player.getX() + (random.nextDouble() - 0.5) * 28.0;
				double z = player.getZ() + (random.nextDouble() - 0.5) * 28.0;
				double y = player.getY() - 1.0 + random.nextDouble() * 4.0;
				level.addParticle(ParticleTypes.SOUL, x, y, z, 0.0, 0.03 + random.nextDouble() * 0.03, 0.0);
			}
			souls -= 1.0f;
		}
	}

	/** Called as the Isle's fog is set up each frame (SoulTideFogMixin). */
	public static void thickenFog(FogData fog, ClientLevel level) {
		if (shown > 0.005f && onIsle(level)) {
			fog.environmentalEnd *= 1.0f - 0.5f * shown;
			fog.environmentalStart *= 1.0f - 0.8f * shown;
		}
	}

	private static void extractHud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (shown < HUD_FROM || minecraft.player == null || !onIsle(minecraft.level)) {
			return;
		}
		float fade = Mth.clamp((shown - HUD_FROM) / 0.08f, 0.0f, 1.0f);
		float pulse = 0.85f + 0.15f * Mth.sin((minecraft.player.tickCount + delta.getGameTimeDeltaPartialTick(true)) * 0.08f);
		int alpha = (int) (210 * fade * pulse);
		if (alpha < 8) {
			return;
		}
		Component line = Component.translatable(shown >= HUD_SURGE ? "hud.tomyen.isle.tide_surges" : "hud.tomyen.isle.tide_high");
		int x = 6, y = 6;
		graphics.text(minecraft.font, line, x, y, ARGB.color(alpha, SOUL), true);
		int pips = Math.round(shown * 5.0f);
		int left = x + minecraft.font.width(line) + 5;
		for (int i = 0; i < 5; i++) {
			int colour = i < pips ? ARGB.color(alpha, SOUL) : ARGB.color(alpha / 3, 0xC8D8E0);
			graphics.fill(left + i * 4, y + 2, left + i * 4 + 3, y + 6, colour);
		}
	}
}
