package com.jheinz.mymod.client.launcher;

import org.joml.Vector2f;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.client.neuralink.NeuralSenses;
import com.jheinz.mymod.launcher.LauncherAction;
import com.jheinz.mymod.launcher.Launchers;
import com.jheinz.mymod.launcher.LockOn;
import com.jheinz.mymod.launcher.PG7Rocket;
import com.jheinz.mymod.launcher.RocketLauncherItem;

/**
 * The launcher on the client: rocket rendering and the roar of one in flight, starting the hoist the moment the
 * launcher is switched to (ahead of the server, which sends the same), its handling sounds, the rocket count in
 * the ammo HUD, and the lock-on display. While the server reports a lock building, brackets close in on the
 * target and a seeker tone beeps faster and higher; once locked the brackets turn red, read LOCKED, and the tone
 * holds steady.
 */
public final class LauncherClient {
	private static final int WHITE = 0xFFFFFFFF;
	private static final int RED = 0xFFFF5555;
	private static final int GRAY = 0xFFAAAAAA;
	private static final int ACQUIRING = 0xFFFFB020;
	private static final int LOCKED = 0xFFFF3030;
	private static final int ROCKET = 0xFF9DB06A;
	private static final int HOMING_ROCKET = 0xFFE8C21E;
	private static final int NAPALM_ROCKET = 0xFFFF7A1A;

	private static Launchers.LockState lock = Launchers.LockState.NONE;
	private static LockTone tone;
	private static int beepTimer;
	private static int lastSlot = -1;
	private static Item lastItem;

