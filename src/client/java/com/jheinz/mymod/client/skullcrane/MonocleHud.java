package com.jheinz.mymod.client.skullcrane;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.joml.Vector2f;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.client.neuralink.NeuralSenses;
import com.jheinz.mymod.skullcrane.ServoGear;

/**
 * The Optic Monocle's two tricks, while it's worn (or socketed):
 * <ul>
 * <li>Hold the zoom key (Z by default) to zoom in like a spyglass: the local player counts as scoping, so vanilla
 * narrows the view, slows the mouse and draws the spyglass's vignette (see PlayerMonocleZoomMixin).
 * <li>Every creature in sight within {@value #RANGE} blocks shows a health bar over its head, green to red, with its
 * health in numbers up close.
 * </ul>
 */
public final class MonocleHud {
	private static final double RANGE = 24.0;
	private static final double NUMBERS = 8.0;
	private static final KeyMapping ZOOM_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mymod.monocle_zoom",
			InputConstants.Type.KEYBOARD, InputConstants.KEY_Z, KeyMapping.Category.register(MyMod.id("gear"))));

	private MonocleHud() {
	}

	static void initialize() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR, MyMod.id("monocle_health"), MonocleHud::extract);
	}

	/** Whether the local player is zooming through their monocle right now. */
	public static boolean zooming() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player != null && minecraft.gui.screen() == null && ZOOM_KEY.isDown() && ServoGear.wearsMonocle(minecraft.player);
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null || !ServoGear.wearsMonocle(player)) {
			return;
		}
		float partialTick = delta.getGameTimeDeltaPartialTick(false);
		Vec3 camera = minecraft.gameRenderer.mainCamera().position();
		record Bar(Vector2f at, double distance, LivingEntity entity) {
		}
		List<Bar> bars = new ArrayList<>();
		for (Entity entity : minecraft.level.getEntities(player, player.getBoundingBox().inflate(RANGE))) {
			if (!(entity instanceof LivingEntity living) || !living.isAlive() || living instanceof ArmorStand || living.isInvisible()
					|| entity == minecraft.getCameraEntity() || !player.hasLineOfSight(entity)) {
				continue;
			}
			Vec3 head = entity.getPosition(partialTick).add(0.0, entity.getBbHeight() + 0.35, 0.0);
			double distance = head.distanceTo(camera);
			Vector2f at = NeuralSenses.project(graphics, head);
			if (at != null && distance <= RANGE) {
				bars.add(new Bar(at, distance, living));
			}
		}
		// Far ones first, so near ones are drawn over them.
		bars.sort(Comparator.comparingDouble(Bar::distance).reversed());
		for (Bar bar : bars) {
			LivingEntity living = bar.entity();
			float fraction = Mth.clamp(living.getHealth() / Math.max(living.getMaxHealth(), 1.0f), 0.0f, 1.0f);
			float scale = Mth.clamp(1.2f - (float) bar.distance() / 20.0f, 0.45f, 1.0f);
			int alpha = (int) (255 * Mth.clamp(1.0f - ((float) bar.distance() - RANGE * 0.7f) / (float) (RANGE * 0.3f), 0.3f, 1.0f));
			int color = Mth.hsvToRgb(fraction / 3.0f, 0.85f, 0.95f);
			graphics.pose().pushMatrix();
			graphics.pose().translate(bar.at().x, bar.at().y);
			graphics.pose().scale(scale, scale);
			int half = 16;
			graphics.fill(-half - 1, -4, half + 1, 0, alpha << 24);
			graphics.fill(-half, -3, -half + Math.round(2 * half * fraction), -1, alpha << 24 | color);
			if (bar.distance() < NUMBERS) {
				String text = Mth.ceil(living.getHealth()) + "/" + Mth.ceil(living.getMaxHealth());
				graphics.centeredText(minecraft.font, text, 0, -14, alpha << 24 | 0xFFFFFF);
			}
			graphics.pose().popMatrix();
		}
	}
}
