package com.jheinz.mymod.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import com.jheinz.mymod.GunItem;

/** Sneaking in first person with a scoped gun looks through the scope: zoomed view behind a scope mask. */
public final class GunScope {
	private static final Identifier SCOPE_TEXTURE = Identifier.withDefaultNamespace("textures/misc/spyglass_scope.png");
	private static final int BLACK = 0xFF000000;

	private GunScope() {
	}

	/** The field-of-view multiplier for this player's scope, or 0 if they aren't looking through one. */
	public static float zoom(Player player) {
		Minecraft minecraft = Minecraft.getInstance();
		if (player != minecraft.player || !minecraft.options.getCameraType().isFirstPerson() || !player.isShiftKeyDown()
				|| !(player.getMainHandItem().getItem() instanceof GunItem gun) || !gun.stats().scoped()) {
			return 0.0f;
		}
		return gun.stats().scopeZoom();
	}

	/** Draws the spyglass mask, blacking out everything around a centred circle; the crosshair stays on top. */
	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || zoom(minecraft.player) == 0.0f) {
			return;
		}
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		int size = Math.min(width, height);
		int left = (width - size) / 2;
		int top = (height - size) / 2;
		graphics.blit(RenderPipelines.GUI_TEXTURED, SCOPE_TEXTURE, left, top, 0.0f, 0.0f, size, size, size, size);
		graphics.fill(RenderPipelines.GUI, 0, 0, width, top, BLACK);
		graphics.fill(RenderPipelines.GUI, 0, top + size, width, height, BLACK);
		graphics.fill(RenderPipelines.GUI, 0, top, left, top + size, BLACK);
		graphics.fill(RenderPipelines.GUI, left + size, top, width, top + size, BLACK);
	}
}
