package com.jheinz.mymod.fishing;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import com.jheinz.mymod.MyMod;

/**
 * The fight's messages. The server starts a fight ({@link FightStart}) and has the last word on how it ends
 * ({@link FightEnd}); in between the client plays the minigame and reports how it went ({@link FightResult}).
 */
public final class FishingNetworking {
	/** How a fight ended, as the client claims and the server rules. */
	public enum Outcome {
		CAUGHT, ESCAPED, SNAPPED, CANCELLED;

		public static Outcome byId(int id) {
			return id >= 0 && id < values().length ? values()[id] : ESCAPED;
		}
	}

	private FishingNetworking() {
	}

	/** Server to client: a fish is on, play this minigame. */
	public record FightStart(FightParams params) implements CustomPacketPayload {
		public static final Type<FightStart> TYPE = new Type<>(MyMod.id("fishing_fight_start"));
		public static final StreamCodec<RegistryFriendlyByteBuf, FightStart> CODEC =
				FightParams.STREAM_CODEC.map(FightStart::new, FightStart::params);

		@Override
		public Type<FightStart> type() {
			return TYPE;
		}
	}

	/** Client to server: how the minigame went. */
	public record FightResult(int id, int outcome, boolean perfect) implements CustomPacketPayload {
		public static final Type<FightResult> TYPE = new Type<>(MyMod.id("fishing_fight_result"));
		public static final StreamCodec<RegistryFriendlyByteBuf, FightResult> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, FightResult::id,
				ByteBufCodecs.VAR_INT, FightResult::outcome,
				ByteBufCodecs.BOOL, FightResult::perfect,
				FightResult::new);

		@Override
		public Type<FightResult> type() {
			return TYPE;
		}
	}

	/**
	 * Server to client: the fight's verdict, with what was caught (empty unless caught), the xp it was worth, and the
	 * catch's weight in lb (0 if it wasn't weighed).
	 */
	public record FightEnd(int id, int outcome, ItemStack caught, boolean perfect, int xp, float weight) implements CustomPacketPayload {
		public static final Type<FightEnd> TYPE = new Type<>(MyMod.id("fishing_fight_end"));
		public static final StreamCodec<RegistryFriendlyByteBuf, FightEnd> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, FightEnd::id,
				ByteBufCodecs.VAR_INT, FightEnd::outcome,
				ItemStack.OPTIONAL_STREAM_CODEC, FightEnd::caught,
				ByteBufCodecs.BOOL, FightEnd::perfect,
				ByteBufCodecs.VAR_INT, FightEnd::xp,
				ByteBufCodecs.FLOAT, FightEnd::weight,
				FightEnd::new);

		@Override
		public Type<FightEnd> type() {
			return TYPE;
		}
	}

	/** Server to client: a Fish Finder has picked up the fish swimming in to the bobber. */
	public record SonarPing(ItemStack species, int difficulty) implements CustomPacketPayload {
		public static final Type<SonarPing> TYPE = new Type<>(MyMod.id("fishing_sonar"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SonarPing> CODEC = StreamCodec.composite(
				ItemStack.OPTIONAL_STREAM_CODEC, SonarPing::species,
				ByteBufCodecs.VAR_INT, SonarPing::difficulty,
				SonarPing::new);

		@Override
		public Type<SonarPing> type() {
			return TYPE;
		}
	}

	static void initialize() {
		PayloadTypeRegistry.clientboundPlay().register(FightStart.TYPE, FightStart.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(FightEnd.TYPE, FightEnd.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SonarPing.TYPE, SonarPing.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(FightResult.TYPE, FightResult.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(FightResult.TYPE, (payload, context) ->
				FishingFights.receive(context.player(), payload));
	}
}