	private LauncherClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(Launchers.ROCKET, RocketRenderer::new);
		ClientPlayNetworking.registerGlobalReceiver(Launchers.LockState.TYPE, (payload, context) -> lock = payload);
		ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof PG7Rocket rocket) {
				Minecraft.getInstance().getSoundManager().play(new MotorSound(rocket));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client.player));
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, MyMod.id("rocket_lock"), LauncherClient::extractLock);
	}

	private static void tick(LocalPlayer player) {
		if (player == null) {
			lock = Launchers.LockState.NONE;
			stopTone();
			return;
		}
		ItemStack held = player.getMainHandItem();
		int slot = player.getInventory().getSelectedSlot();
		// Start the hoist straight away rather than a round trip later; the server's own start replaces this.
		if (held.getItem() instanceof RocketLauncherItem && (slot != lastSlot || !(lastItem instanceof RocketLauncherItem))) {
			held.set(Launchers.LAUNCHER_ACTION, LauncherAction.equip(player.level().getGameTime(), RocketLauncherItem.EQUIP_TICKS));
		}
		lastSlot = slot;
		lastItem = held.getItem();
		LauncherAnimation.tickCues(player);
		tickTones(player);
	}

	private static boolean locking(LocalPlayer player) {
		return lock.target() >= 0 && player.getMainHandItem().getItem() instanceof RocketLauncherItem;
	}

	static boolean locked() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player != null && locking(minecraft.player) && lock.locked();
	}

	/** Beeps quicken and rise as the lock builds; a locked target holds a steady tone. */
	private static void tickTones(LocalPlayer player) {
		if (!locking(player) || lock.locked()) {
			beepTimer = 0;
			if (locked() && (tone == null || tone.isStopped())) {
				tone = new LockTone();
				Minecraft.getInstance().getSoundManager().play(tone);
			} else if (!locked()) {
				stopTone();
			}
			return;
		}
		stopTone();
		float progress = lock.progress() / (float) LockOn.LOCK_TICKS;
		if (++beepTimer >= Math.round(Mth.lerp(progress, 9.0f, 2.0f))) {
			beepTimer = 0;
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(Launchers.LOCK_BEEP, 0.8f + 0.7f * progress, 0.5f));
		}
	}

	private static void stopTone() {
		if (tone != null) {
			Minecraft.getInstance().getSoundManager().stop(tone);
			tone = null;
		}
	}

	// ----------------------------------------------------------------------------------------------------- HUD

	/** The ammo HUD for a held launcher: rocket count, then what it's doing or which rocket is seated. */
	public static void extractAmmo(GuiGraphicsExtractor graphics, Minecraft minecraft, ItemStack launcher, int x, int y) {
		Item loaded = RocketLauncherItem.loadedRocket(launcher);
		graphics.text(minecraft.font, (loaded == null ? 0 : 1) + " / 1", x, y, loaded == null ? RED : WHITE, true);
		LauncherAction action = launcher.get(Launchers.LAUNCHER_ACTION);
		long now = minecraft.level == null ? 0 : minecraft.level.getGameTime();
		Component status;
		int color = GRAY;
		if (action == null || action.kind() == LauncherAction.Kind.EQUIP && !action.shouldered(now)) {
			status = Component.translatable("hud.mymod.rpg7.shouldering");
		} else if (action.kind() == LauncherAction.Kind.RELOAD) {
			status = Component.translatable("hud.mymod.rpg7.reloading");
		} else if (loaded != null) {
			status = Component.translatable(loaded.getDescriptionId());
			color = loaded == Launchers.PG7_HOMING_ROCKET ? HOMING_ROCKET : loaded == Launchers.PG7_NAPALM_ROCKET ? NAPALM_ROCKET : ROCKET;
		} else {
			status = Component.translatable("hud.mymod.punch_to_reload");
		}
		graphics.text(minecraft.font, status, x, y + 10, color, true);
	}

	/** Brackets round the target that close in as the lock builds, with a fill bar and LOCKING / LOCKED over them. */
	private static void extractLock(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null || !locking(minecraft.player)) {
			return;
		}
		Entity target = minecraft.level.getEntity(lock.target());
		if (target == null) {
			return;
		}
		float partialTick = delta.getGameTimeDeltaPartialTick(false);
		Vec3 base = target.getPosition(partialTick);
		Vector2f centre = NeuralSenses.project(graphics, base.add(0.0, target.getBbHeight() / 2.0, 0.0));
		Vector2f top = NeuralSenses.project(graphics, base.add(0.0, target.getBbHeight(), 0.0));
		if (centre == null || top == null) {
			return;
		}
		boolean locked = lock.locked();
		float progress = locked ? 1.0f : lock.progress() / (float) LockOn.LOCK_TICKS;
		float fit = Math.max(6.0f, Math.abs(centre.y - top.y) + 3.0f);
		int half = Math.round(fit * (1.0f + 1.6f * (1.0f - progress)));
		int cx = Math.round(centre.x);
		int cy = Math.round(centre.y);
		int color = locked ? (Util.getMillis() % 400 < 280 ? LOCKED : 0xFFAA2020) : ACQUIRING;
		int arm = Math.max(3, half / 2);
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sy = -1; sy <= 1; sy += 2) {
				int x = cx + sx * half;
				int y = cy + sy * half;
				graphics.fill(Math.min(x, x - sx * arm), y, Math.max(x, x - sx * arm) + 1, y + 1, color);
				graphics.fill(x, Math.min(y, y - sy * arm), x + 1, Math.max(y, y - sy * arm) + 1, color);
			}
		}
		if (locked) {
			// A diamond pip on the target.
			graphics.fill(cx - 1, cy - 2, cx + 2, cy + 3, color);
			graphics.fill(cx - 2, cy - 1, cx + 3, cy + 2, color);
		}
		int barY = cy + half + 3;
		graphics.fill(cx - half, barY, cx + half + 1, barY + 2, 0x80000000);
		graphics.fill(cx - half, barY, cx - half + Math.round((2 * half + 1) * progress), barY + 2, color);
		Component label = Component.translatable(locked ? "hud.mymod.lock.locked" : "hud.mymod.lock.locking");
		graphics.centeredText(minecraft.font, label, cx, cy - half - 11, color);
	}

	/** The steady tone while a target is locked. */
	private static final class LockTone extends AbstractTickableSoundInstance {
		LockTone() {
			super(Launchers.LOCK_TONE, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
			looping = true;
			delay = 0;
			relative = true;
			attenuation = SoundInstance.Attenuation.NONE;
			volume = 0.45f;
		}

		@Override
		public void tick() {
			if (!locked()) {
				stop();
			}
		}
	}

	/** The roar of a rocket's motor, following it until it's gone. */
	private static final class MotorSound extends AbstractTickableSoundInstance {
		private final PG7Rocket rocket;

		MotorSound(PG7Rocket rocket) {
			super(SoundEvents.ELYTRA_FLYING, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
			this.rocket = rocket;
			looping = true;
			delay = 0;
			volume = 2.0f;
			pitch = 1.6f;
			x = rocket.getX();
			y = rocket.getY();
			z = rocket.getZ();
		}

		@Override
		public void tick() {
			if (rocket.isRemoved()) {
				stop();
				return;
			}
			x = rocket.getX();
			y = rocket.getY();
			z = rocket.getZ();
		}
	}
}
