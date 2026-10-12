package com.jheinz.mymod.client.neuralink;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import com.jheinz.mymod.neuralink.NeuralNetworking;
import com.jheinz.mymod.neuralink.NeuralinkModule;

/** What the server has told this client about its Neuralink, kept for the HUD and the world renderers. */
public final class NeuralClient {
	/** The last status, or null before the first one (no actives worn yet). */
	public static NeuralNetworking.Status status;
	/** When the last overheat, blink or EMP hit, in milliseconds; 0 for never. */
	public static long overheatMillis;
	public static long blinkMillis;
	public static long empMillis;
	/** When the last hit marker came in, and whether it was a kill. */
	public static long hitMillis;
	public static boolean hitKill;
	public static Map<Integer, Component> thoughts = Map.of();
	public static List<Integer> threats = List.of();
	public static List<NeuralNetworking.Mate> mates = List.of();
	public static List<NeuralNetworking.Contact> contacts = List.of();
	public static Optional<GlobalPos> deathPoint = Optional.empty();
	public static Optional<GlobalPos> respawnPoint = Optional.empty();
	/** Players whose optic lasers are on. */
	public static final Set<Integer> LASERING = new HashSet<>();

	private NeuralClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(NeuralNetworking.Status.TYPE, (payload, context) -> status = payload);
		ClientPlayNetworking.registerGlobalReceiver(NeuralNetworking.Beam.TYPE, (payload, context) -> {
			if (payload.on()) {
				LASERING.add(payload.playerId());
			} else {
				LASERING.remove(payload.playerId());
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(NeuralNetworking.Flash.TYPE, (payload, context) -> {
			long now = Util.getMillis();
			switch (payload.kind()) {
				case NeuralNetworking.Flash.BLINK -> blinkMillis = now;
				case NeuralNetworking.Flash.EMP -> empMillis = now;
				case NeuralNetworking.Flash.OVERHEAT -> overheatMillis = now;
				default -> {
				}
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(NeuralNetworking.HitMarker.TYPE, (payload, context) -> {
			hitMillis = Util.getMillis();
			hitKill = payload.kill();
		});
		ClientPlayNetworking.registerGlobalReceiver(NeuralNetworking.Thoughts.TYPE, (payload, context) -> {
			Map<Integer, Component> map = new HashMap<>();
			payload.thoughts().forEach(thought -> map.put(thought.entityId(), thought.text()));
			thoughts = map;
		});
		ClientPlayNetworking.registerGlobalReceiver(NeuralNetworking.Threats.TYPE, (payload, context) -> threats = payload.entityIds());
		ClientPlayNetworking.registerGlobalReceiver(NeuralNetworking.Hive.TYPE, (payload, context) -> {
			mates = payload.mates();
			contacts = payload.contacts();
		});
		ClientPlayNetworking.registerGlobalReceiver(NeuralNetworking.Recall.TYPE, (payload, context) -> {
			deathPoint = payload.death();
			respawnPoint = payload.respawn();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
	}

	private static void reset() {
		status = null;
		overheatMillis = blinkMillis = empMillis = hitMillis = 0;
		thoughts = Map.of();
		threats = List.of();
		mates = List.of();
		contacts = List.of();
		deathPoint = Optional.empty();
		respawnPoint = Optional.empty();
		LASERING.clear();
	}

	public static float heat() {
		return status == null ? 0.0f : status.heat();
	}

	public static boolean overheated() {
		return status != null && status.overheated();
	}

	/** Ticks until a module can be fired again (to the nearest quarter second). */
	public static int cooldown(NeuralinkModule module) {
		return status == null || module.ordinal() >= status.cooldowns().size() ? 0 : status.cooldowns().get(module.ordinal());
	}

	/** The module being channelled, if any. */
	public static NeuralinkModule channel() {
		return status == null || status.channel() < 0 ? null : NeuralinkModule.values()[status.channel()];
	}
}
