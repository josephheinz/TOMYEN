package com.jheinz.mymod.client.mech;

import org.joml.Vector2f;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.client.neuralink.NeuralSenses;
import com.jheinz.mymod.client.neuralink.NeuralStyle;
import com.jheinz.mymod.mech.Mech;
import com.jheinz.mymod.mech.MechArm;
import com.jheinz.mymod.mech.MechLoadout;
import com.jheinz.mymod.mech.MechPart;
import com.jheinz.mymod.mech.MechShoulder;
import com.jheinz.mymod.mech.MechWeapon;

import static com.jheinz.mymod.client.neuralink.NeuralStyle.argb;

/**
 * The Bulwark Walker's cockpit HUD, amber on smoked glass, drawn under the rest of the HUD (whose hotbar, bars and
 * crosshair it replaces in the cockpit).
 * <ul>
 * <li>Round the edges, the cockpit's viewport frame (see {@link #cockpitFrame}).
 * <li>The gunsight (see {@link #reticles}): a ring, dot and ticks where the pilot looks, each arm's heat and reload
 * as arcs round it, and a pip for each arm where it really points (the arms lag the view as the torso turns),
 * coloured by the weapon's state.
 * <li>Bottom corners, one per arm: the weapon, what's loaded and in the stores, and a bar for its heat, reload,
 * spin-up or grip.
 * <li>Bottom middle: the walker as a silhouette, each part coloured by its condition, with the hull's integrity;
 * the core's charge and the jump jets' charge; a top-down twist gauge (the legs turned as they stand under the
 * torso); and the smoke salvos or the hold.
 * <li>Over that: what a fist is holding, and the sneak-hold to climb out.
 * <li>From outside (F5): a compact panel and the sight on the aim point.
 * <li>Climbing in or out, the view fades through black into the cockpit or out of it.
 * </ul>
 */
public final class MechHud {
	private static final int AMBER = 0xFFB347;
	private static final int GREEN = 0x7CFF8A;
	private static final int RED = 0xFF4A3A;
	private static final int TEXT = 0xFFFFE6C2;
	private static final int DIM = 0xFFA87A3E;
	private static final int PANEL = 0xB0100C08;
	/** The cockpit frame: the hull's inner faces and the lit edge round the glass. */
	private static final int HULL_DARK = 0xFF181C21;
	private static final int HULL = 0xFF262C34;
	private static final int EDGE = 0xFF4A5563;
	private static final int SIGHT_RADIUS = 9;
	/** Within this many degrees of the view (both ways), the arms count as on the sight. */
	private static final float ALIGNED_DEGREES = 4.0f;

	private MechHud() {
	}

