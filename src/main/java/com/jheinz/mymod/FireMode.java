package com.jheinz.mymod;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

public enum FireMode implements StringRepresentable {
	/** One shot per click. */
	SEMI("semi"),
	/** Three quick shots per click. */
	BURST("burst"),
	/** Keeps firing while use is held. */
	AUTO("auto"),
	/** One shot per click, then the bolt has to be worked (the gun's cycle time). */
	BOLT("bolt"),
	/** One shot per click, then the shotgun's pump is worked (automatically, over its pump time). */
	PUMP("pump");

	public static final int BURST_SIZE = 3;

	public static final Codec<FireMode> CODEC = StringRepresentable.fromEnum(FireMode::values);
	public static final StreamCodec<ByteBuf, FireMode> STREAM_CODEC =
			ByteBufCodecs.VAR_INT.map(i -> values()[i], FireMode::ordinal);

	private final String name;

	FireMode(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	/** Holding use only fires again in full-auto; other modes need a fresh click. */
	public boolean needsFreshClick() {
		return this != AUTO;
	}

	public String translationKey() {
		return "fire_mode.mymod." + name;
	}
}
