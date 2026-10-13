package com.jheinz.mymod.client.drill;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/**
 * A drill running in someone's hands: the motor's whirr (or the Hand Drill's gears), looping on the holder while the
 * bit turns. It rises in pitch and volume as the drill spins up, drops a little in pitch while the bit bites into
 * rock, and dies away as it coasts down, then stops itself. The sound files are stereo, which Minecraft doesn't fade
 * with distance, so it fades itself out over {@link #RANGE} blocks from the listener.
 */
public class DrillSound extends AbstractTickableSoundInstance {
	static final double RANGE = 32.0;
	private final Player holder;
	/** The drill it's the sound of: put away or swapped for another, it dies away. */
	private final Item drill;
	private final float basePitch;
	private final float loudness;

	public DrillSound(Player holder, SoundEvent sound, float basePitch, float loudness) {
		super(sound, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
		this.holder = holder;
		this.drill = holder.getMainHandItem().getItem();
		this.basePitch = basePitch;
		this.loudness = loudness;
		looping = true;
		delay = 0;
		volume = 0.0f;
		pitch = basePitch * 0.6f;
		follow();
	}

	private void follow() {
		x = holder.getX();
		y = holder.getEyeY() - 0.4;
		z = holder.getZ();
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}

	@Override
	public void tick() {
		if (holder.isRemoved()) {
			stop();
			return;
		}
		follow();
		float spin = holder.getMainHandItem().is(drill) ? DrillSpin.speed(holder) : 0.0f;
		Player listener = Minecraft.getInstance().player;
		double distance = listener == null ? 0.0 : listener.distanceTo(holder);
		float target = spin * loudness * (float) Mth.clamp(1.0 - distance / RANGE, 0.0, 1.0);
		volume = Mth.approach(volume, target, 0.15f);
		float wantPitch = basePitch * (0.6f + 0.4f * spin) * (DrillSpin.biting(holder) ? 0.88f : 1.0f);
		pitch = Mth.approach(pitch, wantPitch, 0.06f);
		if (spin <= 0.0f && volume <= 0.0f) {
			stop();
		}
	}
}
