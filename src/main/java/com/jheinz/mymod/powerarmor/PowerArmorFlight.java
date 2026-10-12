package com.jheinz.mymod.powerarmor;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Input;

/**
 * The server side of the thrusters. The wearer's client engages them (a double-tap of jump) and flies the
 * player itself, like a powered elytra, as it does all player movement; the server decides whether they may, keeps
 * the mode everyone sees in sync, burns the fuel and turns gravity off for the wearer while they're lit (which also
 * keeps the server from kicking them for floating).
 *
 * <p>The mode comes from the keys the client reports: forward cruises along the look, forward with sprint boosts,
 * and no forward hovers. Landing (on the ground a few ticks without thrusting forward), water, an empty core or an
 * overheat cuts them out. They can burn for the torso's {@link PowerArmorStats#maxBurnTicks()} at a stretch; past
 * that they overheat and stay locked until they've cooled to {@link #RELIGHT_BURN} of it.
 */
public final class PowerArmorFlight {
	// Fuel a tick, before the torso's efficiency.
	static final float HOVER_DRAIN = 12.0f;
	static final float CRUISE_DRAIN = 16.0f;
	static final float BOOST_DRAIN = 28.0f;
	private static final int COOL_RATE = 3;
	private static final float RELIGHT_BURN = 0.3f;
	private static final int LANDED_TICKS = 4;

	private PowerArmorFlight() {
	}

	/** The client double-tapped jump: light the thrusters, or cut them if they're lit. */
	static void toggle(ServerPlayer player, boolean on) {
		PowerArmor.State state = PowerArmor.state(player);
		PowerArmorMotion motion = PowerArmor.motion(player);
		if (state == null || motion.inSequence()) {
			return;
		}
		if (!on) {
			disengage(player, false);
			return;
		}
		if (motion.flying()) {
			return;
		}
		Component refusal = !PowerArmor.hasFuel(state) ? Component.translatable("power_armor.mymod.thrusters.no_fuel")
				: state.overheated ? Component.translatable("power_armor.mymod.thrusters.overheated")
				: player.isInWater() ? Component.translatable("power_armor.mymod.thrusters.water")
				: player.isPassenger() ? Component.translatable("power_armor.mymod.thrusters.riding") : null;
		if (refusal != null) {
			player.sendOverlayMessage(refusal.copy().withStyle(ChatFormatting.RED));
			return;
		}
		state.groundTicks = 0;
		player.setAttached(PowerArmors.MOTION, motion.withThrusters(PowerArmorMotion.HOVER));
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.9f, 0.7f);
	}

	/** Cuts the thrusters, if they're lit. */
	static void disengage(ServerPlayer player, boolean sputter) {
		PowerArmorMotion motion = PowerArmor.motion(player);
		if (!motion.flying()) {
			return;
		}
		player.setAttached(PowerArmors.MOTION, motion.withThrusters(PowerArmorMotion.OFF));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS,
				sputter ? 1.0f : 0.5f, sputter ? 0.6f : 1.2f);
	}

	static void tick(ServerPlayer player, PowerArmor.State state, PowerArmorStats stats) {
		PowerArmorMotion motion = PowerArmor.motion(player);
		if (!motion.flying()) {
			state.burn = Math.max(0, state.burn - COOL_RATE);
			if (state.overheated && state.burn <= stats.maxBurnTicks() * RELIGHT_BURN) {
				state.overheated = false;
				player.sendOverlayMessage(Component.translatable("power_armor.mymod.thrusters.cooled").withStyle(ChatFormatting.GOLD));
			}
			return;
		}
		if (!PowerArmor.hasFuel(state)) {
			disengage(player, true);
			player.sendOverlayMessage(Component.translatable("power_armor.mymod.thrusters.no_fuel").withStyle(ChatFormatting.RED));
			return;
		}
		if (player.isInWater() || player.isPassenger() || player.isFallFlying()) {
			disengage(player, true);
			return;
		}
		Input input = player.getLastClientInput();
		if (player.onGround() && !input.forward()) {
			if (++state.groundTicks >= LANDED_TICKS) {
				disengage(player, false);
				return;
			}
		} else {
			state.groundTicks = 0;
		}
		int mode = !input.forward() ? PowerArmorMotion.HOVER : input.sprint() ? PowerArmorMotion.BOOST : PowerArmorMotion.CRUISE;
		if (mode != motion.thrusters()) {
			player.setAttached(PowerArmors.MOTION, motion.withThrusters(mode));
		}
		PowerArmor.drain(player, state, switch (mode) {
			case PowerArmorMotion.CRUISE -> CRUISE_DRAIN;
			case PowerArmorMotion.BOOST -> BOOST_DRAIN;
			default -> HOVER_DRAIN;
		});
		// Thrust holds them up, so nothing builds up to land on.
		player.resetFallDistance();
		if (++state.burn >= stats.maxBurnTicks()) {
			state.overheated = true;
			disengage(player, true);
			player.sendOverlayMessage(Component.translatable("power_armor.mymod.thrusters.overheated").withStyle(ChatFormatting.RED));
		}
	}
}
