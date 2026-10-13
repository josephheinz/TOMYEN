package com.jheinz.mymod.energy;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.MyMod;

/**
 * Sent to everyone nearby when an energy weapon fires (a Laser Rifle, a Railgun, a Ray Gun, a UFO's zap), so they can
 * draw the beam: a glowing ribbon of {@code color} from {@code from} to {@code to}, {@code width} blocks across at its
 * glow, fading out over {@code millis}.
 *
 * @param shooterId the entity that fired it, so its own player can start the beam at the gun on screen
 */
public record EnergyBeam(int shooterId, Vec3 from, Vec3 to, int color, float width, int millis) implements CustomPacketPayload {
	public static final Type<EnergyBeam> TYPE = new Type<>(MyMod.id("energy_beam"));
	public static final StreamCodec<RegistryFriendlyByteBuf, EnergyBeam> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, EnergyBeam::shooterId,
			Vec3.STREAM_CODEC, EnergyBeam::from,
			Vec3.STREAM_CODEC, EnergyBeam::to,
			ByteBufCodecs.INT, EnergyBeam::color,
			ByteBufCodecs.FLOAT, EnergyBeam::width,
			ByteBufCodecs.VAR_INT, EnergyBeam::millis,
			EnergyBeam::new);
	/** How far off a beam is drawn. */
	private static final double RANGE = 160.0;

	@Override
	public Type<EnergyBeam> type() {
		return TYPE;
	}

	/** Sends a beam to every player near either end of it. */
	public static void send(ServerLevel level, Entity shooter, Vec3 from, Vec3 to, int color, float width, int millis) {
		EnergyBeam beam = new EnergyBeam(shooter.getId(), from, to, color, width, millis);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(from) < RANGE * RANGE || player.distanceToSqr(to) < RANGE * RANGE) {
				ServerPlayNetworking.send(player, beam);
			}
		}
	}
}
