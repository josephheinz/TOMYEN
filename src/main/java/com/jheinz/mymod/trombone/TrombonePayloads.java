package com.jheinz.mymod.trombone;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.jheinz.mymod.MyMod;

/** What the server tells clients about the Trombone. */
public final class TrombonePayloads {
	private TrombonePayloads() {
	}

	/**
	 * To the player: their phrase so far (scale degrees and how long each was held, oldest first), the song it just
	 * made (an ordinal of {@link TromboneSong}, or -1), whether that song played (false: it was on cooldown), and each
	 * song's cooldown left in ticks. Sent after every note and when a pause clears the phrase.
	 */
	public record Staff(byte[] degrees, byte[] ticks, int song, boolean played, int[] cooldowns) implements CustomPacketPayload {
		public static final Type<Staff> TYPE = new Type<>(MyMod.id("trombone_staff"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Staff> CODEC = StreamCodec.of((buf, staff) -> {
			buf.writeByteArray(staff.degrees);
			buf.writeByteArray(staff.ticks);
			buf.writeVarInt(staff.song + 1);
			buf.writeBoolean(staff.played);
			buf.writeVarIntArray(staff.cooldowns);
		}, buf -> new Staff(buf.readByteArray(32), buf.readByteArray(32), buf.readVarInt() - 1, buf.readBoolean(), buf.readVarIntArray(8)));

		@Override
		public Type<Staff> type() {
			return TYPE;
		}
	}

	/**
	 * To everyone nearby: these mobs are in a song's spell for this many ticks, so clients can show it (the Sad
	 * Trombone's droop, the Lullaby's nap). mood: an ordinal of {@link TromboneMoods.Mood}.
	 */
	public record Moods(int mood, int ticks, int[] ids) implements CustomPacketPayload {
		public static final Type<Moods> TYPE = new Type<>(MyMod.id("trombone_moods"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Moods> CODEC = StreamCodec.of((buf, moods) -> {
			buf.writeVarInt(moods.mood);
			buf.writeVarInt(moods.ticks);
			buf.writeVarIntArray(moods.ids);
		}, buf -> new Moods(buf.readVarInt(), buf.readVarInt(), buf.readVarIntArray(512)));

		@Override
		public Type<Moods> type() {
			return TYPE;
		}
	}
}