	static void initialize() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, MyMod.id("mech_cockpit"), MechHud::extract);
		for (Identifier element : new Identifier[] {VanillaHudElements.HOTBAR, VanillaHudElements.HEALTH_BAR, VanillaHudElements.FOOD_BAR,
				VanillaHudElements.ARMOR_BAR, VanillaHudElements.AIR_BAR, VanillaHudElements.EXPERIENCE_LEVEL, VanillaHudElements.INFO_BAR,
				VanillaHudElements.CROSSHAIR}) {
			HudElementRegistry.replaceElement(element, vanilla -> (graphics, delta) -> {
				if (MechClient.cockpitMech() == null) {
					vanilla.extractRenderState(graphics, delta);
				}
			});
		}
		HudElementRegistry.replaceElement(VanillaHudElements.MOUNT_HEALTH, vanilla -> (graphics, delta) -> {
			if (MechClient.pilotedMech() == null) {
				vanilla.extractRenderState(graphics, delta);
			}
		});
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		Mech mech = MechClient.pilotedMech();
		if (mech == null || minecraft.player == null) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		boolean firstPerson = minecraft.options.getCameraType().isFirstPerson();
		if (!mech.isSeated(minecraft.player)) {
			if (firstPerson) {
				climbFade(graphics, mech, partial);
			}
			return;
		}
		Font font = minecraft.font;
		if (firstPerson) {
			cockpitFrame(graphics);
		}
		reticles(graphics, mech, partial, firstPerson);
		if (firstPerson) {
			int width = graphics.guiWidth();
			int height = graphics.guiHeight();
			int margin = Math.max(3, Math.round(width * 0.016f)) + 4;
			int panelWidth = Math.min(132, (width - 140) / 2 - margin + 6);
			arm(graphics, font, mech, MechArm.LEFT, margin, height - 50, panelWidth);
			arm(graphics, font, mech, MechArm.RIGHT, width - margin - panelWidth, height - 50, panelWidth);
			status(graphics, font, mech, partial, width / 2, height - 6);
			if (mech.hurtTime > 0) {
				int alpha = 0x60 * mech.hurtTime / Math.max(1, mech.hurtDuration);
				graphics.outline(0, 0, width, height, argb(alpha, RED));
				graphics.outline(1, 1, width - 2, height - 2, argb(alpha / 2, RED));
			}
		} else {
			external(graphics, font, mech);
		}
		messages(graphics, font, mech);
	}

	/** Through the climb: from the world into black and up into the cockpit, or back out again. */
	private static void climbFade(GuiGraphicsExtractor graphics, Mech mech, float partial) {
		float progress = mech.sequenceProgress(partial);
		float black = mech.sequence() == Mech.SEQ_ENTERING ? Mth.clamp((progress - 0.74f) / 0.12f, 0.0f, 1.0f)
				* (1.0f - Mth.clamp((progress - 0.9f) / 0.1f, 0.0f, 1.0f))
				: progress < 0.08f ? Mth.clamp(progress / 0.04f, 0.0f, 1.0f) * (1.0f - Mth.clamp((progress - 0.04f) / 0.12f, 0.0f, 1.0f)) : 0.0f;
		if (black > 0.0f) {
			graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), argb((int) (black * 255), 0));
		}
	}

	private static int condition(float condition) {
		return condition > 0.66f ? GREEN : condition > 0.33f ? AMBER : RED;
	}

	/** An arm's state as a colour: green ready, amber busy, red stopped. */
	private static int stateColour(int status) {
		return switch (status) {
			case MechArm.READY, MechArm.HOLDING -> GREEN;
			case MechArm.RELOADING, MechArm.SPINNING -> AMBER;
			default -> RED;
		};
	}

	// --- The sight -------------------------------------------------------------------------------------------

	/**
	 * The gunsight. Where the pilot looks: a thin ring with a centre dot and four short ticks outside it, and round it
	 * each arm's gauges as thin arcs, its heat low on its side of the ring and its reload (or spin-up, grip or jam
	 * clearing) high. Where each arm really points: a small diamond pip with a tail out to its side, coloured by the
	 * arm's state. Lined up, the two pips sit together in the ring; while the torso swings round after the view they
	 * slide in from the side, and the ring dims until they're on it. From outside (F5) the ring sits on the aim point.
	 */
	private static void reticles(GuiGraphicsExtractor graphics, Mech mech, float partial, boolean firstPerson) {
		Vec3 aimPoint = MechClient.aimPoint(mech, partial);
		int cx = graphics.guiWidth() / 2;
		int cy = graphics.guiHeight() / 2;
		boolean aligned = true;
		if (firstPerson) {
			Player pilot = Minecraft.getInstance().player;
			if (pilot != null) {
				float yawOff = Mth.wrapDegrees(mech.torsoYaw(partial) - pilot.getViewYRot(partial));
				float pitchOff = mech.armPitch(partial) - Mth.clamp(pilot.getViewXRot(partial), Mech.MIN_PITCH, Mech.MAX_PITCH);
				aligned = Math.abs(yawOff) < ALIGNED_DEGREES && Math.abs(pitchOff) < ALIGNED_DEGREES;
			}
		} else {
			Vector2f at = NeuralSenses.project(graphics, aimPoint);
			if (at == null) {
				return;
			}
			cx = Math.round(at.x);
			cy = Math.round(at.y);
		}
		int ring = argb(aligned ? 0xC8 : 0x58, AMBER);
		NeuralStyle.ring(graphics, cx, cy, SIGHT_RADIUS, 0.0f, 1.0f, ring);
		graphics.fill(cx, cy, cx + 1, cy + 1, argb(0xF0, AMBER));
		int in = SIGHT_RADIUS + 3;
		int out = SIGHT_RADIUS + 7;
		graphics.fill(cx, cy - out, cx + 1, cy - in, ring);
		graphics.fill(cx, cy + in + 1, cx + 1, cy + out + 1, ring);
		graphics.fill(cx - out, cy, cx - in, cy + 1, ring);
		graphics.fill(cx + in + 1, cy, cx + out + 1, cy + 1, ring);

		for (int side = 0; side < 2; side++) {
			MechWeapon weapon = mech.weapon(side);
			if (weapon == MechWeapon.NONE || !MechLoadout.has(mech.look(), side == MechArm.LEFT ? MechLoadout.LEFT_ARM : MechLoadout.RIGHT_ARM)) {
				continue;
			}
			int packed = mech.armState(side);
			int status = MechArm.status(packed);
			gauges(graphics, cx, cy, side, weapon, packed, status);
			Vector2f at = NeuralSenses.project(graphics, MechClient.armAim(mech, side, aimPoint, partial));
			if (at != null) {
				pip(graphics, Math.round(at.x), Math.round(at.y), side == MechArm.LEFT ? -1 : 1, argb(0xF0, stateColour(status)));
			}
		}
	}

	/**
	 * An arm's two arcs round the sight, on its own side: heat in the lower part of the side (filling upwards, green to
	 * amber to red) and the reload, spin-up, grip or jam in the upper part (filling downwards). Each shows (on a faint
	 * track) only while it has something to say, so an idle sight is just the ring, the dot, the ticks and the pips.
	 */
	private static void gauges(GuiGraphicsExtractor graphics, int cx, int cy, int side, MechWeapon weapon, int packed, int status) {
		float radius = SIGHT_RADIUS + 4;
		boolean left = side == MechArm.LEFT;
		int track = argb(0x38, AMBER);
		// In turns (0 up, clockwise). The right side runs 0.07-0.20 (upper) and 0.30-0.43 (lower); the left mirrors it.
		float upperFrom = left ? 0.80f : 0.07f;
		float lowerFrom = left ? 0.57f : 0.30f;
		float span = 0.13f;
		if (weapon == MechWeapon.AUTOCANNON || weapon == MechWeapon.ROTARY_CANNON) {
			float heat = MechArm.heat(packed);
			if (heat > 0.01f) {
				NeuralStyle.ring(graphics, cx, cy, radius, lowerFrom, lowerFrom + span, track);
				int colour = argb(0xF0, status == MechArm.OVERHEATED || heat > 0.85f ? RED : heat > 0.6f ? AMBER : GREEN);
				// Filling from the bottom of the ring upwards.
				if (left) {
					NeuralStyle.ring(graphics, cx, cy, radius, lowerFrom, lowerFrom + span * heat, colour);
				} else {
					NeuralStyle.ring(graphics, cx, cy, radius, lowerFrom + span * (1.0f - heat), lowerFrom + span, colour);
				}
			}
		}
		float progress = MechArm.progress(packed);
		if (progress > 0.01f) {
			NeuralStyle.ring(graphics, cx, cy, radius, upperFrom, upperFrom + span, track);
			int colour = argb(0xF0, status == MechArm.JAMMED ? RED : status == MechArm.READY || status == MechArm.HOLDING ? GREEN : AMBER);
			// Filling from the top of the ring downwards.
			if (left) {
				NeuralStyle.ring(graphics, cx, cy, radius, upperFrom + span * (1.0f - progress), upperFrom + span, colour);
			} else {
				NeuralStyle.ring(graphics, cx, cy, radius, upperFrom, upperFrom + span * progress, colour);
			}
		}
	}

	/** Where an arm points: a small hollow diamond with a short tail out to its side ({@code dir} -1 left, 1 right). */
	private static void pip(GuiGraphicsExtractor graphics, int x, int y, int dir, int colour) {
		graphics.fill(x - 2, y, x - 1, y + 1, colour);
		graphics.fill(x + 2, y, x + 3, y + 1, colour);
		graphics.fill(x, y - 2, x + 1, y - 1, colour);
		graphics.fill(x, y + 2, x + 1, y + 3, colour);
		graphics.fill(x - 1, y - 1, x, y, colour);
		graphics.fill(x + 1, y - 1, x + 2, y, colour);
		graphics.fill(x - 1, y + 1, x, y + 2, colour);
		graphics.fill(x + 1, y + 1, x + 2, y + 2, colour);
		int tail0 = x + dir * 4;
		int tail1 = x + dir * 6;
		graphics.fill(Math.min(tail0, tail1), y, Math.max(tail0, tail1) + 1, y + 1, colour);
	}

	// --- The cockpit frame -------------------------------------------------------------------------------------

	/**
	 * The viewport's frame round the edges of the view: the hull's dark inner face in two flat tones, with one lit
	 * edge where it meets the glass. A brow across the top that slopes down into the corners, narrow posts either side
	 * and a low sill (the top of the chest hatch) along the bottom. Flat shapes only, kept to the edges.
	 */
	private static void cockpitFrame(GuiGraphicsExtractor graphics) {
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		int brow = Math.max(5, Math.round(height * 0.04f));
		int post = Math.max(3, Math.round(width * 0.016f));
		int sill = Math.max(4, Math.round(height * 0.028f));
		int cornerWidth = Math.round(width * 0.085f);
		int cornerHeight = Math.round(height * 0.15f);
		int lowCornerWidth = Math.round(width * 0.05f);
		int lowCornerHeight = Math.round(height * 0.07f);
		// The far face, darker, then the near face a touch lighter inside it.
		graphics.fill(0, 0, width, brow, HULL_DARK);
		graphics.fill(0, height - sill, width, height, HULL_DARK);
		graphics.fill(0, 0, post, height, HULL_DARK);
		graphics.fill(width - post, 0, width, height, HULL_DARK);
		for (int i = 0; i < cornerWidth; i++) {
			int drop = brow + Math.round((cornerHeight - brow) * (1.0f - i / (float) cornerWidth));
			graphics.fill(post + i, 0, post + i + 1, drop, HULL);
			graphics.fill(width - post - i - 1, 0, width - post - i, drop, HULL);
			graphics.fill(post + i, drop, post + i + 1, drop + 1, EDGE);
			graphics.fill(width - post - i - 1, drop, width - post - i, drop + 1, EDGE);
		}
		for (int i = 0; i < lowCornerWidth; i++) {
			int rise = sill + Math.round((lowCornerHeight - sill) * (1.0f - i / (float) lowCornerWidth));
			graphics.fill(post + i, height - rise, post + i + 1, height, HULL);
			graphics.fill(width - post - i - 1, height - rise, width - post - i, height, HULL);
			graphics.fill(post + i, height - rise - 1, post + i + 1, height - rise, EDGE);
			graphics.fill(width - post - i - 1, height - rise - 1, width - post - i, height - rise, EDGE);
		}
		// The lit edges along the brow, the posts and the sill.
		graphics.fill(post + cornerWidth, brow, width - post - cornerWidth, brow + 1, EDGE);
		graphics.fill(post + lowCornerWidth, height - sill - 1, width - post - lowCornerWidth, height - sill, EDGE);
		graphics.fill(post, cornerHeight, post + 1, height - lowCornerHeight, EDGE);
		graphics.fill(width - post - 1, cornerHeight, width - post, height - lowCornerHeight, EDGE);
	}

	// --- Panels ------------------------------------------------------------------------------------------------

	/** One arm: its weapon, what's loaded and in reserve, and a bar for whatever it's busy with. */
	private static void arm(GuiGraphicsExtractor graphics, Font font, Mech mech, int side, int x0, int y0, int width) {
		int x1 = x0 + width;
		int y1 = y0 + 40;
		graphics.fill(x0, y0, x1, y1, PANEL);
		graphics.outline(x0, y0, width, y1 - y0, argb(0x50, AMBER));
		boolean fitted = MechLoadout.has(mech.look(), side == MechArm.LEFT ? MechLoadout.LEFT_ARM : MechLoadout.RIGHT_ARM);
		MechWeapon weapon = fitted ? mech.weapon(side) : MechWeapon.NONE;
		int packed = mech.armState(side);
		int status = MechArm.status(packed);
		float condition = mech.partCondition(MechPart.arm(side));
		Component name = Component.translatable(weapon.nameKey());
		Component sideName = Component.translatable(side == MechArm.LEFT ? "hud.mymod.mech.left_short" : "hud.mymod.mech.right_short");
		graphics.text(font, sideName, x0 + 4, y0 + 4, argb(0xFF, condition(condition)));
		graphics.text(font, name, x0 + 8 + font.width(sideName), y0 + 4, TEXT);
		if (weapon == MechWeapon.NONE) {
			return;
		}
		Component line = switch (weapon) {
			case AUTOCANNON, ROCKET_POD -> Component.translatable("hud.mymod.mech.ammo", MechArm.loaded(packed), weapon.magazine(), mech.reserve(side));
			case ROTARY_CANNON -> Component.translatable("hud.mymod.mech.belt", mech.reserve(side));
			case FLAMER -> Component.translatable("hud.mymod.mech.gel", Math.round(MechArm.loaded(packed) * 100.0f / weapon.magazine()),
					mech.reserve(side));
			default -> Component.translatable(mech.holding(side) ? "hud.mymod.mech.fist_holding" : "hud.mymod.mech.fist_ready");
		};
		graphics.text(font, line, x0 + 4, y0 + 15, DIM);
		Component state = Component.translatable("hud.mymod.mech.status." + status);
		graphics.text(font, state, x1 - 4 - font.width(state), y0 + 4, argb(0xFF, stateColour(status)));
		// Heat for the guns; under it the reload, spin-up, grip or jam clearing.
		int barWidth = width - 8;
		float heat = MechArm.heat(packed);
		if (weapon == MechWeapon.AUTOCANNON || weapon == MechWeapon.ROTARY_CANNON) {
			bar(graphics, x0 + 4, y0 + 27, barWidth, heat, status == MechArm.OVERHEATED ? RED : heat > 0.7f ? AMBER : GREEN);
		}
		float progress = MechArm.progress(packed);
		if (progress > 0.0f) {
			bar(graphics, x0 + 4, y0 + 33, barWidth, progress, status == MechArm.JAMMED ? RED : AMBER);
		}
	}

	/** The walker's condition, power, jets, twist and shoulder, bottom middle. */
	private static void status(GuiGraphicsExtractor graphics, Font font, Mech mech, float partial, int cx, int bottom) {
		int x0 = cx - 62;
		int x1 = cx + 62;
		int y0 = bottom - 52;
		graphics.fill(x0, y0, x1, bottom, PANEL);
		graphics.outline(x0, y0, x1 - x0, bottom - y0, argb(0x50, AMBER));
		// The silhouette: torso, arms either side, legs below. Facing away from the pilot, its left is on the left.
		int sx = x0 + 18;
		int sy = y0 + 8;
		int torso = argb(0xFF, condition(mech.partCondition(MechPart.TORSO)));
		graphics.fill(sx - 6, sy, sx + 6, sy + 12, torso);
		graphics.fill(sx - 3, sy - 4, sx + 3, sy, torso);
		graphics.fill(sx - 11, sy + 1, sx - 7, sy + 13, argb(0xFF, condition(mech.partCondition(MechPart.LEFT_ARM))));
		graphics.fill(sx + 7, sy + 1, sx + 11, sy + 13, argb(0xFF, condition(mech.partCondition(MechPart.RIGHT_ARM))));
		int legs = argb(0xFF, condition(mech.partCondition(MechPart.LEGS)));
		graphics.fill(sx - 6, sy + 13, sx - 1, sy + 26, legs);
		graphics.fill(sx + 1, sy + 13, sx + 6, sy + 26, legs);
		float hull = mech.getHealth() / mech.getMaxHealth();
		graphics.centeredText(font, Math.round(hull * 100.0f) + "%", sx, sy + 30, argb(0xFF, condition(hull)));

		// Power and jets.
		int bx = x0 + 38;
		int barWidth = 52;
		float fuel = mech.fuel();
		graphics.text(font, Component.translatable("hud.mymod.mech.core"), bx, y0 + 4, DIM);
		if (fuel < 0.0f) {
			graphics.text(font, "--", bx + barWidth - font.width("--"), y0 + 4, argb(0xFF, RED));
		} else {
			String percent = Mth.ceil(fuel * 100.0f) + "%";
			graphics.text(font, percent, bx + barWidth - font.width(percent), y0 + 4, argb(0xFF, fuel > 0.15f ? GREEN : fuel > 0.0f ? AMBER : RED));
			bar(graphics, bx, y0 + 14, barWidth, fuel, fuel > 0.15f ? GREEN : AMBER);
		}
		float jets = mech.jetCharge();
		graphics.text(font, Component.translatable("hud.mymod.mech.jets"), bx, y0 + 20, DIM);
		bar(graphics, bx, y0 + 30, barWidth, jets, jets >= 0.999f ? GREEN : AMBER);
		MechShoulder shoulder = mech.shoulder();
		if (shoulder == MechShoulder.SMOKE_LAUNCHER) {
			StringBuilder salvos = new StringBuilder();
			for (int i = 0; i < Mech.MAX_SMOKE; i++) {
				salvos.append(i < mech.smokeSalvos() ? '■' : '□');
			}
			graphics.text(font, Component.translatable("hud.mymod.mech.smoke", salvos.toString(), MechClient.SMOKE_KEY.getTranslatedKeyMessage()),
					bx, y0 + 38, argb(0xFF, mech.smokeSalvos() > 0 ? GREEN : RED));
		} else if (shoulder == MechShoulder.CARGO_POD) {
			graphics.text(font, Component.translatable("hud.mymod.mech.cargo", MechClient.CARGO_KEY.getTranslatedKeyMessage()), bx, y0 + 38, DIM);
		}

		// The twist gauge: the torso always up, the legs turned under it, the limits as ticks.
		int gx = x1 - 15;
		int gy = y0 + 22;
		float twist = mech.twist(partial);
		NeuralStyle.ring(graphics, gx, gy, 12.0f, 0.0f, 1.0f, argb(0x40, AMBER));
		for (float limit : new float[] {-Mech.TWIST_LIMIT, Mech.TWIST_LIMIT}) {
			float radians = limit * Mth.DEG_TO_RAD;
			NeuralStyle.line(graphics, gx + Mth.sin(radians) * 10.0f, gy + Mth.cos(radians) * 10.0f, gx + Mth.sin(radians) * 13.0f,
					gy + Mth.cos(radians) * 13.0f, argb(0xA0, RED));
		}
		graphics.pose().pushMatrix();
		graphics.pose().translate(gx, gy);
		graphics.pose().rotate(-twist * Mth.DEG_TO_RAD);
		graphics.fill(-5, -6, -2, 7, legs);
		graphics.fill(2, -6, 5, 7, legs);
		graphics.fill(-1, -9, 1, -6, legs);
		graphics.pose().popMatrix();
		graphics.fill(gx - 1, gy - 12, gx + 1, gy - 2, argb(0xFF, Math.abs(twist) > Mech.TWIST_LIMIT - 5.0f ? RED : AMBER));
		graphics.fill(gx - 3, gy - 2, gx + 3, gy + 2, torso);
	}

	/** Above the middle panel: what the fists hold, and the sneak-hold to climb out. */
	private static void messages(GuiGraphicsExtractor graphics, Font font, Mech mech) {
		int cx = graphics.guiWidth() / 2;
		int y = graphics.guiHeight() - 72;
		Entity held = mech.held();
		if (held != null) {
			graphics.centeredText(font, Component.translatable("hud.mymod.mech.holding_hint", held.getDisplayName()), cx, y, argb(0xFF, AMBER));
			y -= 11;
		}
		float exit = MechClient.exitProgress();
		if (exit > 0.0f) {
			graphics.centeredText(font, Component.translatable("hud.mymod.mech.climbing_out"), cx, y - 6, argb(0xFF, AMBER));
			bar(graphics, cx - 30, y + 5, 60, exit, AMBER);
		}
	}

	/** From outside: one compact panel, bottom left. */
	private static void external(GuiGraphicsExtractor graphics, Font font, Mech mech) {
		int x0 = 6;
		int y1 = graphics.guiHeight() - 6;
		int y0 = y1 - 46;
		int x1 = x0 + 160;
		graphics.fill(x0, y0, x1, y1, PANEL);
		graphics.outline(x0, y0, x1 - x0, y1 - y0, argb(0x50, AMBER));
		float hull = mech.getHealth() / mech.getMaxHealth();
		graphics.text(font, Component.translatable("hud.mymod.mech.hull", Math.round(hull * 100.0f)), x0 + 4, y0 + 4, argb(0xFF, condition(hull)));
		float fuel = mech.fuel();
		Component core = Component.translatable("hud.mymod.mech.core_percent", fuel < 0.0f ? "--" : String.valueOf(Mth.ceil(fuel * 100.0f)));
		graphics.text(font, core, x1 - 4 - font.width(core), y0 + 4, argb(0xFF, fuel > 0.15f ? GREEN : fuel > 0.0f ? AMBER : RED));
		for (int side = 0; side < 2; side++) {
			MechWeapon weapon = mech.weapon(side);
			int packed = mech.armState(side);
			int status = MechArm.status(packed);
			int y = y0 + 15 + side * 10;
			graphics.text(font, Component.translatable(side == MechArm.LEFT ? "hud.mymod.mech.left_short" : "hud.mymod.mech.right_short"),
					x0 + 4, y, DIM);
			graphics.text(font, Component.translatable(weapon.nameKey()), x0 + 18, y, TEXT);
			if (weapon != MechWeapon.NONE) {
				Component state = Component.translatable("hud.mymod.mech.status." + status);
				graphics.text(font, state, x1 - 4 - font.width(state), y, argb(0xFF, stateColour(status)));
			}
		}
		bar(graphics, x0 + 4, y0 + 38, x1 - x0 - 8, mech.jetCharge(), mech.jetCharge() >= 0.999f ? GREEN : AMBER);
	}

	private static void bar(GuiGraphicsExtractor graphics, int x, int y, int width, float fill, int colour) {
		graphics.fill(x, y, x + width, y + 3, argb(0x60, 0));
		graphics.fill(x, y, x + Math.round(width * Mth.clamp(fill, 0.0f, 1.0f)), y + 3, argb(0xFF, colour));
	}
}
