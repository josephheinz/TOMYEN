package com.jheinz.tomyen.client.detector;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.detector.DetectorFix;
import com.jheinz.tomyen.detector.Detectors;

/**
 * The detectors on the client: each gadget's reading, worked out every tick it's held (PendulumClient,
 * MetalDetectorClient, TrackerClient, UfoRadarClient, AlmanacClient), drawn by {@link DetectorHud} in one shared
 * panel and shown on the item itself through the {@code tomyen:detector} model property ({@link DetectorProperty}).
 */
public final class DetectorClient {
	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
	private static final String[] COMPASS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

	private DetectorClient() {
	}

	public static void initialize() {
		ClientTickEvents.END_CLIENT_TICK.register(DetectorClient::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, MyMod.id("detector"), DetectorHud::extract);
		RangeSelectItemModelProperties.ID_MAPPER.put(MyMod.id("detector"), DetectorProperty.MAP_CODEC);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			PendulumClient.reset();
			MetalDetectorClient.reset();
			UfoRadarClient.reset();
			return;
		}
		if (minecraft.isPaused()) {
			return;
		}
		PendulumClient.tick(minecraft, player);
		MetalDetectorClient.tick(minecraft, player);
		TrackerClient.tick(minecraft, player);
		UfoRadarClient.tick(minecraft, player);
		AlmanacClient.tick(minecraft, player);
	}

	/** The stack of this item in the player's main hand, else off hand, else empty. */
	static ItemStack held(Player player, Item item) {
		if (player.getMainHandItem().is(item)) {
			return player.getMainHandItem();
		}
		return player.getOffhandItem().is(item) ? player.getOffhandItem() : ItemStack.EMPTY;
	}

	static DetectorFix fix(Player player) {
		DetectorFix fix = player.getAttached(Detectors.FIX);
		return fix == null ? DetectorFix.NONE : fix;
	}

	/** Which way a point lies from where the player faces, in degrees: 0 ahead, positive to the right, ±180 behind. */
	static float relativeYaw(Player player, double x, double z) {
		double dx = x - player.getX(), dz = z - player.getZ();
		float bearing = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
		return Mth.wrapDegrees(bearing - player.getYRot());
	}

	/** An arrow pointing that way on screen (up is ahead). */
	static String arrow(float relativeYaw) {
		return ARROWS[Math.floorMod(Math.round(relativeYaw / 45.0f), 8)];
	}

	/** The compass point a direction is nearest, N, NE and so on. */
	static String compass(double dx, double dz) {
		double angle = Math.toDegrees(Math.atan2(dx, -dz));
		return COMPASS[Math.floorMod((int) Math.round(angle / 45.0), 8)];
	}

	static double flatDistance(Player player, double x, double z) {
		return Math.sqrt(Mth.square(x - player.getX()) + Mth.square(z - player.getZ()));
	}

	static void play(Minecraft minecraft, Player player, SoundEvent sound, float volume, float pitch) {
		minecraft.level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch, false);
	}
}
