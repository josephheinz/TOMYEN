package com.jheinz.tomyen.client.napalm;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import com.jheinz.tomyen.napalm.FlamethrowerItem;
import com.jheinz.tomyen.napalm.Napalm;

/**
 * The roar of a flamethrower spraying, looping on its holder while they keep spraying, swelling in fast and dying away
 * when they let go (then it stops itself). The sound file is stereo, which Minecraft doesn't fade with distance, so it
 * fades itself out over {@link #RANGE} blocks from the listener.
 */
public class FlamethrowerSound extends AbstractTickableSoundInstance {
	static final double RANGE = 40.0;
	private final Player holder;

	public FlamethrowerSound(Player holder) {
		super(Napalm.FLAMETHROWER_ROAR, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
		this.holder = holder;
		looping = true;
		delay = 0;
		volume = 0.0f;
		pitch = 0.9f + holder.getRandom().nextFloat() * 0.15f;
		follow();
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
		boolean spraying = holder.getMainHandItem().getItem() instanceof FlamethrowerItem
				&& FlamethrowerItem.firing(holder.getMainHandItem(), holder.level().getGameTime());
		Player listener = Minecraft.getInstance().player;
		double distance = listener == null ? 0.0 : listener.distanceTo(holder);
		float target = spraying ? (float) Mth.clamp(1.0 - distance / RANGE, 0.0, 1.0) : 0.0f;
		volume = Mth.approach(volume, target, spraying ? 0.35f : 0.12f);
		if (!spraying && volume <= 0.0f) {
			stop();
		}
	}
}
