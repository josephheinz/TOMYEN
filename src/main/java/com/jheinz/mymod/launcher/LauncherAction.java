package com.jheinz.mymod.launcher;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;

/**
 * What a held rocket launcher is doing, timed in game ticks so every client can animate it from the stack alone:
 * being hoisted onto the shoulder, being reloaded, or shouldered and ready. A launcher without one has only just
 * come into the hand and isn't shouldered yet.
 *
 * @param start  game time the action began
 * @param ticks  how long it takes
 * @param rocket for a reload, which rocket is going in, as its index in {@link Launchers#ROCKETS}
 */
public record LauncherAction(Kind kind, long start, int ticks, int rocket) {
	public enum Kind implements StringRepresentable {
		EQUIP("equip"), RELOAD("reload"), READY("ready");

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

	public static final Codec<LauncherAction> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Kind.CODEC.fieldOf("kind").forGetter(LauncherAction::kind),
			Codec.LONG.fieldOf("start").forGetter(LauncherAction::start),
			Codec.INT.fieldOf("ticks").forGetter(LauncherAction::ticks),
			Codec.INT.optionalFieldOf("rocket", 0).forGetter(LauncherAction::rocket)).apply(instance, LauncherAction::new));
	public static final StreamCodec<ByteBuf, LauncherAction> STREAM_CODEC = StreamCodec.composite(
			Kind.STREAM_CODEC, LauncherAction::kind,
			ByteBufCodecs.VAR_LONG, LauncherAction::start,
			ByteBufCodecs.VAR_INT, LauncherAction::ticks,
			ByteBufCodecs.VAR_INT, LauncherAction::rocket,
			LauncherAction::new);

	public static LauncherAction equip(long now, int ticks) {
		return new LauncherAction(Kind.EQUIP, now, ticks, 0);
	}

	public static LauncherAction reload(long now, int ticks, Item rocket) {
		return new LauncherAction(Kind.RELOAD, now, ticks, Math.max(0, Launchers.ROCKETS.indexOf(rocket)));
	}

	public static LauncherAction ready(long now) {
		return new LauncherAction(Kind.READY, now, 0, 0);
	}

	/** For a reload, the rocket going in. */
	public Item rocketItem() {
		return Launchers.ROCKETS.get(Math.clamp(rocket, 0, Launchers.ROCKETS.size() - 1));
	}

	public long end() {
		return start + ticks;
	}

	/** How far through it is at (fractional) game time {@code now}, 0 to 1. */
	public float progress(double now) {
		return ticks <= 0 ? 1.0f : (float) Math.max(0.0, Math.min(1.0, (now - start) / ticks));
	}

	/** Shouldered and able to fire: ready, or done being hoisted. A finished reload waits for the server to seat the rocket. */
	public boolean shouldered(long now) {
		return kind == Kind.READY || kind == Kind.EQUIP && now >= end();
	}
}
