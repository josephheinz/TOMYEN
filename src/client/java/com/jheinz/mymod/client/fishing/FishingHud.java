package com.jheinz.mymod.client.fishing;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import com.jheinz.mymod.fishing.FishingNetworking.Outcome;

/**
 * The fight popup: a small dark panel just under the crosshair (the mouse stays free, it's not a screen). Its header
 * has the fish ("?" until a Fish Finder or the catch names it) and a row of difficulty pips; then the minigame's
 * widget; then a one-line hint. It eases up into place, flashes green, amber or red with the result, and fades out.
 * Before a bite, a Fish Finder's sonar ping shows as a small pill in the same spot.
 */
final class FishingHud {
	private static final int WIDTH = 172;
	private static final int PANEL = 0xD20E151C;
	private static final int PANEL_EDGE = 0x5A9DB4C8;
	private static final int BADGE = 0xFF22313F;
	private static final int TEXT = 0xFFE9EFF5;
	private static final int TEXT_DIM = 0xFF8C9BAA;
	private static final int[] PIP_COLORS = {0xFF5BD46A, 0xFFA6D45B, 0xFFE8C547, 0xFFEE8E43, 0xFFE5534B};

	private FishingHud() {
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		FishingMinigame game = FishingClient.game;
		if (game != null) {
			panel(graphics, minecraft.font, game, partial);
		} else if (!FishingClient.sonar.isEmpty()) {
			sonar(graphics, minecraft.font, partial);
		}
	}

	private static void panel(GuiGraphicsExtractor graphics, Font font, FishingMinigame game, float partial) {
		float appear = easeOutCubic(game.introProgress(partial));
		float alpha = appear;
		float sinceEnd = game.running() ? -1.0f : game.age - FishingClient.endAge + partial;
		if (sinceEnd > FishingClient.RESULT_TICKS - 12) {
			alpha *= Mth.clamp((FishingClient.RESULT_TICKS - sinceEnd) / 12.0f, 0.0f, 1.0f);
		}
		if (alpha <= 0.02f) {
			return;
		}
		int body = game.bodyHeight();
		int height = 22 + body + 15;
		int x0 = (graphics.guiWidth() - WIDTH) / 2;
		int y0 = graphics.guiHeight() / 2 + 18;
		graphics.pose().pushMatrix();
		graphics.pose().translate(0.0f, (1.0f - appear) * 10.0f);
		roundedPanel(graphics, x0, y0, WIDTH, height, alpha);

		// Header: what's on the line, and how hard it is.
		ItemStack shown = game.params.shown();
		if (FishingClient.verdict != null && !FishingClient.verdict.caught().isEmpty()) {
			shown = FishingClient.verdict.caught();
		}
		badge(graphics, font, shown, x0 + 6, y0 + 5, alpha);
		Component name = shown.isEmpty() ? Component.translatable("hud.mymod.fishing.unknown") : shown.getHoverName();
		graphics.text(font, name, x0 + 22, y0 + 7, FishingMinigame.alpha(shown.isEmpty() ? TEXT_DIM : TEXT, alpha), false);
		pips(graphics, x0 + WIDTH - 6, y0 + 9, game.params.difficulty(), alpha);

		// The game, then what to do.
		int bodyY = y0 + 22;
		game.extract(graphics, font, x0 + 8, bodyY, WIDTH - 16, partial, alpha);
		if (game.running()) {
			Component hint = game.hint(FishingClient.keys());
			small(graphics, font, hint, x0 + WIDTH / 2, bodyY + body + 4, FishingMinigame.alpha(TEXT_DIM, alpha));
		}

		if (!game.running()) {
			result(graphics, font, game, x0, y0, height, sinceEnd, alpha);
		}
		graphics.pose().popMatrix();
	}

	/** The result: a flash over the panel, then its verdict across the middle. */
	private static void result(GuiGraphicsExtractor graphics, Font font, FishingMinigame game, int x0, int y0, int height, float sinceEnd,
			float alpha) {
		Outcome outcome = FishingClient.shownOutcome();
		int color = switch (outcome) {
			case CAUGHT -> 0xFF5BD46A;
			case SNAPPED -> 0xFFE5534B;
			default -> 0xFFE8C547;
		};
		float flash = 1.0f - Mth.clamp(sinceEnd / 16.0f, 0.0f, 1.0f);
		graphics.fill(x0 + 1, y0 + 1, x0 + WIDTH - 1, y0 + height - 1, FishingMinigame.alpha(color, 0.45f * flash * alpha));
		// A scrim under the words so the widget behind doesn't fight them.
		graphics.fill(x0 + 1, y0 + 19, x0 + WIDTH - 1, y0 + height - 1, FishingMinigame.alpha(0xFF0E151C, 0.82f * alpha));

		Component title = Component.translatable("hud.mymod.fishing.outcome." + outcome.name().toLowerCase(java.util.Locale.ROOT));
		float pop = 1.0f + 0.25f * (1.0f - easeOutCubic(Mth.clamp(sinceEnd / 6.0f, 0.0f, 1.0f)));
		int cx = x0 + WIDTH / 2;
		int cy = y0 + 22 + (height - 22) / 2 - 7;
		graphics.pose().pushMatrix();
		graphics.pose().translate(cx, cy);
		graphics.pose().scale(1.25f * pop, 1.25f * pop);
		graphics.centeredText(font, title, 0, -4, FishingMinigame.alpha(color, alpha));
		graphics.pose().popMatrix();

		if (FishingClient.verdict != null && outcome == Outcome.CAUGHT) {
			Component detail = Component.translatable("hud.mymod.fishing.xp", FishingClient.verdict.xp());
			if (FishingClient.verdict.perfect()) {
				detail = Component.translatable("hud.mymod.fishing.perfect").append("  ").append(detail);
			}
			small(graphics, font, detail, cx, cy + 9, FishingMinigame.alpha(FishingClient.verdict.perfect() ? 0xFFFFE27A : TEXT_DIM, alpha));
		}
	}

