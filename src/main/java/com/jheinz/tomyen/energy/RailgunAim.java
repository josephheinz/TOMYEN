package com.jheinz.tomyen.energy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import com.jheinz.tomyen.MyMod;

/**
 * Where the player was looking at the instant a Railgun's charge completed, before the shot kicked their view.
 *
 * <p>The Railgun fires from {@code onUseTick} at full charge, on the client and the server alike. The client kicks the
 * view up as it fires, and that kicked rotation goes to the server in the same tick's movement packet. The server's
 * charge starts when the use packet arrives, so it reaches full charge only after that movement packet has been read.
 * Fired from the server's own rotation, every shot went where the recoil had already thrown the view, high of the
 * crosshair. So the client sends its aim from before the kick (ahead of the movement packet on the same connection),
 * and the server fires along it if it's close to the player's current rotation.
 */
public record RailgunAim(float xRot, float yRot) implements CustomPacketPayload {
	public static final Type<RailgunAim> TYPE = new Type<>(MyMod.id("railgun_aim"));
	public static final StreamCodec<RegistryFriendlyByteBuf, RailgunAim> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, RailgunAim::xRot,
			ByteBufCodecs.FLOAT, RailgunAim::yRot,
			RailgunAim::new);
	/** How far (degrees, each axis) a reported aim may be from the player's rotation when the shot goes: the kick plus a flick. */
	private static final float TOLERANCE = 30.0f;
	/** Each player's reported aim for the charge under way (server thread only). */
	private static final Map<UUID, RailgunAim> PENDING = new HashMap<>();
	/** Sends an aim to the server; the client sets this (common code can't reach the client networking API). */
	public static Consumer<RailgunAim> sender = aim -> {
	};

	@Override
	public Type<RailgunAim> type() {
		return TYPE;
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (aim, context) -> {
			ServerPlayer player = context.player();
			if (player.isUsingItem() && player.getUseItem().getItem() instanceof RailgunItem) {
				PENDING.put(player.getUUID(), aim);
			}
		});
	}

	/** A new charge: forget any aim left over from the last one. */
	static void clear(ServerPlayer player) {
		PENDING.remove(player.getUUID());
	}

	/** The aim this shot should go along (taking it): the reported one if it's believable, else the current rotation. */
	static RailgunAim take(ServerPlayer player) {
		RailgunAim reported = PENDING.remove(player.getUUID());
		RailgunAim current = new RailgunAim(player.getXRot(), player.getYRot());
		if (reported == null || !Float.isFinite(reported.xRot) || !Float.isFinite(reported.yRot)
				|| Math.abs(reported.xRot - current.xRot) > TOLERANCE
				|| Math.abs(Mth.wrapDegrees(reported.yRot - current.yRot)) > TOLERANCE) {
			return current;
		}
		return new RailgunAim(Mth.clamp(reported.xRot, -90.0f, 90.0f), reported.yRot);
	}
}
