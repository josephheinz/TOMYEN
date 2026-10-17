package com.jheinz.tomyen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Night vision from worn gear (the Neuralink's module, the TV Helmet, a full set of Star Steel), handed out in one
 * place so the sources can't fight over it.
 * <p>
 * Each source says when it wants night vision; every tick a player gets it if any source does, and loses it once none
 * do. The gear's effect is ambient with no particles, lasts {@value #DURATION} ticks and is topped up before it drops to
 * {@value #TOP_UP}, well clear of the last 200 ticks where vanilla makes night vision flicker. A potion's night vision
 * (not ambient, or longer than ours) is left alone, except that gear takes it over as it runs low so it doesn't
 * flicker out either.
 * <p>
 * (Each source used to treat any short ambient night vision as its own, so taking off a TV Helmet stripped the
 * Neuralink's every tick and it flashed.)
 */
public final class GearNightVision {
	private static final int DURATION = 400;
	private static final int TOP_UP = 300;

	private record Source(Predicate<ServerPlayer> wants, boolean showIcon) {
	}

	private static final List<Source> SOURCES = new ArrayList<>();

	private GearNightVision() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(GearNightVision::tick);
	}

	/** Adds gear that gives night vision whenever {@code wants} holds, with the effect's icon shown or not. */
	public static void source(Predicate<ServerPlayer> wants, boolean showIcon) {
		SOURCES.add(new Source(wants, showIcon));
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			update(player);
		}
	}

	private static void update(ServerPlayer player) {
		boolean wanted = false;
		boolean showIcon = false;
		for (Source source : SOURCES) {
			if (source.wants().test(player)) {
				wanted = true;
				showIcon |= source.showIcon();
			}
		}
		MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
		boolean ours = isOurs(current);
		if (wanted) {
			if (current == null || current.endsWithin(TOP_UP) || ours && current.showIcon() != showIcon) {
				player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, DURATION, 0, true, false, showIcon));
			}
		} else if (ours) {
			player.removeEffect(MobEffects.NIGHT_VISION);
		}
	}

	/** Whether this is the gear's night vision: ambient and no longer than it gives. */
	private static boolean isOurs(MobEffectInstance vision) {
		return vision != null && vision.isAmbient() && !vision.isInfiniteDuration() && vision.getDuration() <= DURATION;
	}
}
