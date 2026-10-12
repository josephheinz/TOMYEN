package com.jheinz.mymod.client.neuralink;

import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import org.joml.Vector2f;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.ModEffects;
import com.jheinz.mymod.neuralink.NeuralCore;
import com.jheinz.mymod.neuralink.NeuralinkItem;
import com.jheinz.mymod.neuralink.NeuralinkModule;

import static com.jheinz.mymod.client.neuralink.NeuralStyle.CYAN;
import static com.jheinz.mymod.client.neuralink.NeuralStyle.DARK;
import static com.jheinz.mymod.client.neuralink.NeuralStyle.DIM;
import static com.jheinz.mymod.client.neuralink.NeuralStyle.GLITCH;
import static com.jheinz.mymod.client.neuralink.NeuralStyle.HOT;
import static com.jheinz.mymod.client.neuralink.NeuralStyle.TEXT;
import static com.jheinz.mymod.client.neuralink.NeuralStyle.argb;
import static com.jheinz.mymod.client.neuralink.NeuralStyle.brackets;
import static com.jheinz.mymod.client.neuralink.NeuralStyle.mix;

/**
 * The active modules' HUD, in the cyan glass of the Neuralink's interface:
 * <ul>
 * <li>The Neural Heat panel in the bottom-left corner, while a Neuralink with an active module is worn: the heat
 * bar, the selected module and whether it's ready, and a chip per active with its cooldown. Overheated, it
 * glitches; knocked out by an EMP, it's static.
 * <li>The module selector, round the crosshair, while the trigger is held.
 * <li>Neural Hijack's lock-on: a ring filling round the crosshair and brackets on the target.
 * <li>Full-screen effects ({@link #extractScreen}): Overclock's grey bullet time, Blink's flash, an overheat's
 * red glitch, an EMP's static, and the frame round a Hive Link view.
 * </ul>
 */
public final class NeuralHud {
	private static final int PANEL_WIDTH = 140;
	private static final int PANEL_HEIGHT = 44;
	private static final int SELECTOR_RADIUS = 52;
	private static final long BLINK_FLASH_MILLIS = 350;
	private static final long OVERHEAT_FLASH_MILLIS = 900;
	private static final long EMP_FLASH_MILLIS = 1200;

	private NeuralHud() {
	}

	// ------------------------------------------------------------------------------------------------ screen effects

	public static void extractScreen(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		int width = graphics.guiWidth(), height = graphics.guiHeight();
		long now = Util.getMillis();

		MobEffectInstance overclock = player.getEffect(ModEffects.OVERCLOCKED);
		if (overclock != null) {
			bulletTime(graphics, minecraft.font, width, height, overclock, now);
		}
		if (NeuralTrigger.hiveView() != null) {
			hiveLink(graphics, minecraft.font, width, height, NeuralTrigger.hiveView(), now);
		}

		long blink = now - NeuralClient.blinkMillis;
		if (blink < BLINK_FLASH_MILLIS) {
			float fade = 1.0f - blink / (float) BLINK_FLASH_MILLIS;
			graphics.fill(0, 0, width, height, argb((int) (0xB0 * fade * fade), 0xCFFBFF));
			slices(graphics, width, height, NeuralClient.blinkMillis + blink / 40, 8, fade, CYAN);
		}
		long overheat = now - NeuralClient.overheatMillis;
		if (overheat < OVERHEAT_FLASH_MILLIS) {
			float fade = 1.0f - overheat / (float) OVERHEAT_FLASH_MILLIS;
			graphics.fill(0, 0, width, height, argb((int) (0x70 * fade), 0xFF1A1A));
			slices(graphics, width, height, NeuralClient.overheatMillis + overheat / 50, 10, fade, GLITCH);
		}
		long emp = now - NeuralClient.empMillis;
		if (emp < EMP_FLASH_MILLIS) {
			float fade = 1.0f - emp / (float) EMP_FLASH_MILLIS;
			if (emp < 150) {
				graphics.fill(0, 0, width, height, argb(0xC0, 0xFFFFFF));
			}
			noise(graphics, 0, 0, width, height, (int) (900 * fade), now / 30, (int) (0xC0 * fade));
		} else if (NeuralinkItem.isJammed(player) && !NeuralinkItem.installedModules(player).isEmpty()) {
			// A light snow of static for as long as the implant's down.
			noise(graphics, 0, 0, width, height, 140, now / 40, 0x30);
		}
	}

