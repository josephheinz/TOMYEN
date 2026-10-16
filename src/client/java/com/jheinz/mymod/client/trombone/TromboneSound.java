package com.jheinz.mymod.client.trombone;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import com.jheinz.mymod.trombone.TromboneMusic;
import com.jheinz.mymod.trombone.TromboneNotes;
import com.jheinz.mymod.trombone.Trombones;

/**
 * One trombonist's sustained note: the one-second Bb3 loop, re-pitched every tick to the note their look picks. The
 * pitch doesn't jump: it closes {@link #GLIDE} of the gap each tick, so moving the slide makes a trombone's glissando,
 * and a long note grows a vibrato. It swells in as they blow and dies away when they stop, then stops itself.
 */
public class TromboneSound extends AbstractTickableSoundInstance {
	/** Share of the way to the new note's pitch the sound moves each tick. */
	private static final float GLIDE = 0.45f;
	private static final float LOUD = 0.9f;
	private final Player player;
	private float semitones;
	private int held;
	private int silentTicks;

	public TromboneSound(Player player) {
		super(Trombones.NOTE, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
		this.player = player;
		looping = true;
		delay = 0;
		volume = 0.0f;
		semitones = TromboneNotes.semitone(TromboneNotes.degree(player.getXRot()));
		pitch = TromboneNotes.pitch(semitones);
		follow();
	}

	private void follow() {
		x = player.getX();
		y = player.getEyeY() - 0.1;
		z = player.getZ();
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}

	/** Whether this sound is still sounding (or fading), so a new blow can pick it back up. */
	public boolean alive() {
		return !isStopped();
	}

	@Override
	public void tick() {
		if (player.isRemoved()) {
			stop();
			return;
		}
		follow();
		boolean playing = TromboneMusic.playing(player);
		float target = TromboneNotes.semitone(TromboneNotes.degree(player.getXRot()));
		if (playing) {
			semitones += (target - semitones) * GLIDE;
			if (Math.abs(target - semitones) > 0.6f) {
				held = 0;
			}
			held++;
			volume = Mth.approach(volume, LOUD, 0.45f);
			silentTicks = 0;
		} else {
			volume = Mth.approach(volume, 0.0f, 0.22f);
			held = 0;
			if (volume <= 0.0f && ++silentTicks > 10) {
				stop();
				return;
			}
		}
		// A singer's vibrato on a held note, growing in after half a second.
		float vibrato = held > 10 ? Math.min(1.0f, (held - 10) / 15.0f) * 0.22f * Mth.sin(held * 1.75f) : 0.0f;
		pitch = TromboneNotes.pitch(semitones + vibrato);
	}
}
