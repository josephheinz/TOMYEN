package com.jheinz.tomyen.neuralink;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

import com.jheinz.tomyen.GearNightVision;
import com.jheinz.tomyen.ModDamageTypes;
import com.jheinz.tomyen.ModEffects;
import com.jheinz.tomyen.launcher.PG7Rocket;
import com.jheinz.tomyen.napalm.Napalm;

/**
 * The server side of the passive modules. (ESP and x-ray only draw on the wearer's screen.)
 * <ul>
 * <li>Night vision: handed out by {@link GearNightVision}, with no icon.
 * <li>Bio-Monitor: bleeding runs out twice as fast (and does half damage, see BleedingEffect), and the wearer is
 * told which mobs within {@link #THREAT_RANGE} are hunting them so the HUD can point out the ones out of view.
 * <li>Recall: the wearer is told their last death and respawn point.
 * <li>Smart-Link: a hit marker for every bullet (or rocket blast) of the wearer's that lands.
 * <li>Mind Reader and Hive Mind: see their own classes.
 * </ul>
 */
public final class NeuralinkEffects {
	private static final int INTERVAL = 20;
	private static final double THREAT_RANGE = 32.0;
	private static final int THREAT_INTERVAL = 10;
	private static final int RECALL_INTERVAL = 40;

	/** What each wearer was last sent, so updates only go out on change. */
	private static final Map<UUID, List<Integer>> SENT_THREATS = new HashMap<>();
	private static final Map<UUID, NeuralNetworking.Recall> SENT_RECALL = new HashMap<>();

	private NeuralinkEffects() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(NeuralinkEffects::tick);
		GearNightVision.source(player -> NeuralinkItem.isActive(player, NeuralinkModule.NIGHT_VISION), false);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> {
			if (!blocked && source.getEntity() instanceof ServerPlayer shooter
					&& (ModDamageTypes.isBullet(source) || source.getDirectEntity() instanceof PG7Rocket || source.is(Napalm.NAPALM))
					&& NeuralinkItem.isActive(shooter, NeuralinkModule.SMART_LINK)) {
				ServerPlayNetworking.send(shooter, new NeuralNetworking.HitMarker(entity.isDeadOrDying()));
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			SENT_THREATS.remove(handler.getPlayer().getUUID());
			SENT_RECALL.remove(handler.getPlayer().getUUID());
		});
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (server.getTickCount() % INTERVAL == 0) {
				slowBleeding(player);
			}
			if (server.getTickCount() % THREAT_INTERVAL == 0) {
				threats(player);
			}
			if (server.getTickCount() % RECALL_INTERVAL == 0) {
				recall(server, player);
			}
			MindReader.tick(player);
		}
		HiveMind.tick(server);
	}

	/** Takes an extra second off a bleed each second, so it runs out twice as fast. */
	private static void slowBleeding(ServerPlayer player) {
		MobEffectInstance bleeding = player.getEffect(ModEffects.BLEEDING);
		if (bleeding == null || bleeding.isInfiniteDuration() || !NeuralinkItem.isActive(player, NeuralinkModule.BIO_MONITOR)) {
			return;
		}
		int remaining = bleeding.getDuration() - INTERVAL;
		player.removeEffect(ModEffects.BLEEDING);
		if (remaining > 0) {
			player.addEffect(new MobEffectInstance(ModEffects.BLEEDING, remaining, bleeding.getAmplifier(), bleeding.isAmbient(),
					bleeding.isVisible(), bleeding.showIcon()));
		}
	}

	private static void threats(ServerPlayer player) {
		List<Integer> threats = new ArrayList<>();
		if (NeuralinkItem.isActive(player, NeuralinkModule.BIO_MONITOR)) {
			for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(THREAT_RANGE), Mob::isAlive)) {
				Optional<?> remembered = mob.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET);
				if (mob.getTarget() == player || remembered != null && remembered.orElse(null) == player) {
					threats.add(mob.getId());
				}
			}
		}
		List<Integer> last = SENT_THREATS.getOrDefault(player.getUUID(), List.of());
		if (!threats.equals(last)) {
			ServerPlayNetworking.send(player, new NeuralNetworking.Threats(threats));
			SENT_THREATS.put(player.getUUID(), threats);
		}
	}

	private static void recall(MinecraftServer server, ServerPlayer player) {
		if (!NeuralinkItem.isActive(player, NeuralinkModule.RECALL)) {
			SENT_RECALL.remove(player.getUUID());
			return;
		}
		ServerPlayer.RespawnConfig config = player.getRespawnConfig();
		GlobalPos respawn = config != null ? config.respawnData().globalPos() : server.getRespawnData().globalPos();
		NeuralNetworking.Recall recall = new NeuralNetworking.Recall(player.getLastDeathLocation(), Optional.of(respawn));
		if (!recall.equals(SENT_RECALL.get(player.getUUID()))) {
			ServerPlayNetworking.send(player, recall);
			SENT_RECALL.put(player.getUUID(), recall);
		}
	}
}
