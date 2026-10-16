package com.jheinz.tomyen.client.powerarmor;

import java.util.Locale;

import org.joml.Vector2f;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.client.neuralink.NeuralSenses;
import com.jheinz.tomyen.client.neuralink.NeuralStyle;
import com.jheinz.tomyen.minigun.MinigunItem;
import com.jheinz.tomyen.minigun.MinigunState;
import com.jheinz.tomyen.minigun.RotaryGun;
import com.jheinz.tomyen.neuralink.NeuralinkItem;
import com.jheinz.tomyen.neuralink.NeuralinkModule;
import com.jheinz.tomyen.powerarmor.FusionCoreItem;
import com.jheinz.tomyen.powerarmor.PowerArmor;
import com.jheinz.tomyen.powerarmor.PowerArmorMotion;
import com.jheinz.tomyen.powerarmor.PowerArmorNetworking;
import com.jheinz.tomyen.powerarmor.PowerArmorPiece;
import com.jheinz.tomyen.powerarmor.PowerArmorStats;
import com.jheinz.tomyen.powerarmor.PowerArmorSuit;
import com.jheinz.tomyen.powerarmor.PowerArmorTier;
import com.jheinz.tomyen.powerarmor.Repulsors;

import static com.jheinz.tomyen.client.neuralink.NeuralStyle.argb;
import static com.jheinz.tomyen.client.neuralink.NeuralStyle.brackets;

/**
 * The view from inside power armor: a Pip-Boy amber helmet HUD. It replaces the vanilla armor
 * bar, and fits round the Neuralink's panels (the helmet is a Neuralink): its Neural Heat panel keeps the
 * bottom-left corner, its Bio-Monitor the top-left, and this keeps clear of both.
 * <ul>
 * <li>Under everything ({@link #extractVisor}): the helmet's frame round the screen, the visor sealing shut and
 * the systems booting as the wearer climbs in (and the seals letting go as they climb out), and a red pulse at
 * the edges when the core is low or a piece is nearly broken.
 * <li>A compass strip across the top, with altitude and vertical speed under it and any warnings under those.
 * <li>The power panel top right (under any effect icons): fusion core, thrusters and their heat, the repulsor
 * and unibeam, a held Minigun's rounds and heat, and the helmet's air underwater.
 * <li>The suit's silhouette on the left, each piece coloured by condition and outlined in its mark's colour.
 * <li>Round the crosshair: repulsor and unibeam readiness, the unibeam charging, the exit hold, and while flying,
 * airspeed over a throttle gauge (hover, cruise, boost) and height above the ground.
 * <li>A Mk II helmet reads out the health of whatever's in the sights; a Mk III adds a threat radar.
 * </ul>
 */
public final class PowerArmorHud {
	private static final int AMBER = 0xFFB000;
	private static final int TEXT = 0xFFFFE9B8;
	private static final int DIM = 0xFFA67C1C;
	private static final int DARK = 0xFF5A4410;
	private static final int GREEN = 0x5CFF7A;
	private static final int RED = 0xFF3B30;
	private static final int ARC = 0x7FE7FF;
	private static final int GLASS = 0xB0140F06;
	private static final int PANEL_WIDTH = 118;
	private static final int BODY_WIDTH = 96;
	private static final int BODY_HEIGHT = 84;
	private static final float WARN_FUEL = 0.15f;
	private static final float WARN_CONDITION = 0.15f;
	private static final double TARGET_RANGE = 48.0;
	private static final double RADAR_RANGE = 32.0;
	private static final int RADAR_RADIUS = 30;
	private static final String[] BEARINGS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

	private PowerArmorHud() {
	}

