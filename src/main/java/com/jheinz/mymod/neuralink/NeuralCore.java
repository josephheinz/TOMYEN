package com.jheinz.mymod.neuralink;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import com.jheinz.mymod.ModDamageTypes;

/**
 * The server side of the active modules and the Neural Heat they share. The client reports presses and releases
 * of the Neural Trigger; a press fires a one-shot module or starts a channelled one, which then runs every tick
 * until the key is released, its module goes away, or the wearer overheats.
 *
 * <p>Every use adds heat ({@link NeuralinkModule#heat()}), which bleeds off at {@link #COOL_RATE} a tick once
 * nothing has added any for a second. Reaching {@link #MAX_HEAT} overheats the implant: it burns the wearer for
 * {@link #OVERHEAT_DAMAGE}, makes them sick, and locks the actives out until heat falls back below
 * {@link #RECOVERED_HEAT}. The state is synced to the wearer's client for the HUD whenever it changes.
 */
public final class NeuralCore {
	public static final float MAX_HEAT = 100.0f;
	/** After an overheat the actives stay locked until heat falls below this. */
	public static final float RECOVERED_HEAT = 35.0f;
	/** Heat starts to bleed off this long after it last rose. */
	private static final int COOL_DELAY = 20;
	private static final float COOL_RATE = 0.75f;
	/** A burnt-out implant cools slower: about six seconds of lockout. */
	private static final float OVERHEATED_COOL_RATE = 0.5f;
	private static final float OVERHEAT_DAMAGE = 6.0f;
	private static final int OVERHEAT_NAUSEA_TICKS = 160;

	/** What an active module does when fired. */
	interface Ability {
		/** Fires a one-shot or begins a channel. False if it fizzled, so no heat or cooldown is spent. */
		boolean start(ServerPlayer player, State state);

		/** Every tick while a channel is held; false ends it. */
		default boolean channel(ServerPlayer player, State state) {
			return true;
		}

		/** A channel ended: released, or cut off ({@code interrupted}) by an overheat, an EMP or its module going away. */
		default void stop(ServerPlayer player, State state, boolean interrupted) {
		}
	}

	/** Everything tracked per wearer. The channel fields are scratch space for whichever channel is running. */
	static final class State {
		float heat;
		boolean overheated;
		int ticksSinceHeated;
		NeuralinkModule channel;
		final int[] cooldowns = new int[NeuralinkModule.values().length];
		/** The entity the channel is working on (held by telekinesis, stared at by hijack), or -1. */
		int targetId = -1;
		/** Ticks the channel has spent on its current target or block. */
		int progress;
		BlockPos meltPos;
		private NeuralNetworking.Status lastSent;
	}

	private static final Map<NeuralinkModule, Ability> ABILITIES = new EnumMap<>(Map.of(
			NeuralinkModule.OPTIC_LASERS, new OpticLasers(),
			NeuralinkModule.TELEKINESIS, new Telekinesis(),
			NeuralinkModule.BLINK, new Blink(),
			NeuralinkModule.NEURAL_HIJACK, new NeuralHijack(),
			NeuralinkModule.EMP_PULSE, new EmpPulse(),
			NeuralinkModule.OVERCLOCK, new Overclock()));

	private static final Map<UUID, State> STATES = new HashMap<>();

