package com.jheinz.mymod.powerarmor;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.MyMod;

/**
 * Everything power armor says over the network beyond its synced attachments. The wearer's client sends
 * requests (thrusters, exit, weapons, core swap), all checked on the server; the server sends the wearer their
 * HUD readings, and everyone nearby the beams to draw.
 */
public final class PowerArmorNetworking {
	private PowerArmorNetworking() {
	}

	/** A request from the wearer's keys. */
	public record Request(int action) implements CustomPacketPayload {
		/** Double-tap of jump with the thrusters off. */
		public static final int THRUSTERS_ON = 0;
		/** Double-tap of jump with them lit. */
		public static final int THRUSTERS_OFF = 1;
		/** The Power Armor key was held long enough. */
		public static final int EXIT = 2;
		public static final int REPULSOR = 3;
		public static final int UNIBEAM = 4;
		public static final int SWAP_CORE = 5;

		public static final Type<Request> TYPE = new Type<>(MyMod.id("power_armor_request"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = ByteBufCodecs.VAR_INT.<RegistryFriendlyByteBuf>cast()
				.map(Request::new, Request::action);

		@Override
		public Type<Request> type() {
			return TYPE;
		}
	}

	/**
	 * The wearer's suit readings for the HUD.
	 *
	 * @param fuel              the core's charge (to within half a thousandth)
	 * @param burn              how hot the thrusters are, 0 to 1 (overheating at 1)
	 * @param repulsorCooldown  ticks until the repulsors are ready, of {@code repulsorCooldownMax}
	 * @param unibeamCooldown   ticks until the unibeam is ready
	 * @param unibeamCharge     ticks it has been charging, or -1
	 * @param air               ticks of air left in the helmet's tank, of {@code airMax}
	 */
	public record Status(int fuel, float burn, boolean overheated, int repulsorCooldown, int repulsorCooldownMax, int unibeamCooldown,
			int unibeamCharge, int air, int airMax) implements CustomPacketPayload {
		public static final Type<Status> TYPE = new Type<>(MyMod.id("power_armor_status"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Status> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Status::fuel,
				ByteBufCodecs.FLOAT, Status::burn,
				ByteBufCodecs.BOOL, Status::overheated,
				ByteBufCodecs.VAR_INT, Status::repulsorCooldown,
				ByteBufCodecs.VAR_INT, Status::repulsorCooldownMax,
				ByteBufCodecs.VAR_INT, Status::unibeamCooldown,
				ByteBufCodecs.VAR_INT, Status::unibeamCharge,
				ByteBufCodecs.VAR_INT, Status::air,
				ByteBufCodecs.VAR_INT, Status::airMax,
				Status::new);

		@Override
		public Type<Status> type() {
			return TYPE;
		}
	}

	/** A repulsor blast or the unibeam, for everyone who can see it to draw. */
	public record Beam(int shooterId, int kind, Vec3 from, Vec3 to) implements CustomPacketPayload {
		public static final int LEFT_REPULSOR = 0;
		public static final int RIGHT_REPULSOR = 1;
		public static final int UNIBEAM = 2;

		public static final Type<Beam> TYPE = new Type<>(MyMod.id("power_armor_beam"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Beam> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Beam::shooterId,
				ByteBufCodecs.VAR_INT, Beam::kind,
				Vec3.STREAM_CODEC, Beam::from,
				Vec3.STREAM_CODEC, Beam::to,
				Beam::new);

		@Override
		public Type<Beam> type() {
			return TYPE;
		}
	}

	static void initialize() {
		PayloadTypeRegistry.serverboundPlay().register(Request.TYPE, Request.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Status.TYPE, Status.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Beam.TYPE, Beam.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Request.TYPE, (payload, context) -> {
			switch (payload.action()) {
				case Request.THRUSTERS_ON -> PowerArmorFlight.toggle(context.player(), true);
				case Request.THRUSTERS_OFF -> PowerArmorFlight.toggle(context.player(), false);
				case Request.EXIT -> PowerArmor.requestExit(context.player());
				case Request.REPULSOR -> Repulsors.fireRepulsor(context.player());
				case Request.UNIBEAM -> Repulsors.chargeUnibeam(context.player());
				case Request.SWAP_CORE -> PowerArmor.swapCore(context.player());
				default -> {
				}
			}
		});
	}
}
