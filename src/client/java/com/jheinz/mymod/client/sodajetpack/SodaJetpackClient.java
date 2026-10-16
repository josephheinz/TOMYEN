package com.jheinz.mymod.client.sodajetpack;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.sodajetpack.JetpackChaos;
import com.jheinz.mymod.sodajetpack.JetpackMode;
import com.jheinz.mymod.sodajetpack.JetpackTank;
import com.jheinz.mymod.sodajetpack.SodaJetpackFlight;
import com.jheinz.mymod.sodajetpack.SodaJetpacks;

/**
 * The jetpack's client side: the worn pack (SodaJetpackRenderer), flying the local player, shaking, everyone's foam
 * and fizz, and the pressure gauge.
 *
 * <p>Flying: hold jump in the air and, while the tank has fuel and pressure (as the server last said, or fuel in the
 * inventory it'll load), each tick pushes the player up by the {@link JetpackMode}'s lift, up to its top climbing
 * speed. It's never steady: a random nudge sideways every tick, a push forward if forward's held, coughs where it
 * gives nothing ({@link JetpackChaos#sputters}) and the odd spin-out that twirls you round and shoves you sideways
 * ({@link JetpackChaos#spins}). The server plays the matching sounds and burns the fuel.
 *
 * <p>Shaking: sneak and hold use with both hands empty, not looking at a block. Each tick of it tells the server
 * ({@link SodaJetpacks.Shake}), and the pack jiggles on everyone's screen.
 */
public final class SodaJetpackClient {
	/** Sideways nudge each thrusting tick, at most, blocks a tick. */
	private static final double WOBBLE = 0.025;
	/** Push along the facing with forward held, blocks a tick. */
	private static final double FORWARD_PUSH = 0.018;
	/** Shove sideways each spinning tick. */
	private static final double SPIN_SHOVE = 0.05;
	/** Fastest the jetpack pushes you along the ground, blocks a tick. */
	private static final double MAX_HORIZONTAL = 0.45;
	private static final int COLA = 0x6E3A1C;
	private static final int LIME = 0x8CD27A;

	private SodaJetpackClient() {
	}

