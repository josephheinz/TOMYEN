package com.jheinz.tomyen.detector;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What the server tells a player's trackers (see {@link DetectorFixes}); the client works out the rest itself.
 *
 * @param faeRing   the nearest Fae Ring within their dowsing rod's range, if they hold one
 * @param meteorite the nearest crash site within the Meteorite Scanner's range, if they hold one
 * @param omen      they hold a Lunar Almanac and a cursed villager is close by
 */
public record DetectorFix(Optional<BlockPos> faeRing, Optional<BlockPos> meteorite, boolean omen) {
	public static final DetectorFix NONE = new DetectorFix(Optional.empty(), Optional.empty(), false);
	public static final StreamCodec<RegistryFriendlyByteBuf, DetectorFix> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.optional(BlockPos.STREAM_CODEC), DetectorFix::faeRing,
			ByteBufCodecs.optional(BlockPos.STREAM_CODEC), DetectorFix::meteorite,
			ByteBufCodecs.BOOL, DetectorFix::omen,
			DetectorFix::new);
}
