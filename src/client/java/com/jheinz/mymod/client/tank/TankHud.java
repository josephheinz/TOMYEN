package com.jheinz.mymod.client.tank;

import java.util.Locale;

import org.joml.Vector2f;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.client.neuralink.NeuralSenses;
import com.jheinz.mymod.client.neuralink.NeuralStyle;
import com.jheinz.mymod.tank.ShellType;
import com.jheinz.mymod.tank.Tank;
import com.jheinz.mymod.tank.TankGunnery;
import com.jheinz.mymod.tank.TankPart;

import static com.jheinz.mymod.client.neuralink.NeuralStyle.argb;

/**
 * The view from a tank's gunner's seat, drawn under the rest of the HUD (which it mostly replaces).
 * <ul>
 * <li>The sight: a window in a black frame, edged with a vignette, a green reticle (white in thermal) with mil
 * ticks and a range ladder for the round the gun is laid for. The camera is on the sight and follows the gun, so
 * the reticle is always where the gun will put the round; a ring shows where the gunner's mouse is asking the gun
 * to go, running ahead while the turret slews. Also the rangefinder's last reading and the magnification. Thermal
 * darkens the view so living things glow white.
 * <li>Above the window: a compass strip on the turret's heading, and top right an azimuth indicator: the
 * turret (gun up) over the hull outline turned as the hull lies under it, each part coloured by its condition.
 * <li>Below the window: the breech (empty, loading with its progress, loaded and with what), the next round and
 * what's left in the rack; speed, gear and throttle; hull integrity and the parts; smoke salvos and the coax's
 * heat; and the sneak-hold to climb out.
 * <li>From outside (F5), a single compact panel with the essentials and a ring where the gun will put the round.
 * <li>Climbing in or out, the view fades through black between the world and the sight.
 * </ul>
 * Which view is up goes by {@link TankClient#sightTank}. Vanilla's hotbar, bars and crosshair are hidden in the
 * sight, and the tank's (huge) health never shows as mount hearts.
 */
public final class TankHud {
	private static final int GREEN = 0x6CFF6C;
	private static final int WHITE = 0xF2F2F2;
	private static final int AMBER = 0xFFB000;
	private static final int RED = 0xFF4030;
	private static final int TEXT = 0xFFDFFFE0;
	private static final int DIM = 0xFF6F9A6F;
	private static final int FRAME = 0xF2050605;
	private static final int PANEL = 0xC0101410;
	private static final String[] BEARINGS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
	private static final int[] LADDER = {400, 800, 1200, 1600, 2000};
	private static final int FADE_TICKS = 10;

	private static int seatedTicks;
	private static int upFlash;
	private static int lastBreech = -1;
	private static long lastTick;

	private TankHud() {
	}

