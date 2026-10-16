package com.jheinz.tomyen.client.napalm;

import java.util.HashMap;
import java.util.Map;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.napalm.FlameStream;
import com.jheinz.tomyen.napalm.FlamethrowerItem;
import com.jheinz.tomyen.napalm.Napalm;

/**
 * Napalm on the client. For everyone holding a flamethrower nearby it keeps the pilot light flickering at the nozzle
 * while there's fuel, and while they spray it draws the stream (flame particles flying the server globs' physics, with
 * heat haze and smoke over it) and plays the roar ({@link FlamethrowerSound}). Also the particles, the thrown grenade's
 * renderer and the tanks on the holder's back ({@link FlamethrowerTanksLayer}).
 */
public final class NapalmClient {
	/** How far away another player's flamethrower is drawn and heard. */
	private static final double RANGE = 64.0;
	/** Flame particles a spray throws each tick. */
	private static final int FLAMES_PER_TICK = 4;
	private static final Map<Integer, FlamethrowerSound> ROARS = new HashMap<>();

	private NapalmClient() {
	}

	public static void initialize() {
		ParticleProviderRegistry.getInstance().register(Napalm.NAPALM_FLAME, NapalmFlameParticle.Provider::new);
		ParticleProviderRegistry.getInstance().register(Napalm.HEAT_HAZE, HeatHazeParticle.Provider::new);
		EntityRendererRegistry.register(Napalm.GRENADE, ThrownItemRenderer::new);
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new FlamethrowerTanksLayer(avatar));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level != null && !client.isPaused()) {
				tick(client, client.level);
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ROARS.clear());
	}

	private static void tick(Minecraft minecraft, ClientLevel level) {
		Vec3 camera = minecraft.gameRenderer.mainCamera().position();
		RandomSource random = level.getRandom();
		long now = level.getGameTime();
		ROARS.values().removeIf(FlamethrowerSound::isStopped);
		for (AbstractClientPlayer player : level.players()) {
			ItemStack held = player.getMainHandItem();
			if (!(held.getItem() instanceof FlamethrowerItem gun) || player.position().distanceToSqr(camera) > RANGE * RANGE) {
				continue;
			}
			Vec3 nozzle = FlamethrowerItem.nozzle(player, 1.0f);
			if (level.getFluidState(BlockPos.containing(nozzle)).is(FluidTags.WATER)) {
				continue;
			}
			boolean fuelled = FlamethrowerItem.fuel(held) > 0 || player.hasInfiniteMaterials();
			if (fuelled && random.nextInt(3) == 0) {
				// The pilot light, a little flicker of flame at the nozzle.
				level.addParticle(ParticleTypes.SMALL_FLAME, nozzle.x, nozzle.y, nozzle.z, 0.0, 0.004 + random.nextDouble() * 0.006, 0.0);
			}
			if (!fuelled || !FlamethrowerItem.firing(held, now)) {
				continue;
			}
			spray(level, player, nozzle, gun.stats(held, player).spreadDegrees(), random);
			if (player.position().distanceTo(camera) < FlamethrowerSound.RANGE) {
				ROARS.computeIfAbsent(player.getId(), id -> {
					FlamethrowerSound roar = new FlamethrowerSound(player);
					minecraft.getSoundManager().play(roar);
					return roar;
				});
			}
		}
	}

	/** One tick's stream, flying as the server's globs do, with haze and smoke rising off it. */
	private static void spray(ClientLevel level, AbstractClientPlayer player, Vec3 nozzle, float spread, RandomSource random) {
		Vec3 direction = FlameStream.aim(player.getEyePosition(), player.getViewVector(1.0f), nozzle);
		for (int i = 0; i < FLAMES_PER_TICK; i++) {
			double speed = FlameStream.SPEED * (0.92 + random.nextDouble() * 0.16);
			Vec3 velocity = FlameStream.scatter(direction, spread, random).scale(speed);
			// Spread over the tick's first step so the stream comes out even, not in puffs.
			Vec3 at = nozzle.add(velocity.scale(i / (double) FLAMES_PER_TICK));
			level.addParticle(Napalm.NAPALM_FLAME, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z);
		}
		Vec3 along = nozzle.add(direction.scale(2.0 + random.nextDouble() * 6.0));
		level.addParticle(Napalm.HEAT_HAZE, along.x, along.y + 0.3, along.z, direction.x * 0.05, 0.02, direction.z * 0.05);
		if (random.nextInt(3) == 0) {
			Vec3 smoke = nozzle.add(direction.scale(4.0 + random.nextDouble() * 6.0));
			level.addParticle(ParticleTypes.LARGE_SMOKE, smoke.x, smoke.y + 0.5, smoke.z, 0.0, 0.05, 0.0);
		}
	}
}
