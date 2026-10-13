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
 * <li>The sight: a small dot where the pilot looks, and a bracket for each arm where its weapon will hit (the
 * point the torso's sight line meets: the arms lag the view as the torso turns), coloured by the weapon's state.
 * <li>Bottom corners, one per arm: the weapon, what's loaded and in the stores, and a bar for its heat, reload,
 * spin-up or grip.
 * <li>Bottom middle: the walker as a silhouette, each part coloured by its condition, with the hull's integrity;
 * the core's charge and the jump jets' charge; a top-down twist gauge (the legs turned as they stand under the
 * torso); and the smoke salvos or the hold.
 * <li>Over that: what a fist is holding, and the sneak-hold to climb out.
 * <li>From outside (F5): a compact panel and the arms' brackets.
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
		reticles(graphics, mech, partial, firstPerson);
		if (firstPerson) {
			int width = graphics.guiWidth();
			int height = graphics.guiHeight();
			int panelWidth = Math.min(132, (width - 140) / 2);
			arm(graphics, font, mech, MechArm.LEFT, 6, height - 46, panelWidth);
			arm(graphics, font, mech, MechArm.RIGHT, width - 6 - panelWidth, height - 46, panelWidth);
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

	/** The view dot, and each arm's bracket where its weapon will hit. */
	private static void reticles(GuiGraphicsExtractor graphics, Mech mech, float partial, boolean firstPerson) {
		int cx = graphics.guiWidth() / 2;
		int cy = graphics.guiHeight() / 2;
		if (firstPerson) {
			graphics.fill(cx, cy, cx + 1, cy + 1, argb(0xC0, AMBER));
		}
		Vector2f at = NeuralSenses.project(graphics, MechClient.aimPoint(mech, partial));
		if (at == null) {
			return;
		}
		int x = Math.round(at.x);
		int y = Math.round(at.y);
		for (int side = 0; side < 2; side++) {
			MechWeapon weapon = mech.weapon(side);
			if (weapon == MechWeapon.NONE || !MechLoadout.has(mech.look(), side == MechArm.LEFT ? MechLoadout.LEFT_ARM : MechLoadout.RIGHT_ARM)) {
				continue;
			}
			int colour = argb(0xE0, stateColour(MechArm.status(mech.armState(side))));
			int dir = side == MechArm.LEFT ? -1 : 1;
			int gap = weapon == MechWeapon.POWER_FIST ? 9 : 5;
			int reach = weapon == MechWeapon.ROTARY_CANNON || weapon == MechWeapon.FLAMER ? 8 : 6;
			// A bracket opening towards the middle: the left arm's on the left, the right's on the right.
			int bx = x + dir * gap;
			graphics.fill(Math.min(bx, bx + dir), y - reach, Math.max(bx, bx + dir) + 1, y + reach + 1, colour);
			graphics.fill(Math.min(bx, bx - dir * 3), y - reach, Math.max(bx, bx - dir * 3) + 1, y - reach + 1, colour);
			graphics.fill(Math.min(bx, bx - dir * 3), y + reach, Math.max(bx, bx - dir * 3) + 1, y + reach + 1, colour);
			if (weapon == MechWeapon.ROCKET_POD) {
				graphics.fill(x - 1, y - 1, x + 2, y + 2, colour);
			}
		}
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
