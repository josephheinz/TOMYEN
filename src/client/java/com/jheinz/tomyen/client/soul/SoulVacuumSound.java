package com.jheinz.tomyen.client.soul;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import com.jheinz.tomyen.soul.SoulVacuumItem;
import com.jheinz.tomyen.soul.Souls;

/**
 * A Soul Vacuum's motor while someone holds its trigger: a whine that spins up in pitch and volume over the first
 * second, follows the holder, and winds down quickly when they let go. The sound file is stereo (which Minecraft
 * doesn't fade with distance), so it fades itself out over {@link #RANGE} blocks from the listener.
 */
public class SoulVacuumSound extends AbstractTickableSoundInstance {
	static final double RANGE = 32.0;
	private final Player holder;
	private int age;

	public SoulVacuumSound(Player holder) {
		super(Souls.VACUUM_LOOP, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
		this.holder = holder;
		looping = true;
		delay = 0;
		volume = 0.05f;
		pitch = 0.6f;
		follow();
	}

	/** Whether this player is sucking with a Soul Vacuum right now. */
	public static boolean sucking(Player player) {
		return player.isUsingItem() && player.getUseItem().getItem() instanceof SoulVacuumItem;
	}

	private void follow() {
		x = holder.getX();
		y = holder.getEyeY() - 0.3;
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
		if (sucking(holder)) {
			age++;
			float spin = Mth.clamp(age / 20.0f, 0.0f, 1.0f);
			Player listener = Minecraft.getInstance().player;
			double distance = listener == null ? 0.0 : listener.distanceTo(holder);
			float fade = (float) Mth.clamp(1.0 - distance / RANGE, 0.0, 1.0);
			volume = Mth.approach(volume, (0.35f + 0.45f * spin) * fade, 0.08f);
			pitch = Mth.approach(pitch, 0.7f + 0.55f * spin, 0.04f);
		} else {
			volume = Mth.approach(volume, 0.0f, 0.12f);
			pitch = Mth.approach(pitch, 0.5f, 0.08f);
			if (volume <= 0.0f) {
				stop();
			}
		}
	}
}
