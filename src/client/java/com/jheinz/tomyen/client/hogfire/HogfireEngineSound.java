package com.jheinz.tomyen.client.hogfire;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import com.jheinz.tomyen.hogfire.Hogfire;
import com.jheinz.tomyen.hogfire.Hogfires;

/**
 * A Hogfire's rocket, a loop that follows it around: silent while the engine's cold, a low rumble at low throttle
 * rising to a roar, higher again with the afterburner lit. The sound file is stereo, which Minecraft doesn't fade
 * with distance, so it fades itself out over {@link #RANGE} blocks from the listener.
 */
public class HogfireEngineSound extends AbstractTickableSoundInstance {
	private static final double RANGE = 48.0;
	private final Hogfire hogfire;

	public HogfireEngineSound(Hogfire hogfire) {
		super(Hogfires.ENGINE_SOUND, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
		this.hogfire = hogfire;
		looping = true;
		delay = 0;
		volume = 0.0f;
		pitch = 0.6f;
		follow();
	}

	private void follow() {
		x = hogfire.getX();
		y = hogfire.getY() + 0.8;
		z = hogfire.getZ();
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}

	@Override
	public void tick() {
		if (hogfire.isRemoved() || !hogfire.hasEngine()) {
			stop();
			return;
		}
		follow();
		float throttle = hogfire.throttle();
		boolean running = hogfire.engineRunning();
		float targetVolume = running ? 0.35f + throttle * 0.65f + (hogfire.afterburner() ? 0.4f : 0.0f) : 0.0f;
		float targetPitch = 0.55f + throttle * 0.75f + (hogfire.afterburner() ? 0.3f : 0.0f);
		Player listener = Minecraft.getInstance().player;
		double distance = listener == null ? 0.0 : listener.distanceTo(hogfire);
		targetVolume *= (float) Mth.clamp(1.0 - distance / RANGE, 0.0, 1.0);
		volume = Mth.approach(volume, targetVolume, 0.08f);
		pitch = Mth.approach(pitch, running ? targetPitch : 0.5f, 0.05f);
	}
}