	/** Overclock: the world greys out and slows, with bars top and bottom, fading in and out at the ends. */
	private static void bulletTime(GuiGraphicsExtractor graphics, Font font, int width, int height, MobEffectInstance overclock, long now) {
		float fade = Mth.clamp(overclock.getDuration() / 10.0f, 0.0f, 1.0f);
		graphics.fill(0, 0, width, height, argb((int) (0x58 * fade), 0x8C96A3));
		int bar = (int) (height * 0.06f * fade);
		graphics.fill(0, 0, width, bar, argb((int) (0xC0 * fade), 0x000000));
		graphics.fill(0, height - bar, width, height, argb((int) (0xC0 * fade), 0x000000));
		for (int y = (int) (now / 30 % 4); y < height; y += 4) {
			graphics.horizontalLine(0, width, y, argb((int) (0x0C * fade), 0xFFFFFF));
		}
		int accent = NeuralinkModule.OVERCLOCK.color();
		String label = String.format(Locale.ROOT, "◢ %s %.1fs ◣",
				Component.translatable("hud.mymod.overclock").getString(), overclock.getDuration() / 20.0f);
		graphics.centeredText(font, label, width / 2, bar + 4, argb((int) (0xFF * fade), accent));
	}

	/** The Hive Link: an amber frame and the teammate's name while looking through their eyes. */
	private static void hiveLink(GuiGraphicsExtractor graphics, Font font, int width, int height, Player mate, long now) {
		int accent = NeuralinkModule.HIVE_MIND.color();
		for (int y = (int) (now / 25 % 3); y < height; y += 3) {
			graphics.horizontalLine(0, width, y, argb(0x10, accent));
		}
		brackets(graphics, 8, 8, width - 8, height - 8, 24, 2, argb(0xD0, accent));
		String label = Component.translatable("hud.mymod.hive_link", mate.getName()).getString().toUpperCase(Locale.ROOT);
		graphics.centeredText(font, label + (now % 1000 < 600 ? " ●" : " ○"), width / 2, 14, argb(0xFF, accent));
	}

	/** Bands of the screen torn sideways, in the glitch colour. */
	private static void slices(GuiGraphicsExtractor graphics, int width, int height, long seed, int count, float fade, int color) {
		Random random = new Random(seed);
		for (int i = 0; i < count; i++) {
			int y = random.nextInt(height);
			int h = 1 + random.nextInt(5);
			int x = random.nextInt(width / 2);
			graphics.fill(x, y, x + width / 3 + random.nextInt(width / 2), y + h, argb((int) (0x90 * fade), i % 2 == 0 ? color : 0xFFFFFF));
		}
	}

	/** Specks of static. */
	private static void noise(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int specks, long seed, int alpha) {
		Random random = new Random(seed);
		for (int i = 0; i < specks; i++) {
			int x = x0 + random.nextInt(Math.max(1, x1 - x0));
			int y = y0 + random.nextInt(Math.max(1, y1 - y0));
			int grey = 0x60 + random.nextInt(0xA0);
			graphics.fill(x, y, x + 1 + random.nextInt(2), y + 1, argb(alpha, grey << 16 | grey << 8 | grey));
		}
	}