	private NeuralCore() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(NeuralCore::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			State state = STATES.remove(handler.getPlayer().getUUID());
			if (state != null && state.channel != null) {
				endChannel(handler.getPlayer(), state, true);
			}
		});
	}

	/** A press or release of the Neural Trigger on the client, for the module it has selected. */
	static void trigger(ServerPlayer player, NeuralinkModule module, boolean pressed) {
		State state = STATES.computeIfAbsent(player.getUUID(), uuid -> new State());
		if (!pressed) {
			if (state.channel == module) {
				endChannel(player, state, false);
			}
			return;
		}
		if (!module.isActive() || !NeuralinkItem.activeModules(player).contains(module) || !player.isAlive() || player.isSpectator()
				|| state.overheated || state.cooldowns[module.ordinal()] > 0) {
			return;
		}
		if (state.channel != null) {
			endChannel(player, state, true);
		}
		Ability ability = ABILITIES.get(module);
		if (!ability.start(player, state)) {
			return;
		}
		if (module.kind() == NeuralinkModule.Kind.CHANNEL) {
			state.channel = module;
		} else {
			state.cooldowns[module.ordinal()] = module.cooldownTicks();
			addHeat(player, state, module.heat());
		}
	}

	/** Cuts off whatever the player is channelling, e.g. when an EMP hits them. */
	public static void interrupt(ServerPlayer player) {
		State state = STATES.get(player.getUUID());
		if (state != null && state.channel != null) {
			endChannel(player, state, true);
		}
	}

	/** The module this player is channelling right now, or null. */
	public static NeuralinkModule channelling(ServerPlayer player) {
		State state = STATES.get(player.getUUID());
		return state == null ? null : state.channel;
	}

	static void addHeat(ServerPlayer player, State state, float amount) {
		state.heat += amount;
		state.ticksSinceHeated = 0;
		if (state.heat >= MAX_HEAT && !state.overheated) {
			overheat(player, state);
		}
	}

	private static void endChannel(ServerPlayer player, State state, boolean interrupted) {
		NeuralinkModule channel = state.channel;
		state.channel = null;
		ABILITIES.get(channel).stop(player, state, interrupted);
		state.targetId = -1;
		state.progress = 0;
		state.meltPos = null;
	}

	private static void overheat(ServerPlayer player, State state) {
		state.heat = MAX_HEAT;
		state.overheated = true;
		if (state.channel != null) {
			endChannel(player, state, true);
		}
		ServerLevel level = (ServerLevel) player.level();
		player.hurtServer(level, player.damageSources().source(ModDamageTypes.NEURAL_OVERLOAD), OVERHEAT_DAMAGE);
		player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, OVERHEAT_NAUSEA_TICKS, 0, false, false, true));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0f, 0.6f);
		level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getEyeY() + 0.2, player.getZ(), 20, 0.25, 0.15, 0.25, 0.02);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getEyeY() + 0.1, player.getZ(), 12, 0.3, 0.2, 0.3, 0.4);
		ServerPlayNetworking.send(player, new NeuralNetworking.Flash(NeuralNetworking.Flash.OVERHEAT));
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Set<NeuralinkModule> modules = NeuralinkItem.activeModules(player);
			boolean wearsActives = modules.stream().anyMatch(NeuralinkModule::isActive);
			State state = STATES.get(player.getUUID());
			if (state == null) {
				if (!wearsActives) {
					continue;
				}
				state = new State();
				STATES.put(player.getUUID(), state);
			}

			for (int i = 0; i < state.cooldowns.length; i++) {
				state.cooldowns[i] = Math.max(0, state.cooldowns[i] - 1);
			}

			if (state.channel != null) {
				NeuralinkModule channel = state.channel;
				boolean keep = player.isAlive() && !player.isSpectator() && modules.contains(channel)
						&& ABILITIES.get(channel).channel(player, state);
				if (!keep) {
					// The ability may have ended itself already (e.g. a finished hijack).
					if (state.channel == channel) {
						endChannel(player, state, true);
					}
				} else {
					addHeat(player, state, channel.heat());
				}
			}

			state.ticksSinceHeated++;
			if (state.overheated) {
				state.heat -= OVERHEATED_COOL_RATE;
				if (state.heat <= RECOVERED_HEAT) {
					state.overheated = false;
				}
			} else if (state.ticksSinceHeated > COOL_DELAY) {
				state.heat -= COOL_RATE;
			}
			state.heat = Math.max(0.0f, state.heat);

			sync(player, state);
			if (!wearsActives && state.heat == 0.0f && state.channel == null && !state.overheated && !coolingDown(state)) {
				STATES.remove(player.getUUID());
			}
		}
		Telekinesis.tickThrown(server);
		NeuralHijack.tickAllies(server);
		Overclock.tickBulletTime(server);
	}

	private static boolean coolingDown(State state) {
		for (int cooldown : state.cooldowns) {
			if (cooldown > 0) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Sends the HUD what changed. Heat and cooldowns are rounded (to half a point and a quarter second) so cooling
	 * down doesn't send a packet every tick for nothing visible.
	 */
	private static void sync(ServerPlayer player, State state) {
		List<Integer> cooldowns = new ArrayList<>(state.cooldowns.length);
		for (int cooldown : state.cooldowns) {
			cooldowns.add(Mth.positiveCeilDiv(cooldown, 5) * 5);
		}
		float progress = state.channel == NeuralinkModule.NEURAL_HIJACK
				? Mth.clamp(state.progress / (float) NeuralHijack.STARE_TICKS, 0.0f, 1.0f) : 0.0f;
		NeuralNetworking.Status status = new NeuralNetworking.Status(Math.round(state.heat * 2.0f) / 2.0f, state.overheated,
				state.channel == null ? -1 : state.channel.ordinal(), state.targetId, progress, cooldowns);
		if (!status.equals(state.lastSent)) {
			state.lastSent = status;
			ServerPlayNetworking.send(player, status);
		}
	}
}
