package com.jheinz.tomyen.client.soul;

import java.util.HashMap;
import java.util.Map;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties;
import net.minecraft.world.entity.player.Player;

import com.jheinz.tomyen.MyMod;

/** The Soul Vacuum on the client: its canister-fill model property and the motor whine of everyone using one nearby. */
public final class SoulVacuumClient {
	private static final Map<Integer, SoulVacuumSound> MOTORS = new HashMap<>();

	private SoulVacuumClient() {
	}

	public static void initialize() {
		RangeSelectItemModelProperties.ID_MAPPER.put(MyMod.id("soul_fill"), SoulFillProperty.MAP_CODEC);
		ClientTickEvents.END_CLIENT_TICK.register(SoulVacuumClient::tick);
	}

	private static void tick(Minecraft client) {
		if (client.level == null || client.player == null) {
			MOTORS.clear();
			return;
		}
		MOTORS.values().removeIf(SoulVacuumSound::isStopped);
		for (Player player : client.level.players()) {
			if (SoulVacuumSound.sucking(player) && player.distanceTo(client.player) < SoulVacuumSound.RANGE) {
				MOTORS.computeIfAbsent(player.getId(), id -> {
					SoulVacuumSound sound = new SoulVacuumSound(player);
					client.getSoundManager().play(sound);
					return sound;
				});
			}
		}
	}
}
