package com.jheinz.tomyen.minigun;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Where a rotary gun's works are at: kept on a handheld Minigun's stack ({@link Miniguns#STATE}) and in a Hogfire's
 * synced data, so every client can turn the barrels, glow them, flash the muzzle and play the sounds.
 *
 * @param spin       how fast the barrels turn, 0 (still) to 1 (firing speed)
 * @param heat       0 (cold) to 1 (overheated)
 * @param overheated locked out until the heat is all gone
 * @param firing     a round went out this tick
 */
public record MinigunState(float spin, float heat, boolean overheated, boolean firing) {
	public static final MinigunState IDLE = new MinigunState(0.0f, 0.0f, false, false);

	public static final StreamCodec<ByteBuf, MinigunState> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, MinigunState::spin,
			ByteBufCodecs.FLOAT, MinigunState::heat,
			ByteBufCodecs.BOOL, MinigunState::overheated,
			ByteBufCodecs.BOOL, MinigunState::firing,
			MinigunState::new);

	/** Spun right up, cool enough and ready to put rounds out. */
	public boolean ready() {
		return spin >= 1.0f && !overheated;
	}
}
