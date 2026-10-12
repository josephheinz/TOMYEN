package com.jheinz.mymod.neuralink;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * Links every Hive Mind wearer within {@link #RANGE} blocks of each other (in the same dimension). Each one is
 * told twice a second where the others are, and what the others' surroundings hold: every mob and player
 * within {@link NeuralinkModule#ESP_RANGE} of a teammate, up to {@link #MAX_CONTACTS}.
 *
 * <p>Teammates show up as markers on the HUD with Hive Mind alone. The shared contacts are drawn by ESP, so
 * seeing them needs an ESP module too; without one, the wearer still shares what's around them.
 */
public final class HiveMind {
	public static final double RANGE = 160.0;
	public static final int MAX_CONTACTS = 96;
	private static final int INTERVAL = 10;

	/** Wearers who were last sent something, so they get one empty update when the link drops. */
	private static final Set<UUID> LINKED = new HashSet<>();

	private HiveMind() {
	}

	static void tick(MinecraftServer server) {
		if ((server.getTickCount() + 7) % INTERVAL != 0) {
			return;
		}
		List<ServerPlayer> wearers = new ArrayList<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isSpectator() && NeuralinkItem.isActive(player, NeuralinkModule.HIVE_MIND)) {
				wearers.add(player);
			}
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!wearers.contains(player)) {
				if (LINKED.remove(player.getUUID())) {
					ServerPlayNetworking.send(player, new NeuralNetworking.Hive(List.of(), List.of()));
				}
				continue;
			}
			List<NeuralNetworking.Mate> mates = new ArrayList<>();
			List<NeuralNetworking.Contact> contacts = new ArrayList<>();
			Set<Entity> seen = new HashSet<>();
			for (ServerPlayer mate : wearers) {
				if (mate == player || mate.level() != player.level() || mate.distanceTo(player) > RANGE) {
					continue;
				}
				mates.add(new NeuralNetworking.Mate(mate.getId(), mate.getGameProfile().name(), mate.position()));
				double reach = NeuralinkModule.ESP_RANGE;
				for (Entity entity : mate.level().getEntities(mate, mate.getBoundingBox().inflate(reach),
						entity -> (entity instanceof Mob || entity instanceof Player) && entity.isAlive() && entity != player
								&& !entity.isSpectator() && entity.distanceToSqr(mate) <= reach * reach)) {
					if (contacts.size() >= MAX_CONTACTS) {
						break;
					}
					if (seen.add(entity)) {
						int kind = entity instanceof Player ? NeuralNetworking.Contact.PLAYER
								: entity instanceof Enemy ? NeuralNetworking.Contact.HOSTILE : NeuralNetworking.Contact.PASSIVE;
						contacts.add(new NeuralNetworking.Contact(entity.position(), entity.getBbWidth(), entity.getBbHeight(), kind));
					}
				}
			}
			if (!mates.isEmpty() || LINKED.contains(player.getUUID())) {
				ServerPlayNetworking.send(player, new NeuralNetworking.Hive(mates, contacts));
			}
			if (mates.isEmpty()) {
				LINKED.remove(player.getUUID());
			} else {
				LINKED.add(player.getUUID());
			}
		}
	}
}
