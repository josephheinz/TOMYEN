package com.jheinz.mymod.shoddy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * On a homemade gun: what decides its next misfire, and whether it's jammed. Misfires are rolled from the seed and the
 * pull count rather than the server's random, so the client, which has the same stack, knows a pull is going to misfire
 * and doesn't kick for a shot that never came.
 *
 * @param seed   picked by the server on the gun's first pull (0 until then)
 * @param pulls  trigger pulls so far that got as far as the hammer falling
 * @param jammed a case is stuck in the action: it has to be cleared (punch) before the gun fires or loads again
 */
public record ShoddyState(int seed, int pulls, boolean jammed) {
	public static final ShoddyState NEW = new ShoddyState(0, 0, false);

	public static final Codec<ShoddyState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("seed", 0).forGetter(ShoddyState::seed),
			Codec.INT.optionalFieldOf("pulls", 0).forGetter(ShoddyState::pulls),
			Codec.BOOL.optionalFieldOf("jammed", false).forGetter(ShoddyState::jammed)).apply(instance, ShoddyState::new));
	public static final StreamCodec<ByteBuf, ShoddyState> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ShoddyState::seed,
			ByteBufCodecs.VAR_INT, ShoddyState::pulls,
			ByteBufCodecs.BOOL, ShoddyState::jammed,
			ShoddyState::new);

	public ShoddyState withJammed(boolean jammed) {
		return new ShoddyState(seed, pulls, jammed);
	}

	/** A uniform 0-1 roll for this state, different for each {@code salt}. */
	public float roll(int salt) {
		long h = seed * 0x9E3779B97F4A7C15L + pulls * 0xC2B2AE3D27D4EB4FL + salt * 0x165667B19E3779F9L;
		h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
		h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
		h ^= h >>> 31;
		return (h >>> 40) / (float) (1L << 24);
	}
}