	/** Before the bite: "Sonar: Glowfin Tetra" in a small pill under the crosshair. */
	private static void sonar(GuiGraphicsExtractor graphics, Font font, float partial) {
		float age = FishingClient.sonarAge + partial;
		float alpha = Mth.clamp(age / 6.0f, 0.0f, 1.0f) * Mth.clamp((120.0f - age) / 15.0f, 0.0f, 1.0f);
		if (alpha <= 0.02f) {
			return;
		}
		ItemStack stack = FishingClient.sonar;
		Component text = Component.translatable("hud.mymod.fishing.sonar", stack.getHoverName());
		int width = 22 + font.width(text) + 8 + 5 * 7 + 4;
		int x0 = (graphics.guiWidth() - width) / 2;
		int y0 = graphics.guiHeight() / 2 + 18;
		roundedPanel(graphics, x0, y0, width, 22, alpha);
		badge(graphics, font, stack, x0 + 5, y0 + 5, alpha);
		// A ping ring expanding from the badge.
		float ring = (age % 20.0f) / 20.0f;
		int r = Math.round(6 + ring * 8);
		graphics.outline(x0 + 11 - r, y0 + 11 - r, 2 * r, 2 * r, FishingMinigame.alpha(0xFF6CCBFF, alpha * (1.0f - ring) * 0.6f));
		graphics.text(font, text, x0 + 21, y0 + 7, FishingMinigame.alpha(TEXT, alpha), false);
		pips(graphics, x0 + width - 5, y0 + 9, FishingClient.sonarDifficulty, alpha);
	}

	/** A 12x12 rounded square with the fish's icon, or a "?" while it's unknown. */
	private static void badge(GuiGraphicsExtractor graphics, Font font, ItemStack stack, int x, int y, float alpha) {
		graphics.fill(x + 1, y, x + 13, y + 14, FishingMinigame.alpha(BADGE, alpha));
		graphics.fill(x, y + 1, x + 14, y + 13, FishingMinigame.alpha(BADGE, alpha));
		if (!stack.isEmpty() && alpha > 0.85f) {
			graphics.pose().pushMatrix();
			graphics.pose().translate(x + 1, y + 1);
			graphics.pose().scale(0.75f, 0.75f);
			graphics.item(stack, 0, 0);
			graphics.pose().popMatrix();
		} else if (stack.isEmpty()) {
			graphics.centeredText(font, "?", x + 7, y + 3, FishingMinigame.alpha(TEXT, alpha));
		}
	}

	/** Five pips, right-aligned at {@code right}: as many lit as the difficulty, in its colour. */
	private static void pips(GuiGraphicsExtractor graphics, int right, int y, int difficulty, float alpha) {
		int color = PIP_COLORS[Mth.clamp(difficulty, 1, 5) - 1];
		for (int i = 0; i < 5; i++) {
			int x = right - (5 - i) * 7 + 2;
			graphics.fill(x, y, x + 5, y + 4, FishingMinigame.alpha(i < difficulty ? color : 0x40FFFFFF, alpha));
		}
	}

	private static void roundedPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height, float alpha) {
		int edge = FishingMinigame.alpha(PANEL_EDGE, alpha);
		int fill = FishingMinigame.alpha(PANEL, alpha);
		graphics.fill(x + 2, y, x + width - 2, y + 1, edge);
		graphics.fill(x + 2, y + height - 1, x + width - 2, y + height, edge);
		graphics.fill(x, y + 2, x + 1, y + height - 2, edge);
		graphics.fill(x + width - 1, y + 2, x + width, y + height - 2, edge);
		graphics.fill(x + 1, y + 1, x + 2, y + 2, edge);
		graphics.fill(x + width - 2, y + 1, x + width - 1, y + 2, edge);
		graphics.fill(x + 1, y + height - 2, x + 2, y + height - 1, edge);
		graphics.fill(x + width - 2, y + height - 2, x + width - 1, y + height - 1, edge);
		graphics.fill(x + 2, y + 1, x + width - 2, y + height - 1, fill);
		graphics.fill(x + 1, y + 2, x + 2, y + height - 2, fill);
		graphics.fill(x + width - 2, y + 2, x + width - 1, y + height - 2, fill);
		// A faint highlight along the top edge.
		graphics.fill(x + 2, y + 1, x + width - 2, y + 2, FishingMinigame.alpha(0x22FFFFFF, alpha));
	}

	/** Text at three-quarter size, centred on {@code cx}. */
	private static void small(GuiGraphicsExtractor graphics, Font font, Component text, int cx, int y, int color) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(cx, y);
		graphics.pose().scale(0.75f, 0.75f);
		graphics.centeredText(font, text, 0, 0, color);
		graphics.pose().popMatrix();
	}

	private static float easeOutCubic(float t) {
		float u = 1.0f - t;
		return 1.0f - u * u * u;
	}
}
