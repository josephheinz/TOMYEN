package com.jheinz.tomyen.client.mech;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.mech.Mech;
import com.jheinz.tomyen.mech.MechArm;
import com.jheinz.tomyen.mech.MechCrew;
import com.jheinz.tomyen.mech.MechWeapon;
import com.jheinz.tomyen.mech.Mechs;

/**
 * A rotary cannon's barrels whirring while they turn, heard by everyone near (each client plays it from the synced
 * spin): it rises in pitch and volume as they spin up, roars a little louder while firing, and dies away as they
 * wind down. Started by MechClient; stops itself once the barrels are still or the walker is gone.
 */
final class MechWhirSound extends AbstractTickableSoundInstance {
	private final Mech mech;
	private final int side;

	MechWhirSound(Mech mech, int side) {
		super(Mechs.ROTARY_WHIR_SOUND, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
		this.mech = mech;
		this.side = side;
		looping = true;
		delay = 0;
		volume = 0.01f;
		pitch = 0.5f;
		follow();
	}

	/** How fast an arm's rotary barrels turn, 0 (still) to 1 (firing speed); 0 for any other weapon. */
	static float spin(Mech mech, int side) {
		if (mech.weapon(side) != MechWeapon.ROTARY_CANNON) {
			return 0.0f;
		}
		int packed = mech.armState(side);
		int status = MechArm.status(packed);
		return status == MechArm.READY || status == MechArm.SPINNING ? MechArm.progress(packed) : 0.0f;
	}

	private void follow() {
		Vec3 at = MechCrew.elbow(mech, side, 1.0f);
		x = at.x;
		y = at.y;
		z = at.z;
	}

	@Override
	public void tick() {
		float spin = spin(mech, side);
		if (mech.isRemoved() || spin <= 0.01f) {
			stop();
			return;
		}
		boolean firing = MechArm.active(mech.armState(side)) && spin >= 0.99f;
		volume = (0.2f + 0.8f * spin) * (firing ? 1.4f : 1.0f);
		pitch = 0.5f + 0.75f * spin;
		follow();
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}

	boolean playingFor(Mech mech, int side) {
		return this.mech == mech && this.side == side && !isStopped();
	}
}
