package com.jheinz.mymod.neuralink;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;

import com.jheinz.mymod.ModEffects;

/**
 * Fakes bullet time for {@link #DURATION_TICKS}: mobs within {@link #RADIUS} blocks crawl under heavy Slowness
 * and other people's projectiles lose most of their speed as they come in, while the user gets Speed II and
 * reloads guns in half the time (see GunItem#reloadFactor). The client tints the world grey while it lasts.
 */
final class Overclock implements NeuralCore.Ability {
	public static final int DURATION_TICKS = 100;
	public static final double RADIUS = 16.0;
	private static final double PROJECTILE_SLOWDOWN = 0.3;
	private static final int REFRESH_INTERVAL = 5;

	/** Projectiles already slowed, so each is only slowed once. */
	private static final Set<UUID> SLOWED = new HashSet<>();

	@Override
	public boolean start(ServerPlayer player, NeuralCore.State state) {
		player.addEffect(new MobEffectInstance(ModEffects.OVERCLOCKED, DURATION_TICKS, 0, false, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.SPEED, DURATION_TICKS, 1, false, false, false));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 0.5f);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.6f);
		return true;
	}

	static void tickBulletTime(MinecraftServer server) {
		boolean anyone = false;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.hasEffect(ModEffects.OVERCLOCKED)) {
				continue;
			}
			anyone = true;
			ServerLevel level = (ServerLevel) player.level();
			if (player.tickCount % REFRESH_INTERVAL == 0) {
				for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(RADIUS),
						mob -> mob.isAlive() && NeuralHijack.ownerOf(mob) != player)) {
					mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, REFRESH_INTERVAL * 3, 3, false, false));
				}
			}
			for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(RADIUS),
					projectile -> projectile.getOwner() != player && !SLOWED.contains(projectile.getUUID()))) {
				projectile.setDeltaMovement(projectile.getDeltaMovement().scale(PROJECTILE_SLOWDOWN));
				projectile.needsSync = true;
				SLOWED.add(projectile.getUUID());
			}
		}
		if (!anyone) {
			SLOWED.clear();
		}
	}
}
