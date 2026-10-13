package com.jheinz.mymod.client.alien;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import com.jheinz.mymod.alien.Aliens;
import com.jheinz.mymod.alien.Ufo;

/**
 * A saucer's hum, a loop that follows it about: a low drone under a wobbling theremin note, its pitch drifting slowly
 * and rising as it beams something up or races off. The sound file is stereo, which Minecraft doesn't fade with
 * distance, so it fades itself out over {@link #RANGE} blocks from the listener.
 */
public class UfoHumSound extends AbstractTickableSoundInstance {
	static final double RANGE = 64.0;
	private final Ufo ufo;

	public UfoHumSound(Ufo ufo) {
		super(Aliens.UFO_HUM, SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
		this.ufo = ufo;
		looping = true;
		delay = 0;
		volume = 0.0f;
		follow();
	}

	private void follow() {
		x = ufo.getX();
		y = ufo.getY() + 0.8;
		z = ufo.getZ();
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}

	@Override
	public void tick() {
		if (ufo.isRemoved() || !ufo.isAlive()) {
			stop();
			return;
		}
		follow();
		Player listener = Minecraft.getInstance().player;
		double distance = listener == null ? 0.0 : listener.distanceTo(ufo);
		float wanted = 0.7f * (float) Mth.clamp(1.0 - distance / RANGE, 0.0, 1.0);
		volume = Mth.approach(volume, wanted, 0.05f);
		float drift = 0.04f * Mth.sin(ufo.tickCount * 0.02f);
		float wantedPitch = (ufo.isBeaming() ? 1.15f : ufo.state() == Ufo.LEAVING ? 1.3f : ufo.isCrashing() ? 0.6f : 1.0f) + drift;
		pitch = Mth.approach(pitch, wantedPitch, 0.01f);
	}
}
