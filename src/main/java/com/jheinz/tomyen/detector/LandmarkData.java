package com.jheinz.tomyen.detector;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;

/**
 * The landmarks found in a level so far (see {@link Landmarks}): the middle of each Fae Ring and each meteorite crash
 * site. Immutable: a change is a new copy set on the level, which is what marks it to be saved.
 */
public record LandmarkData(List<BlockPos> faeRings, List<BlockPos> meteorites) {
	public static final LandmarkData EMPTY = new LandmarkData(List.of(), List.of());
	public static final Codec<LandmarkData> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.listOf().optionalFieldOf("fae_rings", List.of()).forGetter(LandmarkData::faeRings),
			BlockPos.CODEC.listOf().optionalFieldOf("meteorites", List.of()).forGetter(LandmarkData::meteorites))
			.apply(i, LandmarkData::new));

	public List<BlockPos> get(Landmarks.Kind kind) {
		return kind == Landmarks.Kind.FAE_RING ? faeRings : meteorites;
	}

	public LandmarkData with(Landmarks.Kind kind, List<BlockPos> positions) {
		List<BlockPos> copy = List.copyOf(positions);
		return kind == Landmarks.Kind.FAE_RING ? new LandmarkData(copy, meteorites) : new LandmarkData(faeRings, copy);
	}
}
