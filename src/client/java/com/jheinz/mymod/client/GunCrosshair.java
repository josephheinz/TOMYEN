package com.jheinz.mymod.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

import com.jheinz.mymod.GunItem;
import com.jheinz.mymod.GunSpread;

/**
 * With a gun out, the crosshair is four ticks around an open centre whose gap matches the current shot
 * spread: it opens up as bloom builds during rapid fire, and tightens when sneaking. Aiming down a
 * scope or optic hides it; the sight has its own reticle.
 */
public final class GunCrosshair {
	private static final int ARM_LENGTH = 4;
	private static final int MIN_GAP = 2;
	private static final int MAX_GAP = 60;
	private static final int COLOR = 0xFFFFFFFF;
	private static final int SHADOW = 0x99000000;

	private GunCrosshair() {
	}

	/** Draws the gun crosshair if it applies, returning false so vanilla's is drawn otherwise. */
	public static boolean extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || !minecraft.options.getCameraType().isFirstPerson()
				|| !(minecraft.player.getMainHandItem().getItem() instanceof GunItem gun)) {
			return false;
		}
		if (GunScope.zoom(minecraft.player) > 0.0f) {
			// Aiming: the scope's or optic's own reticle is the crosshair.
			return true;
		}

		float spread = GunSpread.clientSpread(gun.stats(minecraft.player.getMainHandItem(), minecraft.player), minecraft.player);
		float halfFov = (float) Math.toRadians(minecraft.options.fov().get() / 2.0);
		int gap = Math.round((float) (Math.tan(Math.toRadians(spread)) / Math.tan(halfFov)) * graphics.guiHeight() / 2.0f);
		gap = Math.max(MIN_GAP, Math.min(MAX_GAP, gap));

		int cx = graphics.guiWidth() / 2;
		int cy = graphics.guiHeight() / 2;
		tick(graphics, cx - gap - ARM_LENGTH, cy, cx - gap, cy + 1);
		tick(graphics, cx + gap + 1, cy, cx + gap + 1 + ARM_LENGTH, cy + 1);
		tick(graphics, cx, cy - gap - ARM_LENGTH, cx + 1, cy - gap);
		tick(graphics, cx, cy + gap + 1, cx + 1, cy + gap + 1 + ARM_LENGTH);
		return true;
	}

	private static void tick(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1) {
		graphics.fill(RenderPipelines.GUI, x0 - 1, y0 - 1, x1 + 1, y1 + 1, SHADOW);
		graphics.fill(RenderPipelines.GUI, x0, y0, x1, y1, COLOR);
	}
}
