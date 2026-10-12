package com.jheinz.mymod.client.neuralink;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.neuralink.NeuralNetworking;
import com.jheinz.mymod.neuralink.NeuralinkItem;
import com.jheinz.mymod.neuralink.NeuralinkModule;

/**
 * The Neural Trigger key (V) and the Hive Link key (H).
 *
 * <p>The trigger fires the selected active module. A channelled module (lasers, telekinesis, hijack) runs
 * while the key is held: the press and the release both go to the server. A one-shot fires when the key is
 * tapped, on release. With more than one active installed, holding the key opens a radial selector instead
 * (steered with the mouse, picked on release); while a channelled module is selected, hold Sneak as well to
 * open it, since holding the key alone channels.
 *
 * <p>Holding the Hive Link key with Hive Mind installed looks through the eyes of the nearest Hive teammate
 * the client can see.
 */
public final class NeuralTrigger {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(MyMod.id("neuralink"));
	public static final KeyMapping TRIGGER_KEY = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.mymod.neural_trigger", InputConstants.Type.KEYBOARD, InputConstants.KEY_V, CATEGORY));
	public static final KeyMapping HIVE_LINK_KEY = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.mymod.hive_link", InputConstants.Type.KEYBOARD, InputConstants.KEY_H, CATEGORY));

	/** Holding the trigger this long (with more than one active) opens the selector instead of firing. */
	private static final int HOLD_TICKS = 5;
	/** How far (in GUI pixels) the cursor has to leave the centre to point at a module. */
	public static final double DEADZONE = 10.0;
	public static final double CURSOR_LIMIT = 48.0;
	private static final double CURSOR_SPEED = 0.35;

	private static NeuralinkModule selected;
	private static boolean wasDown;
	private static int heldTicks;
	/** The channelled module whose press was sent, awaiting its release. */
	private static NeuralinkModule channelling;
	private static boolean selecting;
	private static double cursorX;
	private static double cursorY;
	private static Player hiveView;

	private NeuralTrigger() {
	}

	/** Loads the class, registering the keys. */
	public static void initialize() {
	}

	/** The actives in the worn Neuralink, in a stable order (the selector's, clockwise from the top). */
	public static List<NeuralinkModule> actives(Player player) {
		List<NeuralinkModule> actives = new ArrayList<>();
		for (NeuralinkModule module : NeuralinkItem.activeModules(player)) {
			if (module.isActive()) {
				actives.add(module);
			}
		}
		return actives;
	}

	public static NeuralinkModule selected() {
		return selected;
	}

	public static boolean isSelecting() {
		return selecting;
	}

	public static double cursorX() {
		return cursorX;
	}

	public static double cursorY() {
		return cursorY;
	}

	/** The teammate being looked through, or null. */
	public static Player hiveView() {
		return hiveView;
	}

	/** Mouse movement while the selector is open steers its cursor instead of the camera (see MouseHandlerRadialMixin). */
	public static void moveCursor(double dx, double dy) {
		cursorX += dx * CURSOR_SPEED;
		cursorY += dy * CURSOR_SPEED;
		double length = Math.sqrt(cursorX * cursorX + cursorY * cursorY);
		if (length > CURSOR_LIMIT) {
			cursorX *= CURSOR_LIMIT / length;
			cursorY *= CURSOR_LIMIT / length;
		}
	}

	/** The selector entry under the cursor, or null in the middle. Entries go clockwise from the top. */
	public static NeuralinkModule hovered(List<NeuralinkModule> actives) {
		if (actives.isEmpty() || cursorX * cursorX + cursorY * cursorY < DEADZONE * DEADZONE) {
			return null;
		}
		float turn = (float) (Math.atan2(cursorX, -cursorY) / Mth.TWO_PI);
		int index = Math.round(turn * actives.size());
		return actives.get(Math.floorMod(index, actives.size()));
	}

	/** Whether the server would fire this module now, as far as the client knows. */
	public static boolean ready(NeuralinkModule module) {
		return !NeuralClient.overheated() && NeuralClient.cooldown(module) == 0;
	}

	public static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		while (TRIGGER_KEY.consumeClick()) {
			// Only whether it's held matters.
		}
		if (player == null) {
			selecting = false;
			channelling = null;
			wasDown = false;
			return;
		}
		List<NeuralinkModule> actives = actives(player);
		if (selected == null || !actives.contains(selected)) {
			selected = actives.isEmpty() ? null : actives.getFirst();
		}
		boolean down = TRIGGER_KEY.isDown() && minecraft.gui.screen() == null && selected != null;

		if (down && !wasDown) {
			heldTicks = 0;
			boolean wantsSelector = player.isShiftKeyDown() && actives.size() > 1;
			if (selected.kind() == NeuralinkModule.Kind.CHANNEL && !wantsSelector) {
				if (ready(selected)) {
					send(selected, true);
					channelling = selected;
				} else {
					deny(player);
				}
			}
		}
		if (down) {
			heldTicks++;
			if (channelling == null && !selecting && actives.size() > 1 && heldTicks >= HOLD_TICKS) {
				selecting = true;
				cursorX = 0.0;
				cursorY = 0.0;
				player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.25f, 1.8f);
			}
		} else if (wasDown || channelling != null || selecting) {
			if (channelling != null) {
				send(channelling, false);
				channelling = null;
			} else if (selecting) {
				NeuralinkModule hovered = hovered(actives);
				if (hovered != null && hovered != selected) {
					selected = hovered;
					player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.35f, 1.4f);
				}
			} else if (wasDown && selected != null && selected.kind() == NeuralinkModule.Kind.ONE_SHOT) {
				if (ready(selected)) {
					send(selected, true);
				} else {
					deny(player);
				}
			}
			selecting = false;
		}
		wasDown = down;

		tickHiveView(minecraft, player);
	}

	private static void send(NeuralinkModule module, boolean pressed) {
		ClientPlayNetworking.send(new NeuralNetworking.Trigger(module.ordinal(), pressed));
	}

	private static void deny(LocalPlayer player) {
		player.playSound(SoundEvents.DISPENSER_FAIL, 0.35f, 2.0f);
	}

	private static void tickHiveView(Minecraft minecraft, LocalPlayer player) {
		boolean wanted = HIVE_LINK_KEY.isDown() && minecraft.gui.screen() == null && NeuralinkItem.isActive(player, NeuralinkModule.HIVE_MIND);
		if (wanted && (hiveView == null || !hiveView.isAlive() || hiveView.isRemoved())) {
			hiveView = nearestMate(minecraft, player);
			if (hiveView != null) {
				minecraft.setCameraEntity(hiveView);
				player.playSound(SoundEvents.BEACON_POWER_SELECT, 0.4f, 2.0f);
			}
		}
		if (!wanted && hiveView != null || hiveView != null && (!hiveView.isAlive() || hiveView.isRemoved())) {
			hiveView = null;
			minecraft.setCameraEntity(player);
		}
		while (HIVE_LINK_KEY.consumeClick()) {
			// Only whether it's held matters.
		}
	}

	private static Player nearestMate(Minecraft minecraft, LocalPlayer player) {
		Player nearest = null;
		for (NeuralNetworking.Mate mate : NeuralClient.mates) {
			Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(mate.entityId());
			if (entity instanceof Player other && other.isAlive() && (nearest == null || other.distanceToSqr(player) < nearest.distanceToSqr(player))) {
				nearest = other;
			}
		}
		return nearest;
	}
}