	public static void initialize() {
		SodaJetpackRenderer.initialize();
		ClientTickEvents.START_CLIENT_TICK.register(SodaJetpackClient::fly);
		ClientTickEvents.END_CLIENT_TICK.register(SodaJetpackClient::particles);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, MyMod.id("soda_jetpack"), SodaJetpackClient::gauge);
	}

	/** Before vanilla moves the local player this tick: shaking, then thrust. */
	private static void fly(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null || !SodaJetpacks.isWearing(player)) {
			return;
		}
		boolean free = minecraft.gui.screen() == null;
		boolean shaking = free && player.isShiftKeyDown() && minecraft.options.keyUse.isDown() && SodaJetpackFlight.handsFree(player)
				&& (minecraft.hitResult == null || minecraft.hitResult.getType() != HitResult.Type.BLOCK);
		if (shaking) {
			ClientPlayNetworking.send(SodaJetpacks.Shake.INSTANCE);
			while (minecraft.options.keyUse.consumeClick()) {
				// Shaking, not using.
			}
			return;
		}
		JetpackTank tank = SodaJetpacks.tank(player);
		if (!free || !minecraft.options.keyJump.isDown() || !SodaJetpackFlight.airborne(player) || !tank.pressurised()) {
			return;
		}
		JetpackMode mode = SodaJetpackFlight.predictMode(player, tank);
		if (mode == JetpackMode.NONE) {
			return;
		}
		long time = minecraft.level.getGameTime();
		int id = player.getId();
		Vec3 motion = player.getDeltaMovement();
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		double forwardX = -Mth.sin(yaw), forwardZ = Mth.cos(yaw);
		double x = motion.x, y = motion.y, z = motion.z;
		if (!JetpackChaos.sputters(time, id, mode)) {
			if (y < mode.maxRise) {
				y = Math.min(y + mode.lift, mode.maxRise);
			}
			x += JetpackChaos.wobble(time, id, 0) * WOBBLE;
			z += JetpackChaos.wobble(time, id, 1) * WOBBLE;
			if (minecraft.options.keyUp.isDown()) {
				x += forwardX * FORWARD_PUSH;
				z += forwardZ * FORWARD_PUSH;
			}
		}
		if (JetpackChaos.spins(time, id, mode)) {
			int direction = JetpackChaos.spinDirection(time, id);
			player.setYRot(player.getYRot() + direction * JetpackChaos.SPIN_DEGREES);
			x += -forwardZ * direction * SPIN_SHOVE;
			z += forwardX * direction * SPIN_SHOVE;
		}
		double horizontal = Math.sqrt(x * x + z * z);
		if (horizontal > MAX_HORIZONTAL) {
			x *= MAX_HORIZONTAL / horizontal;
			z *= MAX_HORIZONTAL / horizontal;
		}
		player.setDeltaMovement(x, y, z);
	}

	/** Foam, fizz and drips from every jetpack in the air, and bubbles from any being shaken. */
	private static void particles(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.isPaused()) {
			return;
		}
		RandomSource random = level.getRandom();
		for (Player player : level.players()) {
			int fx = SodaJetpacks.fx(player);
			if (fx == 0) {
				continue;
			}
			JetpackMode mode = JetpackMode.byId(fx >> SodaJetpacks.FX_MODE_SHIFT);
			Vec3 nozzle = nozzle(player);
			if ((fx & SodaJetpacks.FX_THRUST) != 0 && (fx & SodaJetpacks.FX_SPUTTER) == 0) {
				for (int i = 0; i < 3; i++) {
					spray(level, random, nozzle, ParticleTypes.POOF, 0.06, -0.25);
				}
				spray(level, random, nozzle, ParticleTypes.BUBBLE_POP, 0.12, -0.15);
				ParticleOptions drip = mode == JetpackMode.SLIME ? ParticleTypes.ITEM_SLIME
						: new DustParticleOptions(random.nextBoolean() ? COLA : LIME, 1.0f);
				spray(level, random, nozzle, drip, 0.1, -0.3);
				if (mode != JetpackMode.SUGAR && random.nextInt(3) == 0) {
					spray(level, random, nozzle, ParticleTypes.ITEM_SLIME, 0.08, -0.2);
				}
			} else if ((fx & SodaJetpacks.FX_SPUTTER) != 0 && random.nextInt(2) == 0) {
				spray(level, random, nozzle, ParticleTypes.SMOKE, 0.04, -0.05);
			}
			if ((fx & SodaJetpacks.FX_SPIN) != 0) {
				spray(level, random, nozzle, ParticleTypes.SPLASH, 0.3, 0.0);
			}
			if ((fx & SodaJetpacks.FX_SHAKE) != 0) {
				spray(level, random, nozzle, ParticleTypes.BUBBLE_POP, 0.08, 0.05);
				if (random.nextInt(3) == 0) {
					spray(level, random, nozzle, ParticleTypes.POOF, 0.03, 0.02);
				}
			}
		}
	}

	private static void spray(ClientLevel level, RandomSource random, Vec3 at, ParticleOptions particle, double spread, double down) {
		level.addParticle(particle, at.x + (random.nextDouble() - 0.5) * 0.15, at.y, at.z + (random.nextDouble() - 0.5) * 0.15,
				(random.nextDouble() - 0.5) * spread, down * (0.6 + random.nextDouble() * 0.8), (random.nextDouble() - 0.5) * spread);
	}

	/** Where a wearer's funnel is, in the world: low on their back, behind them. */
	private static Vec3 nozzle(Player player) {
		float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
		double back = 5.0 / 16.0;
		double height = player.isCrouching() ? 0.45 : 11.0 / 16.0;
		return new Vec3(player.getX() + Mth.sin(yaw) * back, player.getY() + height, player.getZ() - Mth.cos(yaw) * back);
	}

	/**
	 * The gauge, left of the off-hand slot while the pack's worn: pressure as a bottle filling with fizz (amber when
	 * low, a flashing FLAT and SHAKE! when it's gone), and the sugar and slime in the tank as two thin bars.
	 */
	private static void gauge(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || !SodaJetpacks.isWearing(player) || player.isSpectator()) {
			return;
		}
		JetpackTank tank = SodaJetpacks.tank(player);
		int right = graphics.guiWidth() / 2 - 91 - 32;
		int bottom = graphics.guiHeight() - 3;
		int x = right - 14;
		int top = bottom - 18;
		// The bottle: a neck and cap, then the body filling with fizz.
		graphics.fill(x + 4, top - 4, x + 10, top - 2, 0xFFC42828);
		graphics.fill(x + 5, top - 2, x + 9, top, 0xFF9AA8B0);
		graphics.fill(x, top, x + 14, bottom, 0xFF20262C);
		graphics.outline(x, top, 14, bottom - top, 0xFF9AA8B0);
		int fill = Math.round((bottom - top - 2) * tank.pressure());
		boolean blink = (player.tickCount / 6) % 2 == 0;
		int colour = tank.flat() ? 0xFF6A5040 : tank.pressure() < 0.3f ? 0xFFE0A030 : 0xFF8A4A24;
		graphics.fill(x + 1, bottom - 1 - fill, x + 13, bottom - 1, colour);
		if (fill > 1 && !tank.flat()) {
			graphics.fill(x + 1, bottom - 1 - fill, x + 13, bottom - fill, 0xFFF0E4C8);
		}
		// Sugar (white) and slime (green) beside it.
		int sugar = Math.round((bottom - top) * Math.min(1.0f, tank.sugar() / (float) JetpackTank.CAPACITY));
		int slime = Math.round((bottom - top) * Math.min(1.0f, tank.slime() / (float) JetpackTank.CAPACITY));
		graphics.fill(x + 16, top, x + 18, bottom, 0x60000000);
		graphics.fill(x + 16, bottom - sugar, x + 18, bottom, 0xFFF4F4F0);
		graphics.fill(x + 19, top, x + 21, bottom, 0x60000000);
		graphics.fill(x + 19, bottom - slime, x + 21, bottom, 0xFF7CD060);
		if (tank.flat()) {
			Component label = Component.translatable(blink ? "hud.mymod.soda_jetpack.flat" : "hud.mymod.soda_jetpack.shake");
			graphics.text(minecraft.font, label, x + 7 - minecraft.font.width(label) / 2, top - 14, blink ? 0xFFFF6040 : 0xFFFFD060);
		}
	}
}
