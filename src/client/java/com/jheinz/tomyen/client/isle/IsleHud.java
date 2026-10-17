package com.jheinz.tomyen.client.isle;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

import com.jheinz.tomyen.isle.Isle;
import com.jheinz.tomyen.isle.IsleDepth;
import com.jheinz.tomyen.isle.IsleHint;
import com.jheinz.tomyen.isle.Revenant;

/**
 * A faint compass strip at the top of the screen while on the Isle with a hoard there: a soul-flame mark shows which
 * way your Revenant waits (pinned to an end of the strip, with an arrow, when it's behind you), the distance and the
 * depth under it. It fades out as you reach the arena, and moves down out of the way of a Revenant's health bar.
 */
public final class IsleHud {
	private static final int HALF_WIDTH = 64;
	/** Degrees either side of straight ahead the strip shows. */
	private static final float SPAN = 90.0f;
	private static final int SOUL = 0x7FE7F0;
	private static final int FADE_NEAR = 12;
	private static final int FADE_FAR = 28;

	private IsleHud() {
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		IsleHint hint = player.getAttached(Isle.HINT);
		if (hint == null) {
			return;
		}
		BlockPos target = hint.target();
		double dx = target.getX() + 0.5 - player.getX();
		double dz = target.getZ() + 0.5 - player.getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);
		float fade = Mth.clamp((float) ((distance - FADE_NEAR) / (FADE_FAR - FADE_NEAR)), 0.0f, 1.0f);
		if (fade <= 0.0f) {
			return;
		}
		float bearing = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
		float relative = Mth.wrapDegrees(bearing - player.getYRot(delta.getGameTimeDeltaPartialTick(true)));

		int centre = graphics.guiWidth() / 2;
		int y = 6 + (revenantNear(minecraft, player) ? 19 : 0);
		int lineAlpha = (int) (70 * fade);
		graphics.fill(centre - HALF_WIDTH, y + 4, centre + HALF_WIDTH, y + 5, ARGB.color(lineAlpha, 0xC8D8E0));
		// Ticks every 45 degrees of the world, sliding past as you turn.
		for (int heading = -180; heading < 180; heading += 45) {
			float at = Mth.wrapDegrees(heading - player.getYRot());
			if (Math.abs(at) <= SPAN) {
				int x = centre + Math.round(at / SPAN * HALF_WIDTH);
				int tall = heading % 90 == 0 ? 3 : 2;
				graphics.fill(x, y + 5 - tall, x + 1, y + 5 + tall - 1, ARGB.color(lineAlpha + 20, 0xC8D8E0));
			}
		}
		boolean behind = Math.abs(relative) > SPAN;
		int mark = centre + Math.round(Mth.clamp(relative, -SPAN, SPAN) / SPAN * HALF_WIDTH);
		int markAlpha = (int) (230 * fade);
		int colour = ARGB.color(markAlpha, SOUL);
		// A small diamond of soul fire, or an arrowhead at the strip's end if it's behind.
		if (behind) {
			int dir = relative < 0 ? -1 : 1;
			for (int i = 0; i < 4; i++) {
				graphics.fill(mark + dir * (i - 3), y + 4 - (3 - i), mark + dir * (i - 3) + 1, y + 5 + (3 - i), colour);
			}
		} else {
			for (int i = 0; i < 4; i++) {
				graphics.fill(mark - i, y + 1 + i, mark + i + 1, y + 2 + i, colour);
				graphics.fill(mark - i, y + 7 - i, mark + i + 1, y + 8 - i, colour);
			}
		}
		Component label = Component.translatable("hud.tomyen.isle.revenant", (int) Math.round(distance), IsleDepth.of(hint.depth()).displayName());
		int textAlpha = (int) (200 * fade);
		if (textAlpha > 8) {
			graphics.centeredText(minecraft.font, label, centre, y + 11, ARGB.color(textAlpha, 0xB8CCD6));
		}
	}

	/** Whether a Revenant is close enough that its health bar is showing. */
	private static boolean revenantNear(Minecraft minecraft, LocalPlayer player) {
		return minecraft.level != null && !minecraft.level.getEntitiesOfClass(Revenant.class, new AABB(player.blockPosition()).inflate(96.0)).isEmpty();
	}
}
