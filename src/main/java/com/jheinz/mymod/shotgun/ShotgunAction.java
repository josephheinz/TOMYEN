package com.jheinz.mymod.shotgun;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;

/**
 * What a held shotgun's action is doing, timed in game ticks so every client can animate it from the stack alone:
 * the pump being worked after a shot (or to chamber the first shell after loading an empty gun), a semi-auto
 * cycle, one shell being pushed into the tube, or a homemade gun's jam being cleared.
 *
 * @param start         game time it began
 * @param ticks         how long it takes
 * @param shell         the shell fired (for PUMP, CYCLE and CLEAR, so the hull that flies out matches) or being loaded
 * @param chambering    for LOAD, whether the tube was empty when loading began, so the pump has to be worked once
 *                      loading stops; for PUMP, that it's chambering the first shell rather than clearing a fired hull
 */
public record ShotgunAction(Kind kind, long start, int ticks, Holder<Item> shell, boolean chambering) {
	public enum Kind implements StringRepresentable {
		PUMP("pump"), CYCLE("cycle"), LOAD("load"),
		/** Clearing a jam (see ShoddyGunItem): the action is knocked and worked to throw out the stuck case. */
		CLEAR("clear");

		public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);
		public static final StreamCodec<ByteBuf, Kind> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(i -> values()[i], Kind::ordinal);

		private final String name;

		Kind(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	public static final Codec<ShotgunAction> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Kind.CODEC.fieldOf("kind").forGetter(ShotgunAction::kind),
			Codec.LONG.fieldOf("start").forGetter(ShotgunAction::start),
			Codec.INT.fieldOf("ticks").forGetter(ShotgunAction::ticks),
			Item.CODEC.fieldOf("shell").forGetter(ShotgunAction::shell),
			Codec.BOOL.optionalFieldOf("chambering", false).forGetter(ShotgunAction::chambering)).apply(instance, ShotgunAction::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, ShotgunAction> STREAM_CODEC = StreamCodec.composite(
			Kind.STREAM_CODEC, ShotgunAction::kind,
			ByteBufCodecs.VAR_LONG, ShotgunAction::start,
			ByteBufCodecs.VAR_INT, ShotgunAction::ticks,
			Item.STREAM_CODEC, ShotgunAction::shell,
			ByteBufCodecs.BOOL, ShotgunAction::chambering,
			ShotgunAction::new);

	public long end() {
		return start + ticks;
	}

	/** How far through it is at (fractional) game time {@code now}, 0 to 1. */
	public float progress(double now) {
		return ticks <= 0 ? 1.0f : (float) Math.max(0.0, Math.min(1.0, (now - start) / ticks));
	}
}
