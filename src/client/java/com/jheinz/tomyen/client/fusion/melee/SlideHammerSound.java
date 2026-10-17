package com.jheinz.tomyen.client.fusion.melee;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

import com.jheinz.tomyen.fusion.melee.SlideHammerItem;
import com.jheinz.tomyen.trombone.TromboneNotes;
import com.jheinz.tomyen.trombone.Trombones;

/**
 * The Slide Hammer winding up: the trombone's sustained note, its slide gliding up two octaves of the scale as the slam
 * charges (one degree every few ticks, glissando between), then held at the top with a growing vibrato until the
 * player lets go. It dies away fast when they do.
 */
public class SlideHammerSound extends AbstractTickableSoundInstance {
	private static final float GLIDE = 0.4f;
	private static final float LOUD = 0.85f;
	private final AbstractClientPlayer player;
	private float semitones;
	private int held;
	private int silentTicks;

	public SlideHammerSound(AbstractClientPlayer player) {
		super(Trombones.NOTE, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
		this.player = player;
		looping = true;
		delay = 0;
		volume = 0.0f;
		semitones = TromboneNotes.semitone(0);
		pitch = TromboneNotes.pitch(semitones);
		follow();
	}

	private void follow() {
		x = player.getX();
		y = player.getEyeY() - 0.2;
		z = player.getZ();
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}

	@Override
	public void tick() {
		if (player.isRemoved()) {
			stop();
			return;
		}
		follow();
		if (MeleeFusionsClient.windingUp(player)) {
			int ticks = player.getTicksUsingItem();
			float target = TromboneNotes.semitone(SlideHammerItem.degree(ticks));
			semitones += (target - semitones) * GLIDE;
			volume = Mth.approach(volume, LOUD, 0.35f);
			held = ticks >= SlideHammerItem.FULL_CHARGE ? held + 1 : 0;
			silentTicks = 0;
		} else {
			volume = Mth.approach(volume, 0.0f, 0.3f);
			held = 0;
			if (volume <= 0.0f && ++silentTicks > 5) {
				stop();
				return;
			}
		}
		float vibrato = held > 4 ? Math.min(1.0f, (held - 4) / 12.0f) * 0.3f * Mth.sin(held * 1.9f) : 0.0f;
		pitch = Mth.clamp(TromboneNotes.pitch(semitones + vibrato), 0.5f, 2.0f);
	}
}