	// ------------------------------------------------------------------------------------------------ heat, selector, lock-on

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		Set<NeuralinkModule> installed = NeuralinkItem.installedModules(player);
		if (installed.isEmpty()) {
			return;
		}
		if (NeuralinkItem.isJammed(player)) {
			jammedPanel(graphics, minecraft.font, player);
			return;
		}
		List<NeuralinkModule> actives = NeuralTrigger.actives(player);
		if (actives.isEmpty()) {
			return;
		}
		heatPanel(graphics, minecraft.font, actives);
		if (NeuralClient.channel() == NeuralinkModule.NEURAL_HIJACK) {
			lockOn(graphics, minecraft, delta.getGameTimeDeltaPartialTick(false));
		}
		if (NeuralTrigger.isSelecting()) {
			selector(graphics, minecraft.font, actives);
		}
	}

	private static void heatPanel(GuiGraphicsExtractor graphics, Font font, List<NeuralinkModule> actives) {
		long now = Util.getMillis();
		boolean overheated = NeuralClient.overheated();
		Random glitch = new Random(now / 60);
		// Overheated, the whole panel stutters sideways now and then.
		int jitter = overheated && glitch.nextInt(4) == 0 ? glitch.nextInt(5) - 2 : 0;
		int x0 = 6 + jitter, y1 = graphics.guiHeight() - 6, y0 = y1 - PANEL_HEIGHT, x1 = x0 + PANEL_WIDTH;
		float heat = NeuralClient.heat() / NeuralCore.MAX_HEAT;
		int accent = overheated ? (now % 300 < 150 ? GLITCH : HOT) : CYAN;
		NeuralStyle.panel(graphics, x0, y0, x1, y1, accent);

		// Heading and reading.
		String title = Component.translatable(overheated ? "hud.mymod.neural_heat.overheat" : "hud.mymod.neural_heat").getString();
		if (overheated && now % 500 < 90) {
			graphics.text(font, title, x0 + 4, y0 + 4, argb(0xB0, CYAN), false);
		}
		graphics.text(font, title, x0 + 5, y0 + 4, overheated ? argb(0xFF, accent) : DIM, false);
		String percent = Math.round(heat * 100) + "%";
		graphics.text(font, percent, x1 - 5 - font.width(percent), y0 + 4, argb(0xFF, heatColor(heat, overheated, now)), false);

		// The bar, with the lockout's release point marked while overheated.
		int bx0 = x0 + 5, bx1 = x1 - 5, by = y0 + 15;
		graphics.fill(bx0, by, bx1, by + 4, argb(0x30, CYAN));
		int filled = bx0 + Math.round((bx1 - bx0) * Mth.clamp(heat, 0.0f, 1.0f));
		graphics.fillGradient(bx0, by, filled, by + 4, argb(0xFF, heatColor(heat * 0.6f, overheated, now)), argb(0xFF, heatColor(heat, overheated, now)));
		if (overheated) {
			int release = bx0 + Math.round((bx1 - bx0) * NeuralCore.RECOVERED_HEAT / NeuralCore.MAX_HEAT);
			graphics.fill(release, by - 2, release + 1, by + 6, argb(0xFF, TEXT));
			for (int i = 0; i < 3; i++) {
				int x = bx0 + glitch.nextInt(bx1 - bx0);
				graphics.fill(x, by - 1, x + 2 + glitch.nextInt(8), by + 5, argb(0xC0, i % 2 == 0 ? GLITCH : CYAN));
			}
		}

		// The selected module and its state.
		NeuralinkModule selected = NeuralTrigger.selected();
		if (selected != null) {
			String name = "▸ " + selected.displayName().getString().toUpperCase(Locale.ROOT);
			graphics.text(font, name, x0 + 5, y0 + 23, argb(0xFF, selected.color()), false);
			String state;
			int stateColor;
			int cooldown = NeuralClient.cooldown(selected);
			if (overheated) {
				state = Component.translatable("hud.mymod.neural_heat.locked").getString();
				stateColor = argb(0xFF, accent);
			} else if (NeuralClient.channel() == selected) {
				state = Component.translatable("hud.mymod.neural_heat.active").getString();
				stateColor = argb((int) (0x80 + 0x7F * NeuralStyle.wave(now, 400.0f, 0.0f)), selected.color());
			} else if (cooldown > 0) {
				state = String.format(Locale.ROOT, "%.1fs", cooldown / 20.0f);
				stateColor = DIM;
			} else {
				state = Component.translatable("hud.mymod.neural_heat.ready").getString();
				stateColor = argb(0xFF, TEXT);
			}
			graphics.text(font, state, x1 - 5 - font.width(state), y0 + 23, stateColor, false);
		}

		// A chip per active, filling back up as its cooldown runs out; the selected one is bracketed.
		int cx = x0 + 5;
		for (NeuralinkModule module : actives) {
			int cy = y0 + 34;
			int cooldown = NeuralClient.cooldown(module);
			float charged = module.cooldownTicks() == 0 ? 1.0f : 1.0f - cooldown / (float) module.cooldownTicks();
			graphics.fill(cx, cy, cx + 7, cy + 5, argb(0x40, module.color()));
			graphics.fill(cx, cy + 5 - Math.round(5 * Mth.clamp(charged, 0.0f, 1.0f)), cx + 7, cy + 5, argb(cooldown > 0 ? 0x90 : 0xFF, module.color()));
			if (module == selected) {
				graphics.outline(cx - 2, cy - 2, 11, 9, argb(0xFF, TEXT));
			}
			cx += 12;
		}
		String hint = Component.keybind("key.mymod.neural_trigger").getString().toUpperCase(Locale.ROOT);
		graphics.text(font, "[" + hint + "]", x1 - 5 - font.width("[" + hint + "]"), y0 + 33, DARK, false);
	}

	/** Cyan when cool, through orange to red when nearly overheating; flickering once it has. */
	private static int heatColor(float heat, boolean overheated, long now) {
		if (overheated) {
			return now % 200 < 100 ? GLITCH : HOT;
		}
		return heat < 0.5f ? mix(CYAN, 0xFFC23D, heat * 2.0f) : mix(0xFFC23D, 0xFF2A2A, (heat - 0.5f) * 2.0f);
	}

	/** Knocked out by an EMP: the panel is nothing but static and a reboot countdown. */
	private static void jammedPanel(GuiGraphicsExtractor graphics, Font font, LocalPlayer player) {
		long now = Util.getMillis();
		int x0 = 6, y1 = graphics.guiHeight() - 6, y0 = y1 - PANEL_HEIGHT, x1 = x0 + PANEL_WIDTH;
		NeuralStyle.panel(graphics, x0, y0, x1, y1, 0x7A8A99);
		noise(graphics, x0 + 1, y0 + 1, x1 - 1, y1 - 1, 260, now / 50, 0x90);
		String lost = Component.translatable("hud.mymod.neural_heat.signal_lost").getString();
		graphics.text(font, lost, x0 + 5 + (now % 700 < 80 ? 1 : 0), y0 + 8, argb(0xFF, now % 900 < 450 ? GLITCH : TEXT), true);
		MobEffectInstance jam = player.getEffect(ModEffects.NEURAL_STATIC);
		if (jam != null) {
			String reboot = Component.translatable("hud.mymod.neural_heat.rebooting", String.format(Locale.ROOT, "%.0f", jam.getDuration() / 20.0f))
					.getString();
			graphics.text(font, reboot, x0 + 5, y0 + 24, DIM, true);
		}
	}

	/** Neural Hijack: a ring filling round the crosshair, and brackets locked onto the target. */
	private static void lockOn(GuiGraphicsExtractor graphics, Minecraft minecraft, float partialTick) {
		int cx = graphics.guiWidth() / 2, cy = graphics.guiHeight() / 2;
		int accent = NeuralinkModule.NEURAL_HIJACK.color();
		float progress = NeuralClient.status.progress();
		NeuralStyle.ring(graphics, cx, cy, 11, 0.0f, 1.0f, argb(0x40, accent));
		if (progress > 0.0f) {
			NeuralStyle.ring(graphics, cx, cy, 11, 0.0f, progress, argb(0xFF, accent));
			NeuralStyle.ring(graphics, cx, cy, 12, 0.0f, progress, argb(0x80, accent));
		}
		String label = Component.translatable("hud.mymod.hijack", Math.round(progress * 100)).getString();
		graphics.centeredText(minecraft.font, label, cx, cy + 16, argb(0xFF, accent));

		Entity target = minecraft.level == null ? null : minecraft.level.getEntity(NeuralClient.status.target());
		if (target != null && progress > 0.0f) {
			Vec3 middle = target.getPosition(partialTick).add(0.0, target.getBbHeight() / 2.0, 0.0);
			Vector2f at = NeuralSenses.project(graphics, middle);
			if (at != null) {
				int size = (int) Mth.clamp(220.0f / (float) Math.max(1.0, target.distanceTo(minecraft.player)), 10.0f, 60.0f);
				int x = Math.round(at.x), y = Math.round(at.y);
				// The brackets close in as the hijack takes hold.
				int half = Math.round(size * (1.4f - 0.4f * progress));
				brackets(graphics, x - half, y - half, x + half, y + half, 5, 1, argb(0xFF, accent));
			}
		}
	}

	/** The module selector: the actives on a ring round the crosshair, clockwise from the top, and the cursor. */
	private static void selector(GuiGraphicsExtractor graphics, Font font, List<NeuralinkModule> actives) {
		int cx = graphics.guiWidth() / 2, cy = graphics.guiHeight() / 2;
		long now = Util.getMillis();
		NeuralinkModule hovered = NeuralTrigger.hovered(actives);
		NeuralStyle.ring(graphics, cx, cy, SELECTOR_RADIUS + 14, 0.0f, 1.0f, argb(0x30, CYAN));
		NeuralStyle.ring(graphics, cx, cy, 16, 0.0f, 1.0f, argb(0x70, CYAN));
		// A sweep running round the outer ring.
		float sweep = (now % 2000) / 2000.0f;
		NeuralStyle.ring(graphics, cx, cy, SELECTOR_RADIUS + 14, sweep, sweep + 0.08f, argb(0xC0, CYAN));

		for (int i = 0; i < actives.size(); i++) {
			NeuralinkModule module = actives.get(i);
			float angle = Mth.TWO_PI * i / actives.size();
			int nx = Math.round(cx + Mth.sin(angle) * SELECTOR_RADIUS), ny = Math.round(cy - Mth.cos(angle) * SELECTOR_RADIUS);
			String name = module.displayName().getString().toUpperCase(Locale.ROOT);
			int cooldown = NeuralClient.cooldown(module);
			String detail = cooldown > 0 ? String.format(Locale.ROOT, "%.1fs", cooldown / 20.0f)
					: Component.translatable(module.kind() == NeuralinkModule.Kind.CHANNEL ? "hud.mymod.selector.hold" : "hud.mymod.selector.tap").getString();
			int half = Math.max(font.width(name), font.width(detail)) / 2 + 5;
			boolean hover = module == hovered;
			if (hover) {
				NeuralStyle.line(graphics, cx + Mth.sin(angle) * 17, cy - Mth.cos(angle) * 17, nx - Mth.sin(angle) * 12, ny + Mth.cos(angle) * 12,
						argb(0xC0, module.color()));
			}
			graphics.fillGradient(nx - half, ny - 11, nx + half, ny + 11, argb(hover ? 0xE0 : 0xB0, mix(0x0A1420, module.color(), hover ? 0.25f : 0.05f)),
					argb(hover ? 0xE0 : 0xB0, 0x05090F));
			graphics.outline(nx - half, ny - 11, half * 2, 22, argb(hover ? 0xFF : 0x60, module.color()));
			if (hover) {
				brackets(graphics, nx - half - 2, ny - 13, nx + half + 2, ny + 13, 5, 1, argb(0xFF, TEXT));
			}
			graphics.centeredText(font, name, nx, ny - 8, argb(cooldown > 0 ? 0x90 : 0xFF, module.color()));
			graphics.centeredText(font, detail, nx, ny + 2, module == NeuralTrigger.selected() ? TEXT : DIM);
		}

		int px = cx + (int) Math.round(NeuralTrigger.cursorX()), py = cy + (int) Math.round(NeuralTrigger.cursorY());
		graphics.fill(px - 1, py - 1, px + 2, py + 2, argb(0xFF, hovered != null ? hovered.color() : TEXT));
		graphics.outline(px - 3, py - 3, 7, 7, argb(0x80, CYAN));
	}
}
