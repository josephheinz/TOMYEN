package com.jheinz.tomyen.client.fusion.melee;

import java.util.HashMap;
import java.util.Map;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.sounds.SoundSource;

import com.jheinz.tomyen.fusion.melee.SlideHammerItem;
import com.jheinz.tomyen.trombone.Trombones;

/**
 * The melee fusions on the client: the Slide Hammer's rising note while anyone winds up a slam (SlideHammerSound).
 * Whether a player is winding up is the vanilla use state every client already has.
 */
public final class MeleeFusionsClient {
	private static final double RANGE = 40.0;
	private static final Map<Integer, SlideHammerSound> SOUNDS = new HashMap<>();

	private MeleeFusionsClient() {
	}

	public static void initialize() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level != null && !client.isPaused()) {
				tick(client, client.level);
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SOUNDS.clear());
	}

	/** Whether the player is winding up a Slide Hammer slam. */
	static boolean windingUp(AbstractClientPlayer player) {
		return player.isUsingItem() && player.getUseItem().getItem() instanceof SlideHammerItem && !player.isRemoved();
	}

	private static void tick(Minecraft minecraft, ClientLevel level) {
		SOUNDS.values().removeIf(SlideHammerSound::isStopped);
		for (AbstractClientPlayer player : level.players()) {
			if (!windingUp(player) || player.distanceToSqr(minecraft.gameRenderer.mainCamera().position()) > RANGE * RANGE) {
				continue;
			}
			SlideHammerSound sound = SOUNDS.get(player.getId());
			if (sound == null || sound.isStopped()) {
				sound = new SlideHammerSound(player);
				SOUNDS.put(player.getId(), sound);
				minecraft.getSoundManager().play(sound);
				// The lips catch: the note blats in at the bottom of the slide.
				level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(), Trombones.ATTACK, SoundSource.PLAYERS, 0.8f, 0.5f, false);
			}
		}
	}
}
