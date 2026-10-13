package com.jheinz.mymod.client.neuralink;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.joml.Vector2f;
import org.joml.Vector3fc;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.GunItem;
import com.jheinz.mymod.MagazineContents;
import com.jheinz.mymod.ModComponents;
import com.jheinz.mymod.launcher.Launchers;
import com.jheinz.mymod.napalm.FlamethrowerItem;
import com.jheinz.mymod.launcher.RocketLauncherItem;
import com.jheinz.mymod.neuralink.MindReader;
import com.jheinz.mymod.neuralink.NeuralNetworking;
import com.jheinz.mymod.neuralink.NeuralinkItem;
import com.jheinz.mymod.neuralink.NeuralinkModule;

import static com.jheinz.mymod.client.neuralink.NeuralStyle.CYAN;
import static com.jheinz.mymod.client.neuralink.NeuralStyle.DIM;
import static com.jheinz.mymod.client.neuralink.NeuralStyle.TEXT;
import static com.jheinz.mymod.client.neuralink.NeuralStyle.argb;

/**
 * The passive modules' HUD, anchored to things in the world where it can be:
 * <ul>
 * <li>Smart-Link: rounds left and fire mode beside the crosshair, and a hit marker (red for a kill) per hit.
 * <li>Mind Reader: a thought bubble over each mob the server reports on.
 * <li>Bio-Monitor: a vitals panel in the top-left corner, and red chevrons round the crosshair pointing at
 * anything hunting the wearer from out of view.
 * <li>Recall: markers for the last death and the respawn point, pinned to the screen's edge when off screen.
 * <li>Hive Mind: a marker over each teammate.
 * </ul>
 */
public final class NeuralSenses {
	private static final int DEATH = 0xFF4D4D;
	private static final int HP = 0xFF4D6A;
	private static final int FOOD = 0xFFB13D;
	private static final int AIR = 0x7FD8FF;
	private static final int THREAT = 0xFF2A2A;
	private static final long HIT_MARKER_MILLIS = 260;
	private static final int EDGE_MARGIN = 16;
	private static final int MAX_EFFECTS = 4;
	private static final String[] LEVELS = {"", " II", " III", " IV", " V"};

