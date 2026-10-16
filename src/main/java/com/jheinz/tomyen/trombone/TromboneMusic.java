package com.jheinz.tomyen.trombone;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import com.jheinz.tomyen.alien.Alien;
import com.jheinz.tomyen.banana.BigBanana;
import com.jheinz.tomyen.werewolf.Werewolf;
import com.jheinz.tomyen.werewolf.WerewolfCurse;

/**
 * Listens to everyone playing a Trombone, on the server. Each tick a player holds use with one, the note is the scale
 * degree their look points at ({@link TromboneNotes}). Notes are split where the player lets go and blows again, or
 * where the slide moves and settles on a new note for {@link #SETTLE_TICKS} (a glide's passing notes don't count), so
 * both tonguing and slurring work. The last few notes make the phrase; a pause of {@link #PHRASE_GAP} ticks starts a
 * new one. Each finished note is checked against the songs ({@link TromboneSong}); a match casts the song (unless it's
 * cooling down) and clears the phrase. The player's HUD gets the phrase after every note ({@link TrombonePayloads.Staff}).
 * <p>
 * Every new note also gets a rise out of the neighbours: Big Bananas nearby bounce along, and UFO aliens clap their
 * hands over their ears and run.
 */
public final class TromboneMusic {
	public static final int PHRASE_GAP = 60;
	public static final int SETTLE_TICKS = 4;
	private static final int MIN_NOTE_TICKS = 2;
	private static final int PHRASE_KEEP = 8;
	public static final double SONG_RADIUS = 12.0;
	private static final double BANANA_RADIUS = 10.0;
	private static final double ALIEN_RADIUS = 16.0;
	private static final int SPELL_TICKS = 600;
	private static final int LULLABY_TICKS = 300;
	private static final int DISCO_TICKS = 160;

	private static final class Trombonist {
		final List<TromboneSong.Note> phrase = new ArrayList<>();
		final long[] cooldownUntil = new long[TromboneSong.values().length];
		boolean blowing;
		int degree;
		int ticks;
		long lastNoteEnd = Long.MIN_VALUE / 2;
	}

	private static final Map<UUID, Trombonist> PLAYERS = new HashMap<>();

	private TromboneMusic() {
	}