	static void initialize() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, MyMod.id("tank_sight"), TankHud::extract);
		for (Identifier element : new Identifier[] {VanillaHudElements.HOTBAR, VanillaHudElements.HEALTH_BAR, VanillaHudElements.FOOD_BAR,
				VanillaHudElements.ARMOR_BAR, VanillaHudElements.AIR_BAR, VanillaHudElements.EXPERIENCE_LEVEL, VanillaHudElements.INFO_BAR,
				VanillaHudElements.CROSSHAIR}) {
			HudElementRegistry.replaceElement(element, vanilla -> (graphics, delta) -> {
				if (!TankClient.isCrewSightView()) {
					vanilla.extractRenderState(graphics, delta);
				}
			});
		}
		HudElementRegistry.replaceElement(VanillaHudElements.MOUNT_HEALTH, vanilla -> (graphics, delta) -> {
			if (TankClient.crewTank() == null) {
				vanilla.extractRenderState(graphics, delta);
			}
		});
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		Tank tank = TankClient.crewTank();
		if (player == null || tank == null) {
			seatedTicks = 0;
			lastBreech = -1;
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		boolean firstPerson = minecraft.options.getCameraType().isFirstPerson();
		if (!tank.isSeated(player)) {
			seatedTicks = 0;
			if (firstPerson) {
				climbFade(graphics, tank, partial);
			}
			return;
		}
		trackBreech(tank);
		if (TankClient.sightTank() == tank) {
			sight(graphics, minecraft, player, tank, partial);
			int fade = 255 - Math.min(255, (int) ((seatedTicks + partial) * 255 / FADE_TICKS));
			if (fade > 0) {
				graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), argb(fade, 0));
			}
		} else if (!firstPerson) {
			external(graphics, minecraft, tank, partial);
		}
	}

	/** Counts ticks in the seat and spots the breech closing on a round, for the fade-in and the "UP!" call. */
	private static void trackBreech(Tank tank) {
		long tick = tank.level().getGameTime();
		if (tick != lastTick) {
			lastTick = tick;
			seatedTicks++;
			if (upFlash > 0) {
				upFlash--;
			}
			int breech = tank.breechState();
			if (breech == TankGunnery.BREECH_LOADED && lastBreech == TankGunnery.BREECH_LOADING) {
				upFlash = 30;
			}
			lastBreech = breech;
		}
	}

	/** Through the climb: from the world into black and up into the sight, or back out again. */
	private static void climbFade(GuiGraphicsExtractor graphics, Tank tank, float partial) {
		float progress = tank.sequenceProgress(partial);
		float black;
		if (tank.sequence() == Tank.SEQ_ENTERING) {
			black = Mth.clamp((progress - 0.7f) / 0.15f, 0.0f, 1.0f);
		} else {
			black = progress < 0.1f ? 1.0f : 1.0f - Mth.clamp((progress - 0.1f) / 0.2f, 0.0f, 1.0f);
		}
		if (black > 0.0f) {
			graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), argb((int) (black * 255), 0));
		}
	}

	// --- The sight ------------------------------------------------------------------------------------------

	private static void sight(GuiGraphicsExtractor graphics, Minecraft minecraft, LocalPlayer player, Tank tank, float partial) {
		Font font = minecraft.font;
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		boolean thermal = TankClient.thermal();
		int ink = thermal ? WHITE : GREEN;

		// The window, and the frame round it.
		int top = Math.max(26, height / 9);
		int bottom = height - Math.max(52, height / 4);
		int windowHeight = bottom - top;
		int windowWidth = Math.min(width - 24, Math.round(windowHeight * 1.6f));
		int left = (width - windowWidth) / 2;
		int right = left + windowWidth;
		if (thermal) {
			graphics.fill(left, top, right, bottom, argb(0x88, 0x0A140C));
		}
		vignette(graphics, left, top, right, bottom);
		graphics.fill(0, 0, width, top, FRAME);
		graphics.fill(0, bottom, width, height, FRAME);
		graphics.fill(0, top, left, bottom, FRAME);
		graphics.fill(right, top, width, bottom, FRAME);
		graphics.outline(left - 1, top - 1, windowWidth + 2, windowHeight + 2, argb(0x60, ink));
		if (tank.hurtTime > 0) {
			int alpha = 0x70 * tank.hurtTime / Math.max(1, tank.hurtDuration);
			graphics.outline(left, top, windowWidth, windowHeight, argb(alpha, RED));
			graphics.outline(left + 1, top + 1, windowWidth - 2, windowHeight - 2, argb(alpha / 2, RED));
		}

		int cx = width / 2;
		int cy = (top + bottom) / 2;
		graphics.enableScissor(left, top, right, bottom);
		reticle(graphics, font, tank, cx, cy, windowWidth, windowHeight, height, ink);
		gunDemand(graphics, player, partial, cx, cy, windowWidth, windowHeight, ink);
		graphics.disableScissor();

		// Magnification and mode, top left in the window; range, bottom middle.
		graphics.text(font, String.format(Locale.ROOT, "%.0fx", TankClient.zoom()), left + 4, top + 4, argb(0xFF, ink));
		graphics.text(font, Component.translatable(thermal ? "hud.mymod.tank.thermal" : "hud.mymod.tank.day"), left + 4, top + 14,
				argb(0xC0, ink));
		double range = TankClient.lasedRange();
		String rangeText = range > 0.0 ? String.format(Locale.ROOT, "%04d", Math.min(9999, Math.round(range))) : "----";
		boolean flash = TankClient.sinceLase() < 12 && TankClient.sinceLase() / 3 % 2 == 0;
		graphics.centeredText(font, Component.translatable("hud.mymod.tank.range", rangeText), cx, bottom - 12,
				flash ? argb(0xFF, AMBER) : argb(0xFF, ink));
		if (range > 0.0) {
			graphics.text(font, Component.translatable(tank.laidFor().shortNameKey()), right - 4 - font.width(
					Component.translatable(tank.laidFor().shortNameKey())), bottom - 12, argb(0xC0, ink));
		}
		if (upFlash > 0) {
			graphics.centeredText(font, Component.translatable("hud.mymod.tank.up"), cx, cy + 26, argb(0xFF, GREEN));
		}

		compass(graphics, font, tank, partial, cx, top);
		azimuth(graphics, tank, partial, right + Math.min(28, (width - right) / 2), Math.max(22, top / 2 + 4));
		panels(graphics, font, tank, left, right, bottom, height);
		exitHold(graphics, font, cx, cy);
	}

	/** Darkens the edges of the window, as through glass with an eyepiece. */
	private static void vignette(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom) {
		int band = Math.max(8, (bottom - top) / 10);
		for (int i = 0; i < band; i++) {
			int alpha = 0x90 * (band - i) / band;
			int color = argb(alpha * alpha / 0x90, 0);
			graphics.fill(left + i, top + i, right - i, top + i + 1, color);
			graphics.fill(left + i, bottom - i - 1, right - i, bottom - i, color);
			graphics.fill(left + i, top + i + 1, left + i + 1, bottom - i - 1, color);
			graphics.fill(right - i - 1, top + i + 1, right - i, bottom - i - 1, color);
		}
	}

	/** Pixels on screen that an angle off the middle of the view covers, at the current magnification. */
	private static float pixels(float degrees, int screenHeight) {
		float fov = Minecraft.getInstance().options.fov().get() / TankClient.zoom();
		return (float) (screenHeight / 2.0 * Math.tan(Math.toRadians(degrees)) / Math.tan(Math.toRadians(fov / 2.0)));
	}

	/** The crosshair with its gap and mil ticks, the aiming dot, and the range ladder under it. */
	private static void reticle(GuiGraphicsExtractor graphics, Font font, Tank tank, int cx, int cy, int windowWidth, int windowHeight,
			int screenHeight, int ink) {
		int line = argb(0xE0, ink);
		int gap = 6;
		int reachX = windowWidth / 2 - 14;
		int reachY = windowHeight / 2 - 18;
		graphics.fill(cx - reachX, cy, cx - gap, cy + 1, line);
		graphics.fill(cx + gap + 1, cy, cx + reachX, cy + 1, line);
		graphics.fill(cx, cy - reachY, cx + 1, cy - gap, line);
		graphics.fill(cx, cy + gap + 1, cx + 1, cy + reachY / 2, line);
		graphics.fill(cx - 1, cy - 1, cx + 2, cy + 2, argb(0xFF, ink));
		// Mil ticks along the horizontal: one every 2 mils (0.1125 degrees), longer every 10.
		float mil = pixels(0.05625f, screenHeight);
		if (mil * 2 >= 3.0f) {
			for (int i = 2; i * mil < reachX; i += 2) {
				int length = i % 10 == 0 ? 4 : 2;
				int dx = Math.round(i * mil);
				graphics.fill(cx + dx, cy - length, cx + dx + 1, cy, line);
				graphics.fill(cx - dx, cy - length, cx - dx + 1, cy, line);
			}
		}
		// The range ladder: where on the target to put each mark, past the range the gun is laid for.
		ShellType round = tank.laidFor();
		float laid = tank.superelevationNow();
		for (int range : LADDER) {
			float below = round.elevationFor(range) - laid;
			if (below <= 0.0f) {
				continue;
			}
			int dy = Math.round(pixels(below, screenHeight));
			if (dy < 8 || dy > reachY) {
				continue;
			}
			graphics.fill(cx - 5, cy + dy, cx - 1, cy + dy + 1, line);
			graphics.text(font, String.valueOf(range / 100), cx - 8 - font.width(String.valueOf(range / 100)), cy + dy - 4, argb(0xB0, ink),
					false);
		}
	}

	/**
	 * A ring where the gunner is asking the gun to go (where the mouse has put their view). The sight follows the gun,
	 * so the ring runs ahead of the reticle while the turret slews and sits on it once the gun has caught up. Off the
	 * window (or behind), it waits at the window's edge on that side.
	 */
	private static void gunDemand(GuiGraphicsExtractor graphics, LocalPlayer player, float partial, int cx, int cy, int windowWidth,
			int windowHeight, int ink) {
		Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
		Vec3 wanted = Vec3.directionFromRotation(player.getViewXRot(partial), player.getViewYRot(partial));
		Vector2f at = NeuralSenses.project(graphics, camera.position().add(wanted.scale(400.0)));
		int halfWidth = windowWidth / 2 - 8;
		int halfHeight = windowHeight / 2 - 8;
		boolean inside = at != null && Math.abs(at.x - cx) <= halfWidth && Math.abs(at.y - cy) <= halfHeight;
		float x;
		float y;
		if (inside) {
			x = at.x;
			y = at.y;
		} else {
			float dx = Mth.wrapDegrees(player.getViewYRot(partial) - camera.yRot());
			float dy = player.getViewXRot(partial) - camera.xRot();
			float scale = Math.min(halfWidth / Math.max(1.0e-3f, Math.abs(dx)), halfHeight / Math.max(1.0e-3f, Math.abs(dy)));
			x = cx + dx * scale;
			y = cy + dy * scale;
		}
		if (inside && Math.abs(x - cx) < 1.5f && Math.abs(y - cy) < 1.5f) {
			return;
		}
		NeuralStyle.ring(graphics, x, y, 5.0f, 0.0f, 1.0f, argb(inside ? 0xD0 : 0x80, ink));
		NeuralStyle.ring(graphics, x, y, 6.0f, 0.0f, 1.0f, argb(0x50, ink));
	}

	private record Vec2(float x, float y) {
	}

	private static @Nullable Vec2 fallOfShotOnScreen(GuiGraphicsExtractor graphics, Tank tank, float partial) {
		Vec3 eye = Minecraft.getInstance().gameRenderer.mainCamera().position();
		Vec3 aim = Vec3.directionFromRotation(tank.gunPitch(partial) + tank.superelevationNow(), tank.turretYaw(partial));
		Vector2f at = NeuralSenses.project(graphics, eye.add(aim.scale(400.0)));
		return at == null ? null : new Vec2(at.x, at.y);
	}

	/** A strip of headings centred on where the turret points. */
	private static void compass(GuiGraphicsExtractor graphics, Font font, Tank tank, float partial, int cx, int top) {
		float heading = Mth.positiveModulo(tank.turretYaw(partial) + 180.0f, 360.0f);
		int half = 90;
		int y = Math.max(4, top - 22);
		graphics.fill(cx - half, y + 10, cx + half, y + 11, argb(0x80, GREEN));
		for (int degrees = 0; degrees < 360; degrees += 15) {
			float off = Mth.wrapDegrees(degrees - heading);
			if (Math.abs(off) > 45.0f) {
				continue;
			}
			int x = cx + Math.round(off * half / 45.0f);
			boolean major = degrees % 45 == 0;
			graphics.fill(x, y + (major ? 6 : 8), x + 1, y + 10, argb(0xC0, GREEN));
			if (major) {
				String label = BEARINGS[degrees / 45];
				graphics.text(font, label, x - font.width(label) / 2, y - 2, argb(0xFF, GREEN), false);
			}
		}
		graphics.fill(cx, y + 10, cx + 1, y + 15, argb(0xFF, AMBER));
		String bearing = String.format(Locale.ROOT, "%03d", Math.round(heading) % 360);
		graphics.text(font, bearing, cx - font.width(bearing) / 2, y + 15, argb(0xFF, AMBER), false);
	}

	/** Top down: the turret with its gun pointing up, the hull outline turned the way it lies under it. */
	private static void azimuth(GuiGraphicsExtractor graphics, Tank tank, float partial, int cx, int cy) {
		float hullOff = Mth.wrapDegrees(tank.getViewYRot(partial) - tank.turretYaw(partial));
		NeuralStyle.ring(graphics, cx, cy, 17.0f, 0.0f, 1.0f, argb(0x50, GREEN));
		graphics.pose().pushMatrix();
		graphics.pose().translate(cx, cy);
		graphics.pose().rotate(hullOff * Mth.DEG_TO_RAD);
		// The hull: tracks either side, engine deck at the back (down).
		int tracks = condition(tank.partCondition(TankPart.TRACKS));
		int engine = condition(tank.partCondition(TankPart.ENGINE));
		graphics.fill(-7, -12, -5, 12, argb(0xFF, tracks));
		graphics.fill(5, -12, 7, 12, argb(0xFF, tracks));
		graphics.outline(-5, -12, 10, 24, argb(0xC0, GREEN));
		graphics.fill(-4, 7, 4, 11, argb(0xC0, engine));
		graphics.fill(-1, -12, 1, -10, argb(0xFF, GREEN));
		graphics.pose().popMatrix();
		// The turret, always up.
		int ring = condition(tank.partCondition(TankPart.TURRET_RING));
		int gun = condition(tank.partCondition(TankPart.GUN));
		graphics.outline(cx - 4, cy - 3, 8, 8, argb(0xFF, ring));
		graphics.fill(cx, cy - 15, cx + 1, cy - 3, argb(0xFF, gun));
	}

	private static int condition(float condition) {
		return condition > 0.66f ? GREEN : condition > 0.33f ? AMBER : RED;
	}

	/** The readouts under the window: breech and ammo, driving, condition and the rest. */
	private static void panels(GuiGraphicsExtractor graphics, Font font, Tank tank, int left, int right, int bottom, int height) {
		int y0 = bottom + 4;
		int y1 = height - 4;
		int third = (right - left) / 3;
		int a = left;
		int b = left + third;
		int c = left + third * 2;
		for (int x : new int[] {a, b, c}) {
			graphics.fill(x, y0, x + third - 3, y1, PANEL);
			graphics.outline(x, y0, third - 3, y1 - y0, argb(0x40, GREEN));
		}
		int line = 10;

		// Gun: the breech, the next round, the rack.
		int y = y0 + 4;
		int breech = tank.breechState();
		Component state = switch (breech) {
			case TankGunnery.BREECH_LOADED -> Component.translatable("hud.mymod.tank.breech_loaded",
					Component.translatable(tank.breechRound().shortNameKey()));
			case TankGunnery.BREECH_LOADING -> Component.translatable("hud.mymod.tank.breech_loading",
					Component.translatable(tank.breechRound().shortNameKey()));
			default -> Component.translatable("hud.mymod.tank.breech_empty");
		};
		int stateColor = breech == TankGunnery.BREECH_LOADED ? GREEN : breech == TankGunnery.BREECH_LOADING ? AMBER : RED;
		graphics.text(font, state, a + 4, y, argb(0xFF, stateColor));
		y += line;
		if (breech == TankGunnery.BREECH_LOADING) {
			bar(graphics, a + 4, y, third - 11, tank.loadProgress(), AMBER);
			y += 5;
		} else if (breech == TankGunnery.BREECH_EMPTY && y + line <= y1) {
			graphics.text(font, Component.translatable("hud.mymod.tank.press_reload", TankClient.RELOAD_KEY.getTranslatedKeyMessage()),
					a + 4, y, argb(0xFF, AMBER));
			y += line;
		}
		if (y + line <= y1) {
			graphics.text(font, Component.translatable("hud.mymod.tank.next_round", Component.translatable(tank.nextRound().shortNameKey())),
					a + 4, y, TEXT);
			y += line;
		}
		if (y + line <= y1) {
			int apfsds = tank.rackCount(ShellType.APFSDS);
			int he = tank.rackCount(ShellType.HE);
			graphics.text(font, Component.translatable("hud.mymod.tank.rack", apfsds, he), a + 4, y, apfsds + he == 0 ? argb(0xFF, RED) : DIM);
		}

		// Driving: speed, gear and throttle.
		y = y0 + 4;
		double speed = tank.position().subtract(tank.xo, tank.yo, tank.zo).horizontalDistance() * 20.0 * 3.6;
		graphics.text(font, Component.translatable("hud.mymod.tank.speed", Math.round(speed)), b + 4, y, TEXT);
		y += line;
		float throttle = tank.throttle();
		String gear = Math.abs(throttle) < 0.02f ? "N" : throttle < 0.0f ? "R" : "F" + Mth.clamp((int) (throttle * 4.0f) + 1, 1, 4);
		graphics.text(font, Component.translatable("hud.mymod.tank.gear", gear), b + 4, y, argb(0xFF, throttle < 0.0f ? AMBER : GREEN));
		y += line;
		if (y + 4 <= y1) {
			int barWidth = third - 11;
			int zero = b + 4 + Math.round(barWidth * 0.31f);
			graphics.fill(b + 4, y, b + 4 + barWidth, y + 3, argb(0x60, 0));
			int to = zero + Math.round(throttle * (throttle < 0.0f ? barWidth * 0.31f / 0.45f : barWidth * 0.69f));
			graphics.fill(Math.min(zero, to), y, Math.max(zero, to), y + 3, argb(0xFF, throttle < 0.0f ? AMBER : GREEN));
			graphics.fill(zero, y - 1, zero + 1, y + 4, argb(0xFF, TEXT));
			y += 6;
		}
		if (y + line <= y1) {
			graphics.text(font, Component.translatable("hud.mymod.tank.heading", String.format(Locale.ROOT, "%03d",
					Math.round(Mth.positiveModulo(tank.getYRot() + 180.0f, 360.0f)) % 360)), b + 4, y, DIM);
		}

		// Condition: hull, the parts, smoke and the coax.
		y = y0 + 4;
		float hull = tank.getHealth() / tank.getMaxHealth();
		graphics.text(font, Component.translatable("hud.mymod.tank.hull", Math.round(hull * 100.0f)), c + 4, y, argb(0xFF, condition(hull)));
		y += line;
		int x = c + 4;
		for (TankPart part : TankPart.values()) {
			Component name = Component.translatable(part.nameKey());
			graphics.text(font, name, x, y, argb(0xFF, condition(tank.partCondition(part))));
			x += font.width(name) + 4;
		}
		y += line;
		if (y + line <= y1) {
			StringBuilder salvos = new StringBuilder();
			for (int i = 0; i < TankGunnery.MAX_SMOKE; i++) {
				salvos.append(i < tank.smokeSalvos() ? '■' : '□');
			}
			graphics.text(font, Component.translatable("hud.mymod.tank.smoke", salvos.toString()), c + 4, y,
					argb(0xFF, tank.smokeSalvos() > 0 ? GREEN : RED));
			y += line;
		}
		if (y + 4 <= y1) {
			int labelWidth = font.width(Component.translatable("hud.mymod.tank.coax"));
			graphics.text(font, Component.translatable("hud.mymod.tank.coax"), c + 4, y - 2, tank.coaxJammed() ? argb(0xFF, RED) : DIM);
			bar(graphics, c + 8 + labelWidth, y, third - 15 - labelWidth, tank.coaxHeat(), tank.coaxJammed() ? RED : tank.coaxHeat() > 0.7f ? AMBER : GREEN);
		}
	}

	private static void bar(GuiGraphicsExtractor graphics, int x, int y, int width, float fill, int color) {
		graphics.fill(x, y, x + width, y + 3, argb(0x60, 0));
		graphics.fill(x, y, x + Math.round(width * Mth.clamp(fill, 0.0f, 1.0f)), y + 3, argb(0xFF, color));
	}

	private static void exitHold(GuiGraphicsExtractor graphics, Font font, int cx, int cy) {
		float progress = TankClient.exitProgress();
		if (progress <= 0.0f) {
			return;
		}
		graphics.centeredText(font, Component.translatable("hud.mymod.tank.climbing_out"), cx, cy + 40, argb(0xFF, AMBER));
		bar(graphics, cx - 30, cy + 51, 60, progress, AMBER);
	}

	// --- From outside ---------------------------------------------------------------------------------------

	/** The external view: one compact panel bottom left, and where the gun will put its round. */
	private static void external(GuiGraphicsExtractor graphics, Minecraft minecraft, Tank tank, float partial) {
		Font font = minecraft.font;
		Vec2 at = fallOfShotOnScreen(graphics, tank, partial);
		if (at != null) {
			NeuralStyle.ring(graphics, at.x, at.y, 4.0f, 0.0f, 1.0f, argb(0xD0, GREEN));
			graphics.fill(Math.round(at.x), Math.round(at.y), Math.round(at.x) + 1, Math.round(at.y) + 1, argb(0xFF, GREEN));
		}
		int x0 = 6;
		int y1 = graphics.guiHeight() - 6;
		int y0 = y1 - 56;
		int x1 = x0 + 150;
		graphics.fill(x0, y0, x1, y1, PANEL);
		graphics.outline(x0, y0, x1 - x0, y1 - y0, argb(0x50, GREEN));
		int y = y0 + 4;
		float hull = tank.getHealth() / tank.getMaxHealth();
		graphics.text(font, Component.translatable("hud.mymod.tank.hull", Math.round(hull * 100.0f)), x0 + 4, y, argb(0xFF, condition(hull)));
		double speed = tank.position().subtract(tank.xo, tank.yo, tank.zo).horizontalDistance() * 20.0 * 3.6;
		Component speedText = Component.translatable("hud.mymod.tank.speed", Math.round(speed));
		graphics.text(font, speedText, x1 - 4 - font.width(speedText), y, TEXT);
		y += 10;
		int breech = tank.breechState();
		Component state = breech == TankGunnery.BREECH_LOADED
				? Component.translatable("hud.mymod.tank.breech_loaded", Component.translatable(tank.breechRound().shortNameKey()))
				: breech == TankGunnery.BREECH_LOADING
						? Component.translatable("hud.mymod.tank.breech_loading", Component.translatable(tank.breechRound().shortNameKey()))
						: Component.translatable("hud.mymod.tank.breech_empty");
		graphics.text(font, state, x0 + 4, y, argb(0xFF, breech == TankGunnery.BREECH_LOADED ? GREEN : breech == TankGunnery.BREECH_LOADING
				? AMBER : RED));
		y += 10;
		if (breech == TankGunnery.BREECH_LOADING) {
			bar(graphics, x0 + 4, y - 1, x1 - x0 - 8, tank.loadProgress(), AMBER);
			y += 4;
		}
		graphics.text(font, Component.translatable("hud.mymod.tank.rack", tank.rackCount(ShellType.APFSDS), tank.rackCount(ShellType.HE)),
				x0 + 4, y, DIM);
		y += 10;
		graphics.text(font, Component.translatable("hud.mymod.tank.next_round", Component.translatable(tank.nextRound().shortNameKey())),
				x0 + 4, y, DIM);
		Component smoke = Component.translatable("hud.mymod.tank.smoke", String.valueOf(tank.smokeSalvos()));
		graphics.text(font, smoke, x1 - 4 - font.width(smoke), y, DIM);
		float exit = TankClient.exitProgress();
		if (exit > 0.0f) {
			int cx = graphics.guiWidth() / 2;
			int cy = graphics.guiHeight() / 2;
			graphics.centeredText(font, Component.translatable("hud.mymod.tank.climbing_out"), cx, cy + 30, argb(0xFF, AMBER));
			bar(graphics, cx - 30, cy + 41, 60, exit, AMBER);
		}
		if (upFlash > 0) {
			graphics.centeredText(font, Component.translatable("hud.mymod.tank.up"), graphics.guiWidth() / 2, graphics.guiHeight() / 2 + 20,
					argb(0xFF, GREEN));
		}
	}
}
