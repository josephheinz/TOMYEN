package com.jheinz.mymod.trombone;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.banana.BigBanana;

/**
 * The spells the Trombone's songs leave on mobs, ticked on the server:
 * <ul>
 * <li>{@link Mood#SAD}: the Sad Trombone's curse. Weakness and Slowness do the work (see {@link TromboneMusic}); here
 * the mob just sheds the odd tear, and clients draw it drooping.</li>
 * <li>{@link Mood#LULLED}: asleep on its feet (clients lay it on its side): it doesn't move, and it breathes out Zzz.
 * A werewolf lulled back into a villager can't change again while it naps ({@link #lulled}).</li>
 * <li>{@link Mood#DISCO}: dancing: it spins on the spot and hops to the beat, forgetting whatever it was attacking.
 * Big Bananas bounce for joy instead of hopping.</li>
 * </ul>
 */
public final class TromboneMoods {
	public enum Mood {
		SAD, LULLED, DISCO
	}

	private record Spell(Mood mood, long until) {
	}

	private static final Map<Mob, Spell> SPELLS = new WeakHashMap<>();
	/** How far away clients are told about a spell. */
	private static final double TELL_RANGE = 64.0;

	private TromboneMoods() {
	}

	public static void cast(ServerLevel level, Vec3 at, Mood mood, int ticks, List<? extends Mob> mobs) {
		if (mobs.isEmpty()) {
			return;
		}
		long until = level.getGameTime() + ticks;
		int[] ids = new int[mobs.size()];
		for (int i = 0; i < ids.length; i++) {
			Mob mob = mobs.get(i);
			SPELLS.put(mob, new Spell(mood, until));
			ids[i] = mob.getId();
		}
		TrombonePayloads.Moods payload = new TrombonePayloads.Moods(mood.ordinal(), ticks, ids);
		for (ServerPlayer player : PlayerLookup.around(level, at, TELL_RANGE)) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	/** Whether this mob is napping under the Lullaby (a cursed villager that does can't turn werewolf). */
	public static boolean lulled(Villager villager) {
		Spell spell = SPELLS.get(villager);
		return spell != null && spell.mood == Mood.LULLED && spell.until > villager.level().getGameTime();
	}

	public static void tick(ServerLevel level) {
		long now = level.getGameTime();
		for (Iterator<Map.Entry<Mob, Spell>> it = SPELLS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Mob, Spell> entry = it.next();
			Mob mob = entry.getKey();
			Spell spell = entry.getValue();
			if (mob.level() != level) {
				continue;
			}
			if (!mob.isAlive() || now >= spell.until) {
				it.remove();
				continue;
			}
			long age = now + mob.getId();
			switch (spell.mood) {
				case SAD -> {
					if (age % 25 == 0) {
						level.sendParticles(ParticleTypes.FALLING_WATER, mob.getX(), mob.getEyeY(), mob.getZ(), 1, 0.15, 0.0, 0.15, 0.0);
					}
				}
				case LULLED -> {
					mob.getNavigation().stop();
					mob.setTarget(null);
					Vec3 motion = mob.getDeltaMovement();
					mob.setDeltaMovement(0.0, Math.min(0.0, motion.y), 0.0);
					if (age % 30 == 0) {
						level.sendParticles(Trombones.ZZZ, mob.getX(), mob.getY() + mob.getBbHeight() * 0.7, mob.getZ(), 1, 0.1, 0.0, 0.1, 0.0);
					}
				}
				case DISCO -> dance(level, mob, age);
			}
		}
	}

	private static void dance(ServerLevel level, Mob mob, long age) {
		mob.setTarget(null);
		mob.getNavigation().stop();
		float yaw = mob.getYRot() + 24.0f;
		mob.setYRot(yaw);
		mob.setYBodyRot(yaw);
		mob.setYHeadRot(yaw);
		if (age % 10 == 0 && mob.onGround()) {
			if (mob instanceof BigBanana banana) {
				banana.bounce();
			} else {
				mob.setDeltaMovement(mob.getDeltaMovement().add(0.0, 0.32, 0.0));
				mob.needsSync = true;
			}
		}
		if (age % 6 == 0) {
			level.sendParticles(ParticleTypes.NOTE, mob.getX(), mob.getY() + mob.getBbHeight() + 0.4, mob.getZ(), 0,
					level.getRandom().nextInt(25) / 24.0, 0.0, 0.0, 1.0);
		}
	}

	public static void clear() {
		SPELLS.clear();
	}
}
