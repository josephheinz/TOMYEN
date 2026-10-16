package com.jheinz.tomyen.client.detector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

import com.jheinz.tomyen.alien.Ufo;
import com.jheinz.tomyen.detector.Detectors;

/**
 * The UFO Radar. Saucers are tracked by clients from 256 blocks off, so the radar reads them straight from the
 * client's own level every few ticks: no server help needed. While one's carried anywhere in the inventory it keeps
 * watch: a new saucer within {@value Detectors#UFO_RADAR_RANGE} blocks pings and says where, and one starting to
 * abduct or land sounds the alarm. Held, it shows the sweep (see {@link DetectorHud}): heading-up, one turn every
 * {@value #SWEEP_TICKS} ticks, each blip lit as the sweep passes it and fading after, with a faint ping each time.
 *
 * <p>Why handheld rather than a placed alarm block: saucers roam round players and their landings are about a
 * player, so a radar that goes where you go covers every visit, and keeping watch from the inventory is the alarm.
 */
final class UfoRadarClient {
	record Contact(int id, double x, double z, double y, int state, boolean beaming) {
		boolean landing() {
			return state == Ufo.LANDING || state == Ufo.LANDED;
		}
	}

	static final int NONE = 0, LANDING = 1, ABDUCTING = 2;
	static final int SWEEP_TICKS = 60;
	private static final int UPDATE_TICKS = 4;
	/** Between alarms while the same alert goes on. */
	private static final int ALARM_REPEAT = 200;

	private static List<Contact> contacts = List.of();
	private static final Map<Integer, Long> SWEPT = new HashMap<>();
	private static final Set<Integer> KNOWN = new HashSet<>();
	private static int alert;
	private static int alarmIn;
	private static boolean carried;
	private static float previousSweep;

	private UfoRadarClient() {
	}

	static void reset() {
		contacts = List.of();
		SWEPT.clear();
		KNOWN.clear();
		alert = NONE;
		carried = false;
	}

	static void tick(Minecraft minecraft, LocalPlayer player) {
		if (player.tickCount % UPDATE_TICKS == 0) {
			carried = player.getInventory().contains(stack -> stack.is(Detectors.UFO_RADAR));
			if (!carried) {
				reset();
				return;
			}
			update(minecraft, player);
		}
		if (!carried) {
			return;
		}
		boolean held = !DetectorClient.held(player, Detectors.UFO_RADAR).isEmpty();
		float sweep = sweep(0.0f);
		long now = player.level().getGameTime();
		for (Contact contact : contacts) {
			float at = bearing(player, contact);
			if (passed(previousSweep, sweep, at)) {
				SWEPT.put(contact.id, now);
				if (held) {
					DetectorClient.play(minecraft, player, Detectors.RADAR_SWEEP, 0.25f, contact.beaming || contact.landing() ? 1.6f : 1.2f);
				}
			}
		}
		previousSweep = sweep;
		if (alert != NONE && --alarmIn <= 0) {
			alarmIn = ALARM_REPEAT;
			DetectorClient.play(minecraft, player, Detectors.RADAR_ALARM, 0.7f, 1.0f);
		}
	}

	private static void update(Minecraft minecraft, LocalPlayer player) {
		double range = Detectors.UFO_RADAR_RANGE;
		List<Contact> found = new ArrayList<>();
		for (Ufo ufo : minecraft.level.getEntitiesOfClass(Ufo.class, new AABB(player.blockPosition()).inflate(range, 320.0, range),
				ufo -> ufo.isAlive() && !ufo.isCrashing())) {
			if (DetectorClient.flatDistance(player, ufo.getX(), ufo.getZ()) <= range) {
				found.add(new Contact(ufo.getId(), ufo.getX(), ufo.getZ(), ufo.getY(), ufo.state(), ufo.isBeaming()));
			}
		}
		contacts = found;
		SWEPT.keySet().removeIf(id -> found.stream().noneMatch(contact -> contact.id == id));
		int newAlert = NONE;
		Contact worst = null;
		for (Contact contact : found) {
			int level = contact.beaming ? ABDUCTING : contact.landing() ? LANDING : NONE;
			if (level > newAlert) {
				newAlert = level;
				worst = contact;
			}
			if (KNOWN.add(contact.id) && level == NONE) {
				DetectorClient.play(minecraft, player, Detectors.RADAR_CONTACT, 0.6f, 1.0f);
				minecraft.gui.hud.setOverlayMessage(Component.translatable("detector.tomyen.radar.new_contact", distance(player, contact),
						DetectorClient.compass(contact.x - player.getX(), contact.z - player.getZ())).withStyle(ChatFormatting.GREEN), false);
			}
		}
		KNOWN.removeIf(id -> found.stream().noneMatch(contact -> contact.id == id));
		if (newAlert > alert && worst != null) {
			alarmIn = 0;
			minecraft.gui.hud.setOverlayMessage(alertText(player, newAlert, worst), false);
		}
		alert = newAlert;
	}

	static Component alertText(LocalPlayer player, int alert, Contact contact) {
		return Component.translatable(alert == ABDUCTING ? "detector.tomyen.radar.abducting" : "detector.tomyen.radar.landing",
				distance(player, contact), DetectorClient.compass(contact.x - player.getX(), contact.z - player.getZ()))
				.withStyle(alert == ABDUCTING ? ChatFormatting.RED : ChatFormatting.GOLD);
	}

	static int distance(LocalPlayer player, Contact contact) {
		return (int) Math.round(DetectorClient.flatDistance(player, contact.x, contact.z));
	}

	/** Whether the sweep went past this bearing between two ticks (all in degrees, 0 to 360). */
	private static boolean passed(float from, float to, float at) {
		return from <= to ? at > from && at <= to : at > from || at <= to;
	}

	/** Where the sweep is, in degrees clockwise from straight up (ahead), 0 to 360. */
	static float sweep(float partialTicks) {
		Minecraft minecraft = Minecraft.getInstance();
		float time = minecraft.level == null ? 0.0f : minecraft.level.getGameTime() % SWEEP_TICKS + partialTicks;
		return time / SWEEP_TICKS * 360.0f % 360.0f;
	}

	/** A contact's bearing on the heading-up screen, degrees clockwise from ahead, 0 to 360. */
	static float bearing(LocalPlayer player, Contact contact) {
		return Mth.positiveModulo(DetectorClient.relativeYaw(player, contact.x, contact.z), 360.0f);
	}

	/** How lit a contact's blip is: 1 just as the sweep passes, fading over one turn. */
	static float glow(Contact contact, float partialTicks) {
		Long swept = SWEPT.get(contact.id);
		Minecraft minecraft = Minecraft.getInstance();
		if (swept == null || minecraft.level == null) {
			return 0.0f;
		}
		float age = minecraft.level.getGameTime() - swept + partialTicks;
		return Mth.clamp(1.0f - age / SWEEP_TICKS, 0.0f, 1.0f);
	}

	static List<Contact> contacts() {
		return contacts;
	}

	static int alert() {
		return alert;
	}

	/** The contact behind the alert, if any. */
	static @Nullable Contact alertContact() {
		for (Contact contact : contacts) {
			if (alert == ABDUCTING ? contact.beaming : alert == LANDING && contact.landing()) {
				return contact;
			}
		}
		return null;
	}
}