	public static boolean playing(Player player) {
		return player.isUsingItem() && player.getUseItem().getItem() instanceof TromboneItem;
	}

	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean playing = playing(player);
			Trombonist state = PLAYERS.get(player.getUUID());
			if (state == null) {
				if (!playing) {
					continue;
				}
				state = new Trombonist();
				PLAYERS.put(player.getUUID(), state);
			}
			tick(player, state, playing);
		}
		PLAYERS.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
	}

	private static void tick(ServerPlayer player, Trombonist state, boolean playing) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		int degree = TromboneNotes.degree(player.getXRot());
		if (playing) {
			if (!state.blowing) {
				state.blowing = true;
				state.degree = degree;
				state.ticks = 0;
				if (now - state.lastNoteEnd > PHRASE_GAP && !state.phrase.isEmpty()) {
					state.phrase.clear();
					send(player, state, null, false);
				}
				noteStarted(level, player);
			} else if (degree != state.degree) {
				if (state.ticks >= SETTLE_TICKS) {
					finish(player, state, state.degree, state.ticks);
					state.ticks = 0;
					noteStarted(level, player);
				}
				state.degree = degree;
			}
			state.ticks++;
		} else if (state.blowing) {
			state.blowing = false;
			state.lastNoteEnd = now;
			if (state.ticks >= MIN_NOTE_TICKS) {
				finish(player, state, state.degree, state.ticks);
			}
		}
	}

	/** A note's done: add it to the phrase and see if that makes a song. */
	private static void finish(ServerPlayer player, Trombonist state, int degree, int ticks) {
		state.phrase.add(new TromboneSong.Note(degree, ticks));
		while (state.phrase.size() > PHRASE_KEEP) {
			state.phrase.remove(0);
		}
		ServerLevel level = player.level();
		for (TromboneSong song : TromboneSong.values()) {
			if (song.matchesEnd(state.phrase)) {
				boolean ready = level.getGameTime() >= state.cooldownUntil[song.ordinal()] || player.hasInfiniteMaterials();
				if (ready) {
					state.cooldownUntil[song.ordinal()] = level.getGameTime() + song.cooldown();
					perform(level, player, song);
				}
				send(player, state, song, ready);
				state.phrase.clear();
				return;
			}
		}
		send(player, state, null, false);
	}

	private static void send(ServerPlayer player, Trombonist state, TromboneSong song, boolean played) {
		int n = state.phrase.size();
		byte[] degrees = new byte[n];
		byte[] ticks = new byte[n];
		for (int i = 0; i < n; i++) {
			degrees[i] = (byte) state.phrase.get(i).degree();
			ticks[i] = (byte) Math.min(127, state.phrase.get(i).ticks());
		}
		long now = player.level().getGameTime();
		int[] cooldowns = new int[state.cooldownUntil.length];
		for (int i = 0; i < cooldowns.length; i++) {
			cooldowns[i] = (int) Math.max(0L, state.cooldownUntil[i] - now);
		}
		ServerPlayNetworking.send(player, new TrombonePayloads.Staff(degrees, ticks, song == null ? -1 : song.ordinal(), played, cooldowns));
	}

	/** A new note sounds: Big Bananas bounce along and aliens can't stand it. */
	private static void noteStarted(ServerLevel level, ServerPlayer player) {
		for (BigBanana banana : level.getEntitiesOfClass(BigBanana.class, player.getBoundingBox().inflate(BANANA_RADIUS))) {
			if (banana.onGround() && banana.getTarget() == null) {
				banana.getLookControl().setLookAt(player, 30.0f, 30.0f);
				banana.bounce();
			}
		}
		for (Alien alien : level.getEntitiesOfClass(Alien.class, player.getBoundingBox().inflate(ALIEN_RADIUS))) {
			alien.coverEars(player);
		}
	}

	private static AABB around(Player player) {
		return player.getBoundingBox().inflate(SONG_RADIUS);
	}

	private static void perform(ServerLevel level, ServerPlayer player, TromboneSong song) {
		switch (song) {
			case SAD_TROMBONE -> sadTrombone(level, player);
			case WAR_MARCH -> warMarch(level, player);
			case LULLABY -> lullaby(level, player);
			case DISCO -> disco(level, player);
		}
	}

	/** Every hostile mob in earshot is cursed with Weakness and Slowness and hangs its head; werewolves whimper. */
	private static void sadTrombone(ServerLevel level, ServerPlayer player) {
		level.playSound(null, player.getX(), player.getY(), player.getZ(), Trombones.SAD_TROMBONE, SoundSource.PLAYERS, 1.6f, 1.0f);
		List<Mob> cursed = level.getEntitiesOfClass(Mob.class, around(player), mob -> mob instanceof Enemy && mob.isAlive());
		for (Mob mob : cursed) {
			mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, SPELL_TICKS, 1), player);
			mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SPELL_TICKS, 0), player);
			level.sendParticles(ParticleTypes.FALLING_WATER, mob.getX(), mob.getEyeY(), mob.getZ(), 4, 0.2, 0.05, 0.2, 0.0);
			if (mob instanceof Werewolf) {
				level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), Trombones.WEREWOLF_WHIMPER, SoundSource.HOSTILE, 1.2f, 1.0f);
			}
		}
		TromboneMoods.cast(level, player.position(), TromboneMoods.Mood.SAD, SPELL_TICKS, cursed);
	}

	/** Strength and Speed for everyone on the player's side: players, tamed pets, iron golems and villagers. */
	private static void warMarch(ServerLevel level, ServerPlayer player) {
		level.playSound(null, player.getX(), player.getY(), player.getZ(), Trombones.FANFARE, SoundSource.PLAYERS, 1.6f, 1.0f);
		for (LivingEntity ally : level.getEntitiesOfClass(LivingEntity.class, around(player), TromboneMusic::ally)) {
			ally.addEffect(new MobEffectInstance(MobEffects.STRENGTH, SPELL_TICKS, 0), player);
			ally.addEffect(new MobEffectInstance(MobEffects.SPEED, SPELL_TICKS, 0), player);
			level.sendParticles(ParticleTypes.NOTE, ally.getX(), ally.getY() + ally.getBbHeight() + 0.5, ally.getZ(), 0,
					level.getRandom().nextInt(25) / 24.0, 0.0, 0.0, 1.0);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, ally.getX(), ally.getY(0.5), ally.getZ(), 6, 0.3, 0.4, 0.3, 0.0);
		}
	}

	private static boolean ally(LivingEntity entity) {
		if (!entity.isAlive()) {
			return false;
		}
		if (entity instanceof Player player) {
			return !player.isSpectator();
		}
		return entity instanceof TamableAnimal pet && pet.isTame() || entity instanceof IronGolem || entity instanceof AbstractVillager;
	}

	/**
	 * Every peaceful mob nearby nods off where it stands, and werewolves are sung back into the villagers they were, to
	 * sleep it off before the curse can take them again.
	 */
	private static void lullaby(ServerLevel level, ServerPlayer player) {
		level.playSound(null, player.getX(), player.getY(), player.getZ(), Trombones.LULLABY_CHIME, SoundSource.PLAYERS, 1.2f, 1.0f);
		List<Mob> sleepers = new ArrayList<>();
		for (Mob mob : level.getEntitiesOfClass(Mob.class, around(player), Mob::isAlive)) {
			if (mob instanceof Werewolf werewolf) {
				Villager villager = WerewolfCurse.revert(level, werewolf);
				if (villager != null) {
					sleepers.add(villager);
				}
			} else if (!(mob instanceof Enemy)) {
				sleepers.add(mob);
			}
		}
		TromboneMoods.cast(level, player.position(), TromboneMoods.Mood.LULLED, LULLABY_TICKS, sleepers);
	}

	/** Every mob nearby drops what it's doing and dances. */
	private static void disco(ServerLevel level, ServerPlayer player) {
		level.playSound(null, player.getX(), player.getY(), player.getZ(), Trombones.DISCO_BEAT, SoundSource.PLAYERS, 1.2f, 1.0f);
		List<Mob> dancers = level.getEntitiesOfClass(Mob.class, around(player), Mob::isAlive);
		TromboneMoods.cast(level, player.position(), TromboneMoods.Mood.DISCO, DISCO_TICKS, dancers);
	}

	public static void clear() {
		PLAYERS.clear();
	}
}
