package com.jheinz.tomyen.client.bathtub;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

import com.jheinz.tomyen.bathtub.BathtubBoat;
import com.jheinz.tomyen.bathtub.Bathtubs;

/**
 * A Bathtub Boat's outboard, a loop that follows it: a lumpy two-stroke idle while someone's at the tiller with fuel in
 * the tank, revving up to a buzzing whine with the throttle, and dying away when it stops. (The file is the leaf blower's
 * engine, pitched down for the bigger motor.)
 */
public class MotorSound extends AbstractTickableSoundInstance {
	private static final float IDLE_VOLUME = 0.35f;
	private static final float IDLE_PITCH = 0.55f;
	private final BathtubBoat boat;

	public MotorSound(BathtubBoat boat) {
		super(Bathtubs.MOTOR_SOUND, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
		this.boat = boat;
		looping = true;
		delay = 0;
		volume = 0.0f;
		pitch = IDLE_PITCH;
		follow();
	}

	private void follow() {
		// At the motor, over the stern.
		float yaw = boat.getYRot() * Mth.DEG_TO_RAD;
		x = boat.getX() + Mth.sin(yaw) * 1.2;
		y = boat.getY() + 0.8;
		z = boat.getZ() - Mth.cos(yaw) * 1.2;
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}

	@Override
	public void tick() {
		if (boat.isRemoved()) {
			stop();
			return;
		}
		follow();
		boolean running = boat.engineRunning();
		float throttle = boat.throttle();
		float targetVolume = running ? IDLE_VOLUME + 0.55f * throttle : 0.0f;
		float targetPitch = running ? IDLE_PITCH + 0.75f * throttle + (boat.isInWater() ? 0.0f : 0.1f * throttle) : 0.4f;
		volume = Mth.approach(volume, targetVolume, 0.06f);
		pitch = Mth.approach(pitch, targetPitch, 0.04f);
		if (!running && volume <= 0.0f) {
			stop();
		}
	}
}
