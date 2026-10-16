package com.jheinz.tomyen.powerarmor;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a worn suit is doing that everyone needs to see: its thrusters, and whether the wearer is climbing in or
 * out (and since when, in game time, so each client can play the animation from the start).
 *
 * @param thrusters one of the {@code OFF}..{@code BOOST} constants
 * @param sequence  {@link #NONE}, {@link #ENTERING} or {@link #EXITING}
 */
public record PowerArmorMotion(int thrusters, int sequence, long sequenceStart) {
	public static final int OFF = 0;
	/** Lit, with no forward thrust: braking to a hover. */
	public static final int HOVER = 1;
	/** Thrusting forward along the look. */
	public static final int CRUISE = 2;
	/** Thrusting forward with sprint held: faster, and hungrier. */
	public static final int BOOST = 3;

	public static final int NONE = 0;
	public static final int ENTERING = 1;
	public static final int EXITING = 2;

	public static final PowerArmorMotion IDLE = new PowerArmorMotion(OFF, NONE, 0L);

	public static final StreamCodec<RegistryFriendlyByteBuf, PowerArmorMotion> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, PowerArmorMotion::thrusters,
			ByteBufCodecs.VAR_INT, PowerArmorMotion::sequence,
			ByteBufCodecs.VAR_LONG, PowerArmorMotion::sequenceStart,
			PowerArmorMotion::new);

	public boolean flying() {
		return thrusters != OFF;
	}

	public boolean inSequence() {
		return sequence != NONE;
	}

	public PowerArmorMotion withThrusters(int thrusters) {
		return new PowerArmorMotion(thrusters, sequence, sequenceStart);
	}

	/**
	 * How open the suit is during its sequence, 0 (sealed) to 1 (open), given the current game time: it closes
	 * as the wearer climbs in, and opens as they climb out.
	 */
	public float openness(long gameTime, float partialTick) {
		if (sequence == NONE) {
			return 0.0f;
		}
		float ticks = gameTime - sequenceStart + partialTick;
		if (sequence == ENTERING) {
			// Open while they back in, then the plates swing shut over the second half.
			return 1.0f - smooth((ticks - PowerArmor.ENTER_TICKS * 0.35f) / (PowerArmor.ENTER_TICKS * 0.5f));
		}
		return smooth(ticks / (PowerArmor.EXIT_TICKS * 0.6f));
	}

	private static float smooth(float t) {
		float x = Math.clamp(t, 0.0f, 1.0f);
		return x * x * (3.0f - 2.0f * x);
	}
}
