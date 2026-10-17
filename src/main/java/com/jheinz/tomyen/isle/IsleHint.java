package com.jheinz.tomyen.isle;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * On a player on the Isle with a hoard there: where their Revenant waits and at what depth, synced to that player
 * alone so their HUD can point the way (see IsleHud). Not saved: it's set again every few seconds while they're there.
 */
public record IsleHint(BlockPos target, int depth) {
	public static final StreamCodec<ByteBuf, IsleHint> STREAM_CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, IsleHint::target,
			ByteBufCodecs.VAR_INT, IsleHint::depth,
			IsleHint::new);
}
