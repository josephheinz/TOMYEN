package com.jheinz.mymod.neuralink;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * The server side of the modules: night vision. (ESP and x-ray only draw on the wearer's screen.) The night
 * vision is ambient and hidden, and kept above the 10 seconds where it starts to flicker.
 */
public final class NeuralinkEffects {
	private static final int INTERVAL = 20;
	private static final int NIGHT_VISION_TICKS = 260;

	private NeuralinkEffects() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % INTERVAL != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
				// A potion's night vision isn't ambient; leave that alone.
				boolean ours = current != null && current.isAmbient() && current.getDuration() <= NIGHT_VISION_TICKS;
				if (NeuralinkItem.isActive(player, NeuralinkModule.NIGHT_VISION)) {
					if (current == null || ours) {
						player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, NIGHT_VISION_TICKS, 0, true, false, false));
					}
				} else if (ours) {
					player.removeEffect(MobEffects.NIGHT_VISION);
				}
			}
		});
	}
}