	static void initialize() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, MyMod.id("power_armor_visor"), PowerArmorHud::extractVisor);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, MyMod.id("power_armor"), PowerArmorHud::extract);
		HudElementRegistry.replaceElement(VanillaHudElements.ARMOR_BAR, vanilla -> (graphics, delta) -> {
			LocalPlayer player = Minecraft.getInstance().player;
			if (player == null || !PowerArmor.isWearing(player)) {
				vanilla.extractRenderState(graphics, delta);
			}
		});
	}

	private static float fuel(PowerArmorSuit suit) {
		PowerArmorNetworking.Status status = PowerArmorClient.status;
		int charge = status != null ? status.fuel() : suit.charge();
		return Mth.clamp(charge / (float) FusionCoreItem.MAX_CHARGE, 0.0f, 1.0f);
	}

	private static boolean pieceCritical(PowerArmorSuit suit) {
		for (PowerArmorPiece piece : PowerArmorPiece.values()) {
			if (suit.condition(piece) < WARN_CONDITION) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------------------------------------ the visor

	public static void extractVisor(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		PowerArmorSuit suit = player == null ? null : PowerArmor.worn(player);
		if (suit == null || !minecraft.options.getCameraType().isFirstPerson()) {
			return;
		}
		int width = graphics.guiWidth(), height = graphics.guiHeight();
		long now = Util.getMillis();
		PowerArmorMotion motion = PowerArmor.motion(player);
		float partial = delta.getGameTimeDeltaPartialTick(false);
		float sequence = motion.inSequence() ? (player.level().getGameTime() - motion.sequenceStart() + partial)
				/ (motion.sequence() == PowerArmorMotion.ENTERING ? PowerArmor.ENTER_TICKS : PowerArmor.EXIT_TICKS) : 1.0f;
		float frameAlpha = motion.sequence() == PowerArmorMotion.EXITING ? 1.0f - Mth.clamp(sequence * 1.5f, 0.0f, 1.0f)
				: motion.sequence() == PowerArmorMotion.ENTERING ? Mth.clamp((sequence - 0.3f) * 3.0f, 0.0f, 1.0f) : 1.0f;
		PowerArmorTier helmet = suit.tier(PowerArmorPiece.HELMET);
		frame(graphics, width, height, frameAlpha, helmet == null ? AMBER : helmet.color());

		if (!motion.inSequence() && (fuel(suit) < WARN_FUEL || pieceCritical(suit))) {
			int pulse = (int) (0x50 * NeuralStyle.wave(now, 900.0f, 0.0f));
			for (int i = 0; i < 12; i++) {
				int alpha = pulse * (12 - i) / 12;
				graphics.fill(i, 0, i + 1, height, argb(alpha, RED));
				graphics.fill(width - i - 1, 0, width - i, height, argb(alpha, RED));
			}
		}

		if (motion.sequence() == PowerArmorMotion.ENTERING) {
			// The faceplate swings down over the view, then the visor lights and the systems boot.
			float close = smooth((sequence - 0.35f) / 0.3f);
			float reveal = smooth((sequence - 0.75f) / 0.2f);
			int shutter = Math.round(height / 2.0f * close * (1.0f - reveal));
			if (shutter > 0) {
				graphics.fill(0, 0, width, shutter, 0xFF0C0A07);
				graphics.fill(0, height - shutter, width, height, 0xFF0C0A07);
				graphics.horizontalLine(0, width, shutter - 1, argb(0xFF, DARK));
				graphics.horizontalLine(0, width, height - shutter, argb(0xFF, DARK));
			}
			if (sequence > 0.6f) {
				boot(graphics, minecraft.font, width, height, (sequence - 0.6f) / 0.4f, suit);
			}
		} else if (motion.sequence() == PowerArmorMotion.EXITING) {
			String text = Component.translatable("hud.tomyen.power_armor.disengaging").getString();
			graphics.centeredText(minecraft.font, text, width / 2, height / 2 - 30, argb(now % 400 < 200 ? 0xFF : 0x80, AMBER));
		}
	}

	private static float smooth(float t) {
		float x = Mth.clamp(t, 0.0f, 1.0f);
		return x * x * (3.0f - 2.0f * x);
	}

	/** The helmet's edges: dark bands top and bottom, a soft vignette at the sides and angled corner plates. */
	private static void frame(GuiGraphicsExtractor graphics, int width, int height, float alpha, int accent) {
		if (alpha <= 0.0f) {
			return;
		}
		int dark = (int) (0xC0 * alpha);
		graphics.fillGradient(0, 0, width, 16, argb(dark, 0x0A0805), argb(0, 0x0A0805));
		graphics.fillGradient(0, height - 14, width, height, argb(0, 0x0A0805), argb(dark, 0x0A0805));
		for (int i = 0; i < 16; i++) {
			int a = (int) (0x70 * alpha * (16 - i) / 16.0f);
			graphics.fill(i, 0, i + 1, height, argb(a, 0x0A0805));
			graphics.fill(width - i - 1, 0, width - i, height, argb(a, 0x0A0805));
		}
		// Corner plates, stepped at 45 degrees, with a lit edge in the helmet's mark colour.
		int size = 26;
		int plate = argb((int) (0xE0 * alpha), 0x14100A);
		int edge = argb((int) (0x90 * alpha), accent);
		for (int i = 0; i < size; i++) {
			int run = size - i;
			graphics.fill(0, i, run, i + 1, plate);
			graphics.fill(width - run, i, width, i + 1, plate);
			graphics.fill(0, height - i - 1, run, height - i, plate);
			graphics.fill(width - run, height - i - 1, width, height - i, plate);
			graphics.fill(run, i, run + 1, i + 1, edge);
			graphics.fill(width - run - 1, i, width - run, i + 1, edge);
			graphics.fill(run, height - i - 1, run + 1, height - i, edge);
			graphics.fill(width - run - 1, height - i - 1, width - run, height - i, edge);
		}
		int bolt = argb((int) (0xC0 * alpha), 0x6A5A3A);
		graphics.fill(5, 5, 7, 7, bolt);
		graphics.fill(width - 7, 5, width - 5, 7, bolt);
		graphics.fill(5, height - 7, 7, height - 5, bolt);
		graphics.fill(width - 7, height - 7, width - 5, height - 5, bolt);
	}

	/** The boot-up readout as the visor seals, typed out line by line. */
	private static void boot(GuiGraphicsExtractor graphics, Font font, int width, int height, float progress, PowerArmorSuit suit) {
		String[] lines = {
				Component.translatable("hud.tomyen.power_armor.boot.0").getString(),
				Component.translatable("hud.tomyen.power_armor.boot.1", Math.round(fuel(suit) * 100.0f)).getString(),
				Component.translatable("hud.tomyen.power_armor.boot.2").getString(),
				Component.translatable("hud.tomyen.power_armor.boot.3").getString()};
		int total = 0;
		for (String line : lines) {
			total += line.length();
		}
		int shown = Math.round(total * Mth.clamp(progress * 1.3f, 0.0f, 1.0f));
		int y = height / 2 - 24;
		float fade = progress > 0.85f ? 1.0f - (progress - 0.85f) / 0.15f : 1.0f;
		for (String line : lines) {
			String visible = line.substring(0, Math.min(line.length(), Math.max(0, shown)));
			shown -= line.length();
			graphics.text(font, visible, width / 2 - 80, y, argb((int) (0xFF * fade), AMBER), true);
			y += 11;
		}
	}

	// ------------------------------------------------------------------------------------------------ the readouts

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		PowerArmorSuit suit = player == null ? null : PowerArmor.worn(player);
		if (suit == null || PowerArmor.motion(player).inSequence()) {
			return;
		}
		Font font = minecraft.font;
		PowerArmorStats stats = suit.stats();
		float partial = delta.getGameTimeDeltaPartialTick(false);
		compass(graphics, font, player, partial);
		warnings(graphics, font, suit);
		powerPanel(graphics, font, player, suit, stats);
		silhouette(graphics, font, player, suit);
		crosshair(graphics, font, player, stats);
		if (stats.hudLevel() >= 1) {
			target(graphics, font, minecraft, player, partial);
		}
		if (stats.hudLevel() >= 2) {
			radar(graphics, font, minecraft, player);
		}
	}

	private static void compass(GuiGraphicsExtractor graphics, Font font, LocalPlayer player, float partial) {
		int cx = graphics.guiWidth() / 2, y0 = 3, half = 92;
		graphics.fill(cx - half, y0, cx + half, y0 + 14, GLASS);
		graphics.horizontalLine(cx - half, cx + half - 1, y0 + 14, argb(0xC0, AMBER));
		brackets(graphics, cx - half, y0, cx + half, y0 + 15, 4, 1, argb(0xFF, AMBER));
		// Bearing: 0 north, clockwise. A yaw of 0 faces south.
		float bearing = Mth.positiveModulo(player.getViewYRot(partial) + 180.0f, 360.0f);
		float pixelsPerDegree = 1.5f;
		graphics.enableScissor(cx - half + 2, y0, cx + half - 2, y0 + 14);
		for (int degrees = 0; degrees < 360; degrees += 15) {
			float offset = Mth.wrapDegrees(degrees - bearing);
			int x = cx + Math.round(offset * pixelsPerDegree);
			if (Math.abs(offset) > 64) {
				continue;
			}
			if (degrees % 45 == 0) {
				String label = BEARINGS[degrees / 45];
				int colour = degrees == 0 ? argb(0xFF, RED) : TEXT;
				graphics.centeredText(font, label, x, y0 + 3, colour);
			} else {
				graphics.verticalLine(x, y0 + 8, y0 + 13, DIM);
			}
		}
		graphics.disableScissor();
		graphics.fill(cx, y0 + 12, cx + 1, y0 + 16, argb(0xFF, TEXT));
		String heading = String.format(Locale.ROOT, "%03d°", Math.round(bearing) % 360);
		graphics.text(font, heading, cx + half + 4, y0 + 3, TEXT, true);

		// Altitude and vertical speed under the strip.
		double vertical = (player.getY() - player.yo) * 20.0;
		String altitude = Component.translatable("hud.tomyen.power_armor.altitude", (int) Math.floor(player.getY())).getString();
		String climb = String.format(Locale.ROOT, "V/S %+.1f m/s", vertical);
		graphics.text(font, altitude, cx - half, y0 + 18, DIM, true);
		graphics.text(font, climb, cx + half - font.width(climb), y0 + 18, Math.abs(vertical) > 15 ? argb(0xFF, RED) : DIM, true);
	}

	private static void warnings(GuiGraphicsExtractor graphics, Font font, PowerArmorSuit suit) {
		long now = Util.getMillis();
		if (now % 1000 > 650) {
			return;
		}
		int cx = graphics.guiWidth() / 2;
		int y = 34;
		float fuel = fuel(suit);
		if (fuel <= 0.0f) {
			graphics.centeredText(font, "⚠ " + Component.translatable("hud.tomyen.power_armor.core_empty").getString(), cx, y, argb(0xFF, RED));
			y += 10;
		} else if (fuel < WARN_FUEL) {
			graphics.centeredText(font, "⚠ " + Component.translatable("hud.tomyen.power_armor.core_low", Math.round(fuel * 100.0f)).getString(), cx, y,
					argb(0xFF, RED));
			y += 10;
		}
		for (PowerArmorPiece piece : PowerArmorPiece.values()) {
			float condition = suit.condition(piece);
			if (condition < WARN_CONDITION) {
				String key = condition <= 0.0f ? "hud.tomyen.power_armor.piece_broken" : "hud.tomyen.power_armor.piece_critical";
				graphics.centeredText(font, "⚠ " + Component.translatable(key, piece.displayName()).getString().toUpperCase(Locale.ROOT), cx, y,
						argb(0xFF, condition <= 0.0f ? RED : AMBER));
				y += 10;
			}
		}
	}

	/** Below the vanilla effect icons, if there are any. */
	private static int effectsHeight(LocalPlayer player) {
		boolean good = false, bad = false;
		for (MobEffectInstance effect : player.getActiveEffects()) {
			if (effect.showIcon()) {
				if (effect.getEffect().value().isBeneficial()) {
					good = true;
				} else {
					bad = true;
				}
			}
		}
		return (good ? 26 : 0) + (bad ? 26 : 0);
	}

	private static void powerPanel(GuiGraphicsExtractor graphics, Font font, LocalPlayer player, PowerArmorSuit suit, PowerArmorStats stats) {
		PowerArmorNetworking.Status status = PowerArmorClient.status;
		PowerArmorMotion motion = PowerArmor.motion(player);
		long now = Util.getMillis();
		int x1 = graphics.guiWidth() - 6, x0 = x1 - PANEL_WIDTH, y0 = 6 + effectsHeight(player);
		boolean underwater = status != null && status.airMax() > 0 && status.air() < status.airMax();
		boolean minigun = player.getMainHandItem().getItem() instanceof MinigunItem;
		int rows = 4 + (underwater ? 1 : 0) + (minigun ? 1 : 0);
		int y1 = y0 + 8 + rows * 13;
		graphics.fill(x0, y0, x1, y1, GLASS);
		brackets(graphics, x0, y0, x1, y1, 5, 1, argb(0xFF, AMBER));
		int x = x0 + 5, right = x1 - 5, y = y0 + 5;

		// Fusion core.
		float fuel = fuel(suit);
		int fuelColour = fuel <= 0.0f ? RED : fuel < WARN_FUEL ? (now % 500 < 250 ? RED : AMBER) : fuel < 0.4f ? AMBER : GREEN;
		readout(graphics, font, x, right, y, Component.translatable("hud.tomyen.power_armor.core").getString(),
				String.format(Locale.ROOT, "%.1f%%", fuel * 100.0f), fuelColour, fuel);
		y += 13;

		// Thrusters.
		boolean overheated = status != null && status.overheated();
		String state = overheated ? "hud.tomyen.power_armor.thrusters.overheat" : switch (motion.thrusters()) {
			case PowerArmorMotion.HOVER -> "hud.tomyen.power_armor.thrusters.hover";
			case PowerArmorMotion.CRUISE -> "hud.tomyen.power_armor.thrusters.cruise";
			case PowerArmorMotion.BOOST -> "hud.tomyen.power_armor.thrusters.boost";
			default -> "hud.tomyen.power_armor.thrusters.off";
		};
		float burn = status == null ? 0.0f : status.burn();
		int thrusterColour = overheated ? (now % 400 < 200 ? RED : AMBER) : motion.flying() ? (burn > 0.8f ? RED : burn > 0.5f ? AMBER : ARC) : 0xA67C1C;
		readout(graphics, font, x, right, y, Component.translatable("hud.tomyen.power_armor.thrusters").getString(),
				Component.translatable(state).getString(), thrusterColour, burn);
		y += 13;

		// Repulsors and unibeam.
		int repulsor = status == null ? 0 : status.repulsorCooldown();
		float repulsorReady = status == null || status.repulsorCooldownMax() <= 0 ? 1.0f : 1.0f - repulsor / (float) status.repulsorCooldownMax();
		boolean armsWork = stats.repulsorWorks(PowerArmorPiece.LEFT_ARM) || stats.repulsorWorks(PowerArmorPiece.RIGHT_ARM);
		readout(graphics, font, x, right, y, Component.translatable("hud.tomyen.power_armor.repulsor").getString(),
				!armsWork ? Component.translatable("hud.tomyen.power_armor.offline").getString() : weaponState(repulsor),
				!armsWork ? RED : repulsor > 0 ? AMBER : ARC, armsWork ? repulsorReady : 0.0f);
		y += 13;
		int unibeam = status == null ? 0 : status.unibeamCooldown();
		boolean charging = status != null && status.unibeamCharge() >= 0;
		String uniState = !stats.unibeamWorks() ? Component.translatable("hud.tomyen.power_armor.offline").getString()
				: charging ? Component.translatable("hud.tomyen.power_armor.charging").getString() : weaponState(unibeam);
		float uniBar = charging ? status.unibeamCharge() / (float) Repulsors.UNIBEAM_CHARGE_TICKS : 1.0f - unibeam / (float) Repulsors.UNIBEAM_COOLDOWN;
		readout(graphics, font, x, right, y, Component.translatable("hud.tomyen.power_armor.unibeam").getString(), uniState,
				!stats.unibeamWorks() ? RED : charging ? 0xFFFFFF : unibeam > 0 ? AMBER : ARC, stats.unibeamWorks() ? uniBar : 0.0f);
		y += 13;

		// A Minigun in hand: the 5.56 rounds carried for it, over its heat.
		if (minigun) {
			MinigunState gun = MinigunItem.state(player.getMainHandItem());
			String value = gun.overheated() ? Component.translatable("hud.tomyen.minigun.overheated").getString()
					: Component.translatable("hud.tomyen.minigun.rounds", RotaryGun.carried(player)).getString();
			int colour = gun.overheated() ? (now % 400 < 200 ? RED : AMBER) : gun.heat() > 0.6f ? AMBER : ARC;
			readout(graphics, font, x, right, y, Component.translatable("hud.tomyen.power_armor.minigun").getString(), value, colour, gun.heat());
			y += 13;
		}

		if (underwater) {
			float air = status.air() / (float) status.airMax();
			readout(graphics, font, x, right, y, "O2", String.format(Locale.ROOT, "%ds", Mth.ceil(status.air() / 20.0f)),
					air < 0.2f ? RED : 0x7FD8FF, air);
		}
	}

	private static String weaponState(int cooldown) {
		return cooldown > 0 ? String.format(Locale.ROOT, "%.1fs", cooldown / 20.0f) : Component.translatable("hud.tomyen.power_armor.ready").getString();
	}

	/** A label, a value on the right and a thin bar under them. */
	private static void readout(GuiGraphicsExtractor graphics, Font font, int x0, int x1, int y, String label, String value, int colour, float fraction) {
		graphics.text(font, label, x0, y, DIM, false);
		graphics.text(font, value, x1 - font.width(value), y, argb(0xFF, colour), false);
		graphics.fill(x0, y + 9, x1, y + 10, argb(0x40, AMBER));
		graphics.fill(x0, y + 9, x0 + Math.round((x1 - x0) * Mth.clamp(fraction, 0.0f, 1.0f)), y + 10, argb(0xFF, colour));
	}

	/** The suit as a little figure, each piece coloured by condition and outlined in its mark's colour. */
	private static void silhouette(GuiGraphicsExtractor graphics, Font font, LocalPlayer player, PowerArmorSuit suit) {
		int height = graphics.guiHeight();
		// Clear of the Bio-Monitor above and the Neural Heat panel below.
		boolean bioMonitor = NeuralinkItem.isActive(player, NeuralinkModule.BIO_MONITOR);
		int top = bioMonitor ? 100 : 30;
		int bottom = height - (NeuralinkItem.installedModules(player).stream().anyMatch(NeuralinkModule::isActive) ? 56 : 8) - BODY_HEIGHT;
		int y0 = Math.max(top, Math.min(bottom, height / 2 - BODY_HEIGHT / 2));
		int x0 = 6, x1 = x0 + BODY_WIDTH, y1 = y0 + BODY_HEIGHT;
		graphics.fill(x0, y0, x1, y1, GLASS);
		brackets(graphics, x0, y0, x1, y1, 5, 1, argb(0xFF, AMBER));
		graphics.text(font, Component.translatable("hud.tomyen.power_armor.armor").getString(), x0 + 4, y0 + 4, DIM, false);

		int cx = x0 + 18, by = y0 + 16;
		part(graphics, suit, PowerArmorPiece.HELMET, cx - 5, by, cx + 5, by + 9);
		part(graphics, suit, PowerArmorPiece.TORSO, cx - 7, by + 10, cx + 7, by + 32);
		// Facing the viewer, as in the station: the suit's right arm on the left.
		part(graphics, suit, PowerArmorPiece.RIGHT_ARM, cx - 13, by + 10, cx - 8, by + 31);
		part(graphics, suit, PowerArmorPiece.LEFT_ARM, cx + 8, by + 10, cx + 13, by + 31);
		part(graphics, suit, PowerArmorPiece.RIGHT_LEG, cx - 7, by + 33, cx - 1, by + 60);
		part(graphics, suit, PowerArmorPiece.LEFT_LEG, cx + 1, by + 33, cx + 7, by + 60);

		int ty = y0 + 14;
		for (PowerArmorPiece piece : PowerArmorPiece.values()) {
			PowerArmorTier tier = suit.tier(piece);
			float condition = suit.condition(piece);
			int tx = x0 + 36;
			graphics.text(font, piece.shortName().getString(), tx, ty, DIM, false);
			if (tier != null) {
				graphics.text(font, tier.numeral(), tx + 25, ty, argb(0xFF, tier.color()), false);
			}
			String percent = condition <= 0.0f ? "--" : Math.round(condition * 100.0f) + "";
			graphics.text(font, percent, x1 - 4 - font.width(percent), ty, argb(0xFF, conditionColour(condition)), false);
			ty += 11;
		}
	}

	private static void part(GuiGraphicsExtractor graphics, PowerArmorSuit suit, PowerArmorPiece piece, int x0, int y0, int x1, int y1) {
		float condition = suit.condition(piece);
		PowerArmorTier tier = suit.tier(piece);
		boolean blink = condition < WARN_CONDITION && Util.getMillis() % 600 < 300;
		graphics.fill(x0, y0, x1, y1, argb(blink ? 0x40 : 0xC0, conditionColour(condition)));
		graphics.outline(x0, y0, x1 - x0, y1 - y0, argb(0xFF, tier == null ? DARK : tier.color()));
	}

	private static int conditionColour(float condition) {
		return condition <= 0.0f ? 0x6A1A14 : condition < 0.25f ? RED : condition < 0.6f ? AMBER : GREEN;
	}

	private static void crosshair(GuiGraphicsExtractor graphics, Font font, LocalPlayer player, PowerArmorStats stats) {
		PowerArmorNetworking.Status status = PowerArmorClient.status;
		int cx = graphics.guiWidth() / 2, cy = graphics.guiHeight() / 2;
		// Repulsor readiness on the left arc, the unibeam on the right.
		float repulsor = status == null || status.repulsorCooldownMax() <= 0 ? 1.0f : 1.0f - status.repulsorCooldown() / (float) status.repulsorCooldownMax();
		float unibeam = status == null ? 1.0f : 1.0f - status.unibeamCooldown() / (float) Repulsors.UNIBEAM_COOLDOWN;
		NeuralStyle.ring(graphics, cx, cy, 11, 0.6f, 0.9f, argb(0x40, ARC));
		NeuralStyle.ring(graphics, cx, cy, 11, 0.9f - 0.3f * Mth.clamp(repulsor, 0.0f, 1.0f), 0.9f, argb(repulsor >= 1.0f ? 0xC0 : 0x80, ARC));
		NeuralStyle.ring(graphics, cx, cy, 11, 0.1f, 0.4f, argb(0x40, 0xFFFFFF));
		NeuralStyle.ring(graphics, cx, cy, 11, 0.1f, 0.1f + 0.3f * Mth.clamp(unibeam, 0.0f, 1.0f), argb(unibeam >= 1.0f ? 0xC0 : 0x70, 0xFFFFFF));
		if (status != null && status.unibeamCharge() >= 0) {
			float charge = status.unibeamCharge() / (float) Repulsors.UNIBEAM_CHARGE_TICKS;
			NeuralStyle.ring(graphics, cx, cy, 15, 0.0f, charge, argb(0xFF, 0xFFFFFF));
			NeuralStyle.ring(graphics, cx, cy, 16, 0.0f, charge, argb(0x80, ARC));
		}
		float exit = PowerArmorClient.exitProgress();
		if (exit > 0.0f) {
			NeuralStyle.ring(graphics, cx, cy, 19, 0.0f, exit, argb(0xFF, AMBER));
			graphics.centeredText(font, Component.translatable("hud.tomyen.power_armor.exit").getString(), cx, cy + 24, argb(0xFF, AMBER));
		}
		PowerArmorMotion motion = PowerArmor.motion(player);
		if (motion.flying()) {
			// Left of the crosshair: speed over a throttle gauge (hover, cruise, boost) scaled to this suit's top
			// boost speed. Right: height above the ground.
			double speed = player.position().subtract(player.xo, player.yo, player.zo).length();
			String readout = String.format(Locale.ROOT, "%.0f m/s", speed * 20.0);
			int right = cx - 24;
			graphics.text(font, Component.translatable("hud.tomyen.power_armor.airspeed").getString(), right - 46, cy - 14, DIM, true);
			graphics.text(font, readout, right - font.width(readout), cy - 4, argb(0xFF, ARC), true);
			int throttle = motion.thrusters() == PowerArmorMotion.BOOST ? 3 : motion.thrusters() == PowerArmorMotion.CRUISE ? 2 : 1;
			int gauge = right - 46;
			for (int step = 0; step < 3; step++) {
				int colour = step >= throttle ? argb(0x40, AMBER) : step == 2 ? argb(0xFF, RED) : argb(0xFF, step == 1 ? AMBER : ARC);
				graphics.fill(gauge + step * 9, cy + 7, gauge + step * 9 + 7, cy + 10, colour);
			}
			float top = PowerArmorStats.topSpeed(stats.boostThrust());
			graphics.fill(gauge, cy + 12, right, cy + 13, argb(0x40, ARC));
			graphics.fill(gauge, cy + 12, gauge + Math.round(46 * Mth.clamp((float) speed / Math.max(0.01f, top), 0.0f, 1.0f)), cy + 13,
					argb(0xFF, ARC));
			String mode = Component.translatable(motion.thrusters() == PowerArmorMotion.BOOST ? "hud.tomyen.power_armor.thrusters.boost"
					: motion.thrusters() == PowerArmorMotion.CRUISE ? "hud.tomyen.power_armor.thrusters.cruise" : "hud.tomyen.power_armor.thrusters.hover")
					.getString();
			graphics.text(font, mode, right - font.width(mode), cy + 15, motion.thrusters() == PowerArmorMotion.BOOST ? argb(0xFF, RED) : DIM, true);
			int ground = player.level().getHeight(Heightmap.Types.MOTION_BLOCKING, player.getBlockX(), player.getBlockZ());
			String agl = Component.translatable("hud.tomyen.power_armor.agl", Math.max(0, (int) Math.floor(player.getY() - ground))).getString();
			graphics.text(font, agl, cx + 24, cy - 4, argb(0xFF, ARC), true);
		}
	}

	/** Mk II helmets: the name and health of whatever's in the sights, out to {@link #TARGET_RANGE}. */
	private static void target(GuiGraphicsExtractor graphics, Font font, Minecraft minecraft, LocalPlayer player, float partial) {
		LivingEntity target = sighted(player, partial);
		if (target == null) {
			return;
		}
		int cx = graphics.guiWidth() / 2, cy = graphics.guiHeight() / 2;
		Vector2f at = NeuralSenses.project(graphics, target.getPosition(partial).add(0.0, target.getBbHeight() / 2.0, 0.0));
		int colour = target instanceof Enemy ? RED : target instanceof Player ? ARC : AMBER;
		if (at != null) {
			int size = (int) Mth.clamp(180.0f / (float) Math.max(1.0, target.distanceTo(player)), 8.0f, 50.0f);
			brackets(graphics, Math.round(at.x) - size, Math.round(at.y) - size, Math.round(at.x) + size, Math.round(at.y) + size, 4, 1,
					argb(0xC0, colour));
		}
		String name = target.getDisplayName().getString();
		String distance = String.format(Locale.ROOT, "%.0fm", target.distanceTo(player));
		int width = Math.max(70, font.width(name) + font.width(distance) + 14);
		int x0 = cx - width / 2, y0 = cy + 30;
		graphics.fill(x0, y0, x0 + width, y0 + 17, GLASS);
		graphics.text(font, name, x0 + 3, y0 + 2, argb(0xFF, colour), false);
		graphics.text(font, distance, x0 + width - 3 - font.width(distance), y0 + 2, DIM, false);
		float health = target.getHealth() / Math.max(1.0f, target.getMaxHealth());
		graphics.fill(x0 + 3, y0 + 12, x0 + width - 3, y0 + 14, argb(0x50, RED));
		graphics.fill(x0 + 3, y0 + 12, x0 + 3 + Math.round((width - 6) * Mth.clamp(health, 0.0f, 1.0f)), y0 + 14, argb(0xFF, health > 0.5f ? GREEN : health > 0.25f ? AMBER : RED));
	}

	private static @Nullable LivingEntity sighted(LocalPlayer player, float partial) {
		Vec3 eye = player.getEyePosition(partial);
		Vec3 look = player.getViewVector(partial);
		Vec3 end = eye.add(look.scale(TARGET_RANGE));
		BlockHitResult blocked = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		end = blocked.getLocation();
		AABB area = player.getBoundingBox().expandTowards(look.scale(TARGET_RANGE)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, area,
				entity -> entity instanceof LivingEntity && entity.isAlive() && !entity.isSpectator() && !entity.isInvisible(), eye.distanceToSqr(end));
		return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
	}

	/** Mk III helmets: everything alive within {@link #RADAR_RANGE}, the way the wearer faces at the top. */
	private static void radar(GuiGraphicsExtractor graphics, Font font, Minecraft minecraft, LocalPlayer player) {
		int cx = graphics.guiWidth() - 6 - RADAR_RADIUS - 4, cy = graphics.guiHeight() - 36 - RADAR_RADIUS - 4;
		long now = Util.getMillis();
		// A filled disc, row by row.
		for (int dy = -RADAR_RADIUS; dy <= RADAR_RADIUS; dy++) {
			int dx = (int) Math.sqrt(RADAR_RADIUS * RADAR_RADIUS - dy * dy);
			graphics.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, GLASS);
		}
		NeuralStyle.ring(graphics, cx, cy, RADAR_RADIUS, 0.0f, 1.0f, argb(0xC0, AMBER));
		NeuralStyle.ring(graphics, cx, cy, RADAR_RADIUS / 2.0f, 0.0f, 1.0f, argb(0x50, AMBER));
		graphics.horizontalLine(cx - RADAR_RADIUS + 2, cx + RADAR_RADIUS - 2, cy, argb(0x30, AMBER));
		graphics.verticalLine(cx, cy - RADAR_RADIUS + 2, cy + RADAR_RADIUS - 2, argb(0x30, AMBER));
		// The sweep.
		float sweep = (now % 2400) / 2400.0f;
		NeuralStyle.line(graphics, cx, cy, cx + Mth.sin(sweep * Mth.TWO_PI) * (RADAR_RADIUS - 1), cy - Mth.cos(sweep * Mth.TWO_PI) * (RADAR_RADIUS - 1),
				argb(0x90, AMBER));
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		double scale = RADAR_RADIUS / RADAR_RANGE;
		int threats = 0;
		for (Entity entity : player.level().getEntities(player, player.getBoundingBox().inflate(RADAR_RANGE),
				entity -> entity instanceof LivingEntity && entity.isAlive() && !entity.isSpectator())) {
			double dx = entity.getX() - player.getX(), dz = entity.getZ() - player.getZ();
			// Right of the wearer is right on the radar, ahead is up.
			double side = dx * -Mth.cos(yaw) + dz * -Mth.sin(yaw);
			double ahead = dx * -Mth.sin(yaw) + dz * Mth.cos(yaw);
			if (side * side + ahead * ahead > RADAR_RANGE * RADAR_RANGE) {
				continue;
			}
			int px = cx + (int) Math.round(side * scale), py = cy - (int) Math.round(ahead * scale);
			int colour = entity instanceof Enemy ? RED : entity instanceof Player ? ARC : 0xB0B0B0;
			if (entity instanceof Enemy) {
				threats++;
			}
			graphics.fill(px - 1, py - 1, px + 1, py + 1, argb(0xFF, colour));
		}
		graphics.fill(cx - 1, cy - 1, cx + 1, cy + 1, argb(0xFF, TEXT));
		String label = Component.translatable("hud.tomyen.power_armor.threats", threats).getString();
		graphics.centeredText(font, label, cx, cy + RADAR_RADIUS + 3, threats > 0 ? argb(0xFF, RED) : DIM);
	}
}
