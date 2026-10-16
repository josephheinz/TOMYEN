package com.jheinz.mymod.client.trombone;

import java.util.HashMap;
import java.util.Map;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.HeartParticle;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.trombone.TromboneMusic;
import com.jheinz.mymod.trombone.TromboneNotes;
import com.jheinz.mymod.trombone.TrombonePayloads;
import com.jheinz.mymod.trombone.Trombones;

/**
 * The Trombone on the client. Every player within earshot who's blowing one gets their own sustained note
 * ({@link TromboneSound}, gliding as their slide moves), a brassy attack each time they start a note, and music notes
 * floating up out of the bell, coloured by pitch. All of it follows their synced look, so everyone hears the same tune.
 * Also the slide property, the HUD staff, the song spells on mobs and the lullaby's Zzz.
 */
public final class TromboneClient {
	private static final double RANGE = 40.0;
	private static final Map<Integer, TromboneSound> SOUNDS = new HashMap<>();
	private static final Map<Integer, Boolean> BLOWING = new HashMap<>();
	private static final Map<Integer, Integer> DEGREES = new HashMap<>();

	private TromboneClient() {
	}

	public static void initialize() {
		TromboneSlide.register();
		ParticleProviderRegistry.getInstance().register(Trombones.ZZZ, HeartParticle.Provider::new);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, MyMod.id("trombone"), TromboneHud::extract);
		ClientPlayNetworking.registerGlobalReceiver(TrombonePayloads.Staff.TYPE, (payload, context) -> TromboneHud.receive(payload));
		ClientPlayNetworking.registerGlobalReceiver(TrombonePayloads.Moods.TYPE, (payload, context) -> TromboneMoodsClient.receive(payload));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level != null && !client.isPaused()) {
				tick(client, client.level);
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			SOUNDS.clear();
			BLOWING.clear();
			DEGREES.clear();
			TromboneSlide.clear();
			TromboneHud.clear();
			TromboneMoodsClient.clear();
		});
	}

	private static void tick(Minecraft minecraft, ClientLevel level) {
		TromboneMoodsClient.tick();
		SOUNDS.values().removeIf(TromboneSound::isStopped);
		Vec3 camera = minecraft.gameRenderer.mainCamera().position();
		for (AbstractClientPlayer player : level.players()) {
			int id = player.getId();
			boolean playing = TromboneMusic.playing(player) && player.position().distanceTo(camera) < RANGE;
			boolean was = BLOWING.getOrDefault(id, false);
			if (!playing) {
				if (was) {
					BLOWING.remove(id);
					DEGREES.remove(id);
				}
				continue;
			}
			BLOWING.put(id, true);
			int degree = TromboneNotes.degree(player.getXRot());
			Integer last = DEGREES.put(id, degree);
			TromboneSound sound = SOUNDS.get(id);
			if (sound == null || sound.isStopped()) {
				sound = new TromboneSound(player);
				SOUNDS.put(id, sound);
				minecraft.getSoundManager().play(sound);
			}
			if (!was) {
				// Tongued: the lips catch and the note blats in.
				level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(), Trombones.ATTACK, SoundSource.PLAYERS, 0.8f,
						Mth.clamp(TromboneNotes.pitch(TromboneNotes.semitone(degree)), 0.5f, 2.0f), false);
			}
			if (!was || (last != null && last != degree && level.getRandom().nextInt(3) == 0) || player.tickCount % 5 == 0) {
				floatNote(level, player, degree);
			}
		}
		BLOWING.keySet().removeIf(id -> level.getEntity(id) == null);
	}

	/** A music note drifting up out of the bell, coloured by the note (the note block's colours, low to high). */
	private static void floatNote(ClientLevel level, AbstractClientPlayer player, int degree) {
		Vec3 look = player.getViewVector(1.0f);
		Vec3 left = new Vec3(look.z, 0.0, -look.x).normalize();
		Vec3 bell = player.getEyePosition().add(look.scale(1.05)).add(left.scale(0.22)).add(0.0, -0.08, 0.0);
		double colour = TromboneNotes.semitone(degree) / 24.0;
		level.addParticle(ParticleTypes.NOTE, bell.x + (level.getRandom().nextDouble() - 0.5) * 0.2, bell.y,
				bell.z + (level.getRandom().nextDouble() - 0.5) * 0.2, colour, 0.0, 0.0);
	}
}
