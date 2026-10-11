package com.jheinz.mymod.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import com.jheinz.mymod.GunItem;
import com.jheinz.mymod.GunStats;
import com.jheinz.mymod.MyMod;

/** Sneaking in first person with a scoped gun looks through the scope: zoomed view behind the scope and its reticle. */
public final class GunScope {
	private static final Identifier SCOPE_TEXTURE = MyMod.id("textures/misc/scope.png");
	private static final int BLACK = 0xFF000000;

	private GunScope() {
	}

	/**
	 * The field-of-view multiplier for the sight this player is aiming through (the gun's scope, or a red dot
	 * sight), or 0 if they aren't aiming.
	 */
	public static float zoom(Player player) {
		Minecraft minecraft = Minecraft.getInstance();
		if (player != minecraft.player || !minecraft.options.getCameraType().isFirstPerson() || !player.isShiftKeyDown()
				|| !(player.getMainHandItem().getItem() instanceof GunItem gun)) {
			return 0.0f;
		}
		GunStats stats = gun.stats(player.getMainHandItem());
		return stats.scoped() ? stats.scopeZoom() : 0.0f;
	}

	/** Whether the player is looking through the gun's own scope (as opposed to a red dot sight). */
	public static boolean throughScope(Player player) {
		return zoom(player) > 0.0f && player.getMainHandItem().getItem() instanceof GunItem gun && gun.stats().scoped();
	}

	/** Draws the scope, blacking out everything around a centred circle; its reticle stands in for the crosshair. */
	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || !throughScope(minecraft.player)) {
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
