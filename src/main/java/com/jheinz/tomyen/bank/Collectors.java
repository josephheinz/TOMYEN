package com.jheinz.tomyen.bank;

import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * What every debt collector does each second: keep its debtor targeted, catch up with them if they've
 * run off, and leave as soon as the hunt is over for any reason (paid up, dead, logged out, in another
 * dimension, or the collector just doesn't know who it's after).
 */
public final class Collectors {
	/** Further than this from the debtor and a collector teleports in closer. */
	public static final double CATCH_UP_DISTANCE = 48.0;
	private static final int CHECK_TICKS = 20;

	private Collectors() {
	}

	/** On a hunt: a collector with a debtor (a player's own tank is a Collector that's never sent after anyone). */
	public static boolean isCollector(@Nullable Entity entity) {
		return entity instanceof Collector collector && collector.debtor() != null;
	}

	/** Collectors don't hurt each other: tank shells and stray bullets pass them by. */
	public static boolean isFriendlyFire(DamageSource source) {
		return isCollector(source.getEntity()) || isCollector(source.getDirectEntity());
	}

	/** The debtor, if they're online, alive, being hunted and in this collector's dimension; null otherwise. */
	public static @Nullable ServerPlayer huntedDebtor(Mob mob, @Nullable UUID debtor) {
		if (debtor == null || !(mob.level() instanceof ServerLevel level)) {
			return null;
		}
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(debtor);
		if (player == null || !player.isAlive() || player.level() != level || !Debt.inCollections(player)) {
			return null;
		}
		return player;
	}

	/** Called every server tick by each collector. */
	public static <T extends Mob & Collector> void tick(T mob) {
		if (!(mob.level() instanceof ServerLevel level) || (mob.tickCount + mob.getId()) % CHECK_TICKS != 0) {
			return;
		}
		ServerPlayer debtor = huntedDebtor(mob, mob.debtor());
		if (debtor == null) {
			leave(level, mob);
			return;
		}
		// A tank's crew rides along with it, so only whatever is on the bottom moves.
		if (!mob.isPassenger() && mob.distanceToSqr(debtor) > CATCH_UP_DISTANCE * CATCH_UP_DISTANCE) {
			Vec3 spot = DebtCollection.findSpawn(level, debtor, mob.getType());
			if (spot != null) {
				puff(level, mob);
				mob.teleportTo(spot.x, spot.y, spot.z);
				mob.getNavigation().stop();
				puff(level, mob);
			}
		}
		boolean huntable = !debtor.isCreative() && !debtor.isSpectator();
		mob.setTarget(huntable ? debtor : null);
	}

	/** Gone in a puff of smoke: the hunt is over (or never had a target). Takes any passengers along. */
	public static void leave(ServerLevel level, Mob mob) {
		puff(level, mob);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.VILLAGER_NO, SoundSource.HOSTILE, 0.6f, 0.8f);
		mob.getPassengers().forEach(Entity::discard);
		mob.discard();
	}

	private static void puff(ServerLevel level, Mob mob) {
		level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + mob.getBbHeight() / 2, mob.getZ(), 20,
				mob.getBbWidth() / 2, mob.getBbHeight() / 2, mob.getBbWidth() / 2, 0.02);
	}

	public static void save(ValueOutput output, @Nullable UUID debtor) {
		if (debtor != null) {
			output.store("Debtor", UUIDUtil.CODEC, debtor);
		}
	}

	public static @Nullable UUID load(ValueInput input) {
		return input.read("Debtor", UUIDUtil.CODEC).orElse(null);
	}
}
