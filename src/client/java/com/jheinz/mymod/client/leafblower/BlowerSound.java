package com.jheinz.mymod.client.leafblower;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.jheinz.mymod.leafblower.LeafBlowerItem;
import com.jheinz.mymod.leafblower.LeafBlowers;

/**
 * A blower's engine on its holder: idling low and quiet while it's held with fuel in it, revving up to a full-throttle
 * scream while it runs (a little higher sucking, the impeller working harder), and dying away when it's put down or runs
 * dry, then it stops itself. The file is the engine flat out; idle is it slowed down.
 */
public class BlowerSound extends AbstractTickableSoundInstance {
	private static final float IDLE_PITCH = 0.55f;
	private static final float IDLE_VOLUME = 0.3f;
	private final Player holder;

	public BlowerSound(Player holder) {
		super(LeafBlowers.ENGINE, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
		this.holder = holder;
		looping = true;
		delay = 0;
		volume = 0.0f;
		pitch = IDLE_PITCH;
		follow();
	}

	/** Held and fuelled, so the engine's turning. */
	static boolean turning(Player player) {
		ItemStack held = player.getMainHandItem();
		return held.getItem() instanceof LeafBlowerItem && (LeafBlowerItem.fuel(held) > 0 || player.hasInfiniteMaterials());
	}

	private void follow() {
		x = holder.getX();
		y = holder.getEyeY() - 0.5;
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
		boolean on = turning(holder);
		ItemStack held = holder.getMainHandItem();
		boolean running = on && LeafBlowerItem.running(held, holder.level().getGameTime());
		float targetVolume = !on ? 0.0f : running ? 0.85f : IDLE_VOLUME;
		float targetPitch = running ? (LeafBlowerItem.sucking(held) ? 1.08f : 1.0f) : IDLE_PITCH;
		volume = Mth.approach(volume, targetVolume, running ? 0.12f : 0.06f);
		// The revs climb quicker than they fall, with a little wobble from the governor.
		pitch = Mth.approach(pitch, targetPitch, targetPitch > pitch ? 0.06f : 0.035f)
				+ (running ? 0.01f * Mth.sin(holder.tickCount * 0.9f) : 0.0f);
		if (!on && volume <= 0.0f) {
			stop();
		}
	}
}