	private NeuralSenses() {
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			return;
		}
		Set<NeuralinkModule> active = NeuralinkItem.activeModules(player);
		float partialTick = delta.getGameTimeDeltaPartialTick(false);
		if (active.contains(NeuralinkModule.MIND_READER)) {
			thoughts(graphics, minecraft, partialTick);
		}
		if (active.contains(NeuralinkModule.RECALL)) {
			recall(graphics, minecraft);
		}
		if (active.contains(NeuralinkModule.HIVE_MIND)) {
			mates(graphics, minecraft, partialTick);
		}
		if (active.contains(NeuralinkModule.SMART_LINK)) {
			smartLink(graphics, minecraft, player);
		}
		if (active.contains(NeuralinkModule.BIO_MONITOR)) {
			vitals(graphics, minecraft, player);
			threats(graphics, minecraft, partialTick);
		}
	}

	// ------------------------------------------------------------------------------------------------ projection

	/** Where a point in the world is on the GUI, or null if it's behind the camera. */
	public static Vector2f project(GuiGraphicsExtractor graphics, Vec3 point) {
		Minecraft minecraft = Minecraft.getInstance();
		Camera camera = minecraft.gameRenderer.mainCamera();
		Vec3 offset = point.subtract(camera.position());
		Vector3fc forward = camera.forwardVector();
		if (offset.x * forward.x() + offset.y * forward.y() + offset.z * forward.z() < 0.1) {
			return null;
		}
		Vec3 ndc = minecraft.gameRenderer.projectPointToScreen(point);
		return new Vector2f((float) ((ndc.x + 1.0) * 0.5 * graphics.guiWidth()), (float) ((1.0 - ndc.y) * 0.5 * graphics.guiHeight()));
	}

	/** Whether a point is on screen, at least {@code margin} in from the edges. */
	private static boolean onScreen(GuiGraphicsExtractor graphics, Vector2f at, int margin) {
		return at != null && at.x >= margin && at.x <= graphics.guiWidth() - margin && at.y >= margin && at.y <= graphics.guiHeight() - margin;
	}

	/** Which way a point lies from the middle of the screen: 0 straight up, clockwise, in radians. Behind counts as down. */
	private static float screenAngle(Vec3 point) {
		Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
		Vec3 offset = point.subtract(camera.position());
		Vector3fc left = camera.leftVector();
		Vector3fc up = camera.upVector();
		Vector3fc forward = camera.forwardVector();
		double x = -(offset.x * left.x() + offset.y * left.y() + offset.z * left.z());
		double y = offset.x * up.x() + offset.y * up.y() + offset.z * up.z();
		double depth = offset.x * forward.x() + offset.y * forward.y() + offset.z * forward.z();
		if (depth < 0.0) {
			y -= Math.abs(depth);
		}
		return (float) Math.atan2(x, y);
	}

	private static String distance(double blocks) {
		return blocks >= 1000.0 ? String.format(Locale.ROOT, "%.1fkm", blocks / 1000.0) : (int) blocks + "m";
	}

	/** A marker over a spot in the world, or an arrow on the screen's edge pointing towards it. */
	private static void waypoint(GuiGraphicsExtractor graphics, Font font, Vec3 point, int color, String label) {
		String text = label + " " + distance(Minecraft.getInstance().gameRenderer.mainCamera().position().distanceTo(point));
		Vector2f at = project(graphics, point);
		int width = graphics.guiWidth(), height = graphics.guiHeight();
		if (onScreen(graphics, at, EDGE_MARGIN)) {
			diamond(graphics, at.x, at.y, 4, argb(0xFF, color));
			diamond(graphics, at.x, at.y, 6, argb(0x50, color));
			graphics.centeredText(font, text, Math.round(at.x), Math.round(at.y) - 16, argb(0xFF, color));
			return;
		}
		float angle = screenAngle(point);
		float x = width / 2.0f + Mth.sin(angle) * (width / 2.0f - EDGE_MARGIN);
		float y = height / 2.0f - Mth.cos(angle) * (height / 2.0f - EDGE_MARGIN);
		chevron(graphics, x, y, angle, 6, argb(0xFF, color));
		int textX = Mth.clamp(Math.round(x - Mth.sin(angle) * 16), font.width(text) / 2 + 2, width - font.width(text) / 2 - 2);
		int textY = Mth.clamp(Math.round(y + Mth.cos(angle) * 14) - 4, 2, height - 11);
		graphics.centeredText(font, text, textX, textY, argb(0xE0, color));
	}

	private static void diamond(GuiGraphicsExtractor graphics, float x, float y, float r, int color) {
		NeuralStyle.line(graphics, x, y - r, x + r, y, color);
		NeuralStyle.line(graphics, x + r, y, x, y + r, color);
		NeuralStyle.line(graphics, x, y + r, x - r, y, color);
		NeuralStyle.line(graphics, x - r, y, x, y - r, color);
	}

	/** An arrowhead with its tip at (x, y), pointing along {@code angle} (0 up, clockwise). */
	private static void chevron(GuiGraphicsExtractor graphics, float x, float y, float angle, float size, int color) {
		for (float side : new float[] {-0.6f, 0.6f}) {
			float back = angle + Mth.PI + side;
			NeuralStyle.line(graphics, x, y, x + Mth.sin(back) * size, y - Mth.cos(back) * size, color);
		}
	}

	// ------------------------------------------------------------------------------------------------ modules

	private static void thoughts(GuiGraphicsExtractor graphics, Minecraft minecraft, float partialTick) {
		Vec3 camera = minecraft.gameRenderer.mainCamera().position();
		record Bubble(Vector2f at, double distance, Component text) {
		}
		List<Bubble> bubbles = new ArrayList<>();
		for (Map.Entry<Integer, Component> entry : NeuralClient.thoughts.entrySet()) {
			Entity entity = minecraft.level.getEntity(entry.getKey());
			if (!(entity instanceof LivingEntity) || !entity.isAlive() || entity == minecraft.getCameraEntity()) {
				continue;
			}
			Vec3 head = entity.getPosition(partialTick).add(0.0, entity.getBbHeight() + 0.6, 0.0);
			double distance = head.distanceTo(camera);
			Vector2f at = project(graphics, head);
			if (at != null && distance <= MindReader.RANGE + 4.0) {
				bubbles.add(new Bubble(at, distance, entry.getValue()));
			}
		}
		// Far ones first, so near ones are drawn over them.
		bubbles.sort(Comparator.comparingDouble(Bubble::distance).reversed());
		int accent = NeuralinkModule.MIND_READER.color();
		Font font = minecraft.font;
		for (Bubble bubble : bubbles) {
			float scale = Mth.clamp(1.15f - (float) bubble.distance() / 22.0f, 0.5f, 1.0f);
			int alpha = (int) (255 * Mth.clamp(1.0f - ((float) bubble.distance() - 12.0f) / 8.0f, 0.25f, 1.0f));
			int width = font.width(bubble.text());
			graphics.pose().pushMatrix();
			graphics.pose().translate(bubble.at().x, bubble.at().y);
			graphics.pose().scale(scale, scale);
			int x0 = -width / 2 - 4, x1 = width / 2 + 4, y0 = -18, y1 = -5;
			graphics.fill(x0, y0, x1, y1, argb(alpha * 3 / 4, 0x0A1420));
			graphics.outline(x0, y0, x1 - x0, y1 - y0, argb(alpha / 2, accent));
			graphics.fill(x0, y0, x0 + 3, y0 + 1, argb(alpha, accent));
			graphics.fill(x1 - 3, y1 - 1, x1, y1, argb(alpha, accent));
			// The trail of little bubbles down to the head.
			graphics.fill(-2, -3, 1, 0, argb(alpha * 3 / 4, accent));
			graphics.fill(1, 1, 3, 3, argb(alpha / 2, accent));
			graphics.text(font, bubble.text(), -width / 2, y0 + 3, argb(alpha, TEXT), false);
			graphics.pose().popMatrix();
		}
	}

	private static void recall(GuiGraphicsExtractor graphics, Minecraft minecraft) {
		Font font = minecraft.font;
		marker(graphics, font, minecraft, NeuralClient.deathPoint, DEATH, Component.translatable("hud.mymod.recall.death").getString());
		marker(graphics, font, minecraft, NeuralClient.respawnPoint, NeuralinkModule.RECALL.color(),
				Component.translatable("hud.mymod.recall.respawn").getString());
	}

	private static void marker(GuiGraphicsExtractor graphics, Font font, Minecraft minecraft, Optional<GlobalPos> point, int color, String label) {
		if (point.isPresent() && point.get().dimension() == minecraft.level.dimension()) {
			waypoint(graphics, font, Vec3.atCenterOf(point.get().pos()).add(0.0, 1.0, 0.0), color, label);
		}
	}

	private static void mates(GuiGraphicsExtractor graphics, Minecraft minecraft, float partialTick) {
		for (NeuralNetworking.Mate mate : NeuralClient.mates) {
			Entity entity = minecraft.level.getEntity(mate.entityId());
			if (entity == minecraft.getCameraEntity()) {
				continue;
			}
			Vec3 at = entity != null ? entity.getPosition(partialTick) : mate.pos();
			waypoint(graphics, minecraft.font, at.add(0.0, 2.3, 0.0), NeuralinkModule.HIVE_MIND.color(), "⬡ " + mate.name());
		}
	}

	private static void smartLink(GuiGraphicsExtractor graphics, Minecraft minecraft, LocalPlayer player) {
		int cx = graphics.guiWidth() / 2, cy = graphics.guiHeight() / 2;
		long age = Util.getMillis() - NeuralClient.hitMillis;
		if (age < HIT_MARKER_MILLIS) {
			float fade = 1.0f - age / (float) HIT_MARKER_MILLIS;
			int color = argb((int) (255 * fade), NeuralClient.hitKill ? 0xFF3B30 : 0xFFFFFF);
			for (int sx = -1; sx <= 1; sx += 2) {
				for (int sy = -1; sy <= 1; sy += 2) {
					NeuralStyle.line(graphics, cx + sx * 4, cy + sy * 4, cx + sx * 8, cy + sy * 8, color);
				}
			}
		}

		ItemStack held = player.getMainHandItem();
		if (!(held.getItem() instanceof GunItem gun) || !minecraft.options.getCameraType().isFirstPerson()) {
			return;
		}
		MagazineContents loaded = held.get(ModComponents.LOADED_MAGAZINE);
		// A launcher holds a single rocket rather than a magazine; a flamethrower's tank reads as a percentage.
		Item rocket = gun instanceof RocketLauncherItem ? RocketLauncherItem.loadedRocket(held) : null;
		int rounds = gun instanceof RocketLauncherItem ? (rocket == null ? 0 : 1) : loaded == null ? 0 : loaded.rounds();
		int capacity = gun instanceof RocketLauncherItem ? 1 : gun.capacity(held);
		boolean tank = gun instanceof FlamethrowerItem;
		Font font = minecraft.font;
		int x = cx + 18, y = cy + 6;
		boolean empty = rounds == 0;
		String count = tank ? Math.round(100.0f * rounds / capacity) + "%" : String.valueOf(rounds);
		int countColor = empty ? argb(Util.getMillis() % 500 < 250 ? 0xFF : 0x60, 0xFF3B30) : argb(0xFF, NeuralinkModule.SMART_LINK.color());
		graphics.text(font, count, x, y, countColor, true);
		if (!tank) {
			graphics.text(font, "/" + capacity, x + font.width(count) + 1, y, DIM, true);
		}
		String mode = Component.translatable(gun instanceof RocketLauncherItem
				? rocket == Launchers.PG7_HOMING_ROCKET ? "hud.mymod.smart_link.homing"
						: rocket == Launchers.PG7_NAPALM_ROCKET ? "hud.mymod.smart_link.napalm" : "hud.mymod.smart_link.rocket"
				: tank ? "hud.mymod.smart_link.fuel" : gun.fireMode(held).translationKey() + ".short").getString().toUpperCase(Locale.ROOT);
		graphics.text(font, empty ? Component.translatable("hud.mymod.smart_link.reload").getString() : mode, x, y + 10,
				empty ? argb(0xFF, 0xFF3B30) : DIM, true);
		// A thin gauge of the magazine under it all.
		int gauge = 30;
		graphics.fill(x, y + 20, x + gauge, y + 21, argb(0x50, CYAN));
		graphics.fill(x, y + 20, x + Math.round(gauge * rounds / (float) Math.max(1, capacity)), y + 21,
				argb(0xFF, NeuralinkModule.SMART_LINK.color()));
	}

	private static void vitals(GuiGraphicsExtractor graphics, Minecraft minecraft, LocalPlayer player) {
		Font font = minecraft.font;
		List<MobEffectInstance> effects = new ArrayList<>(player.getActiveEffects());
		effects.removeIf(effect -> !effect.showIcon());
		effects.sort(Comparator.comparingInt(MobEffectInstance::getDuration));
		boolean underwater = player.getAirSupply() < player.getMaxAirSupply();
		int lines = Math.min(MAX_EFFECTS, effects.size());
		int x0 = 6, y0 = 6, x1 = x0 + 124;
		int y1 = y0 + 34 + (underwater ? 9 : 0) + lines * 9 + (NeuralClient.threats.isEmpty() ? 0 : 10);
		int accent = NeuralinkModule.BIO_MONITOR.color();
		NeuralStyle.panel(graphics, x0, y0, x1, y1, accent);

		// Header, with a heart monitor trace that races when hurt or hunted.
		float health = player.getHealth() / Math.max(1.0f, player.getMaxHealth());
		int bpm = Math.round(68 + (1.0f - health) * 50 + NeuralClient.threats.size() * 14);
		graphics.text(font, Component.translatable("hud.mymod.bio_monitor").getString(), x0 + 5, y0 + 4, argb(0xFF, accent), false);
		String beat = bpm + " BPM";
		graphics.text(font, beat, x1 - 5 - font.width(beat), y0 + 4, DIM, false);
		ecg(graphics, x1 - 9 - font.width(beat) - 30, y0 + 3, 28, bpm, argb(0xFF, accent));

		int y = y0 + 15;
		bar(graphics, font, x0 + 5, y, x1 - 5, "HP", health, String.format(Locale.ROOT, "%.0f/%.0f", player.getHealth(), player.getMaxHealth()), HP);
		y += 9;
		bar(graphics, font, x0 + 5, y, x1 - 5, "FD", player.getFoodData().getFoodLevel() / 20.0f,
				String.format(Locale.ROOT, "%d +%.1f", player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel()), FOOD);
		y += 9;
		if (underwater) {
			bar(graphics, font, x0 + 5, y, x1 - 5, "O2", Math.max(0, player.getAirSupply()) / (float) player.getMaxAirSupply(), "", AIR);
			y += 9;
		}
		float tickrate = minecraft.level.tickRateManager().tickrate();
		for (MobEffectInstance effect : effects.subList(0, lines)) {
			int amplifier = Math.min(effect.getAmplifier(), LEVELS.length - 1);
			String name = effect.getEffect().value().getDisplayName().getString() + LEVELS[amplifier];
			String time = MobEffectUtil.formatDuration(effect, 1.0f, tickrate).getString();
			int color = argb(0xFF, effect.getEffect().value().getColor());
			graphics.fill(x0 + 5, y + 2, x0 + 8, y + 5, color);
			graphics.text(font, name, x0 + 11, y, TEXT, false);
			graphics.text(font, time, x1 - 5 - font.width(time), y, DIM, false);
			y += 9;
		}
		if (!NeuralClient.threats.isEmpty()) {
			y += 1;
			String threats = Component.translatable("hud.mymod.bio_monitor.threats", NeuralClient.threats.size()).getString();
			int color = argb(Util.getMillis() % 800 < 400 ? 0xFF : 0x90, THREAT);
			graphics.text(font, "⚠ " + threats, x0 + 5, y, color, false);
		}
	}

	/** A labelled meter: label, a bar filled to {@code fraction}, and a readout. */
	private static void bar(GuiGraphicsExtractor graphics, Font font, int x0, int y, int x1, String label, float fraction, String value, int color) {
		graphics.text(font, label, x0, y, DIM, false);
		int barX0 = x0 + 16, barX1 = x1 - 44;
		graphics.fill(barX0, y + 3, barX1, y + 6, argb(0x40, color));
		graphics.fill(barX0, y + 3, barX0 + Math.round((barX1 - barX0) * Mth.clamp(fraction, 0.0f, 1.0f)), y + 6, argb(0xFF, color));
		graphics.text(font, value, x1 - font.width(value), y, TEXT, false);
	}

	/** A scrolling heartbeat trace, {@code width} wide and 7 tall. */
	private static void ecg(GuiGraphicsExtractor graphics, int x, int y, int width, int bpm, int color) {
		float beatMillis = 60000.0f / bpm;
		long now = Util.getMillis();
		float lastX = x, lastY = y + 4;
		for (int i = 0; i <= width; i++) {
			// Time at this column: the trace scrolls left, a beat's spike every beatMillis.
			float phase = ((now - (width - i) * 18L) % (long) beatMillis) / beatMillis;
			float offset = phase < 0.06f ? -3.0f * Mth.sin(phase / 0.06f * Mth.PI) : phase < 0.12f ? 2.0f * Mth.sin((phase - 0.06f) / 0.06f * Mth.PI) : 0.0f;
			float px = x + i, py = y + 4 + offset;
			NeuralStyle.line(graphics, lastX, lastY, px, py, argb(0x40 + 0xBF * i / width, color));
			lastX = px;
			lastY = py;
		}
	}

	/** Red chevrons round the crosshair towards whatever's hunting the wearer from out of view. */
	private static void threats(GuiGraphicsExtractor graphics, Minecraft minecraft, float partialTick) {
		int cx = graphics.guiWidth() / 2, cy = graphics.guiHeight() / 2;
		float pulse = NeuralStyle.wave(Util.getMillis(), 600.0f, 0.0f);
		for (int id : NeuralClient.threats) {
			Entity entity = minecraft.level.getEntity(id);
			if (entity == null) {
				continue;
			}
			Vec3 at = entity.getPosition(partialTick).add(0.0, entity.getBbHeight() * 0.6, 0.0);
			if (onScreen(graphics, project(graphics, at), 0)) {
				continue;
			}
			float angle = screenAngle(at);
			float radius = 30.0f + 2.0f * pulse;
			float x = cx + Mth.sin(angle) * radius, y = cy - Mth.cos(angle) * radius;
			int color = argb((int) (0x90 + 0x6F * pulse), THREAT);
			chevron(graphics, x, y, angle, 6, color);
			chevron(graphics, x - Mth.sin(angle) * 3, y + Mth.cos(angle) * 3, angle, 5, argb(0x60, THREAT));
		}
	}
}
