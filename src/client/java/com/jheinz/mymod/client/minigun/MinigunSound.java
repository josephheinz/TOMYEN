package com.jheinz.mymod.client.minigun;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import com.jheinz.mymod.minigun.MinigunState;
import com.jheinz.mymod.minigun.Miniguns;

/**
 * One of a rotary gun's two loops, following whoever carries it (a player, or a Hogfire with the gun mount): the
 * motor's whine, louder and higher the faster the barrels turn, or the roar of fire while rounds go out. Each swells in
 * and dies away with its gun and then stops itself. The sound files are stereo, which Minecraft doesn't fade with
 * distance, so they fade themselves out over {@link #RANGE} blocks from the listener.
 */
public class MinigunSound extends AbstractTickableSoundInstance {
	static final double RANGE = 48.0;
	private final Entity source;
	private final boolean fireLoop;

	public MinigunSound(Entity source, boolean fireLoop) {
		super(fireLoop ? Miniguns.FIRE : Miniguns.SPIN, source instanceof Player ? SoundSource.PLAYERS : SoundSource.NEUTRAL,
				SoundInstance.createUnseededRandom());
		this.source = source;
		this.fireLoop = fireLoop;
		looping = true;
		delay = 0;
		volume = 0.0f;
		follow();
	}

	private void follow() {
		x = source.getX();
		y = source instanceof Player ? source.getEyeY() - 0.6 : source.getY() + 0.8;
		z = source.getZ();
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}

	@Override
	public void tick() {
		if (source.isRemoved()) {
			stop();
			return;
		}
		follow();
		MinigunState state = MinigunClient.state(source);
		float near = MinigunClient.nearness(source, RANGE);
		float target;
		if (fireLoop) {
			target = state.firing() ? near : 0.0f;
			volume = Mth.approach(volume, target, state.firing() ? 0.5f : 0.25f);
			pitch = 0.9f + 0.12f * state.spin();
		} else {
			// The whine climbs with the barrels' speed.
			target = state.spin() * near * 0.8f;
			volume = Mth.approach(volume, target, 0.15f);
			pitch = 0.5f + 0.5f * state.spin();
		}
		if (target <= 0.0f && volume <= 0.0f) {
			stop();
		}
	}
}
